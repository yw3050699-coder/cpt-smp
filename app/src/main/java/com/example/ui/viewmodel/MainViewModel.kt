package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firestore.ExamRepository
import com.example.data.firestore.QrScanOutcome
import com.example.data.firestore.friendlyFirestoreMessage
import com.example.data.model.AttendanceRecord
import com.example.data.model.ExamQuestion
import com.example.data.model.ExamSchedule
import com.example.data.model.ExamSubmission
import com.example.data.model.QuestionType
import com.example.data.model.RoleAssignment
import com.example.data.model.SchoolClass
import com.example.data.model.Student
import com.example.data.model.Subject
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class ActiveExamSession(
    val exam: ExamSchedule,
    val student: Student,
    val submission: ExamSubmission,
    val questions: List<ExamQuestion>,
    val draftAnswers: Map<String, String>,
    val remainingSeconds: Long,
    val isSaving: Boolean = false,
    val lastSavedLabel: String = "Siap"
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: ExamRepository,
    private val currentUserId: String
) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val bootstrappedAdmin = repository.isBootstrappedAdmin(auth.currentUser)

    private val _profileState = MutableStateFlow<UiState<UserProfile>>(UiState.Loading)
    val profileState: StateFlow<UiState<UserProfile>> = _profileState.asStateFlow()

    private val _effectiveRole = MutableStateFlow(
        if (bootstrappedAdmin) UserRole.ADMIN else UserRole.STUDENT
    )
    val effectiveRole: StateFlow<UserRole> = _effectiveRole.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _selectedExamForDetail = MutableStateFlow<ExamSchedule?>(null)
    val selectedExamForDetail: StateFlow<ExamSchedule?> = _selectedExamForDetail.asStateFlow()

    private val _qrScanOutcome = MutableStateFlow<QrScanOutcome?>(null)
    val qrScanOutcome: StateFlow<QrScanOutcome?> = _qrScanOutcome.asStateFlow()

    private val _activeExamSession = MutableStateFlow<ActiveExamSession?>(null)
    val activeExamSession: StateFlow<ActiveExamSession?> = _activeExamSession.asStateFlow()

    private var timerJob: Job? = null
    private var autosaveJob: Job? = null

    init {
        refreshProfile()
    }

    fun refreshProfile() {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            _profileState.value = UiState.Loading
            repository.ensureUserProfileAndRole(user)
                .onSuccess { profile ->
                    val role = UserRole.fromWire(profile.role)
                    _effectiveRole.value = role
                    _profileState.value = UiState.Success(profile)
                }
                .onFailure { error ->
                    _profileState.value = UiState.Error(friendlyFirestoreMessage(error))
                }
        }
    }

    private fun <T> Flow<List<T>>.toUiState(label: String): StateFlow<UiState<List<T>>> {
        return this
            .map<List<T>, UiState<List<T>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing $label", rootError)
                emit(UiState.Error(friendlyFirestoreMessage(rootError)))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )
    }

    val classesState: StateFlow<UiState<List<SchoolClass>>> =
        repository.observeClasses(currentUserId, bootstrappedAdmin).toUiState("classes")

    val subjectsState: StateFlow<UiState<List<Subject>>> =
        repository.observeSubjects(currentUserId, bootstrappedAdmin).toUiState("subjects")

    val examsState: StateFlow<UiState<List<ExamSchedule>>> =
        repository.observeExams(currentUserId, bootstrappedAdmin).toUiState("exams")

    val studentsState: StateFlow<UiState<List<Student>>> = _effectiveRole
        .flatMapLatest { role -> repository.observeStudents(currentUserId, role, bootstrappedAdmin) }
        .toUiState("students")

    val attendanceState: StateFlow<UiState<List<AttendanceRecord>>> = _effectiveRole
        .flatMapLatest { role -> repository.observeAttendance(currentUserId, role, bootstrappedAdmin) }
        .toUiState("attendance")

    val submissionsState: StateFlow<UiState<List<ExamSubmission>>> = _effectiveRole
        .flatMapLatest { role -> repository.observeSubmissions(currentUserId, role, bootstrappedAdmin) }
        .toUiState("submissions")

    val proctorsState: StateFlow<UiState<List<RoleAssignment>>> =
        repository.observeProctors(currentUserId, bootstrappedAdmin).toUiState("proctors")

    val selectedExamQuestionsState: StateFlow<UiState<List<ExamQuestion>>> = _selectedExamForDetail
        .flatMapLatest { exam ->
            if (exam == null) flowOf(emptyList())
            else repository.observeQuestions(exam.id, bootstrappedAdmin)
        }
        .toUiState("questions")

    fun selectExamForDetail(exam: ExamSchedule?) {
        _selectedExamForDetail.value = exam
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun clearQrScanOutcome() {
        _qrScanOutcome.value = null
    }

    fun addClass(name: String, gradeLevel: Long, homeroomTeacher: String, capacity: Long) {
        viewModelScope.launch {
            repository.addClass(name, gradeLevel, homeroomTeacher, capacity)
                .onSuccess { _snackbarMessage.value = "Rombel $name berhasil ditambahkan." }
                .onFailure { _snackbarMessage.value = friendlyFirestoreMessage(it) }
        }
    }

    fun addSubject(code: String, name: String) {
        viewModelScope.launch {
            repository.addSubject(code, name)
                .onSuccess { _snackbarMessage.value = "Mata pelajaran $name berhasil ditambahkan." }
                .onFailure { _snackbarMessage.value = friendlyFirestoreMessage(it) }
        }
    }

    fun addStudent(
        nis: String,
        fullName: String,
        gender: String,
        schoolClass: SchoolClass,
        linkedEmail: String
    ) {
        viewModelScope.launch {
            repository.addStudent(nis, fullName, gender, schoolClass, linkedEmail)
                .onSuccess { _snackbarMessage.value = "Siswa ${it.fullName} (QR dibuat) berhasil disimpan." }
                .onFailure { _snackbarMessage.value = friendlyFirestoreMessage(it) }
        }
    }

    fun deleteStudent(studentId: String) {
        viewModelScope.launch {
            repository.deleteStudent(studentId)
                .onSuccess { _snackbarMessage.value = "Data siswa dihapus." }
                .onFailure { _snackbarMessage.value = friendlyFirestoreMessage(it) }
        }
    }

    fun addExam(
        title: String,
        subject: Subject,
        selectedClasses: List<SchoolClass>,
        startAt: Date,
        endAt: Date,
        durationMinutes: Long,
        tokenCode: String
    ) {
        viewModelScope.launch {
            repository.addExam(title, subject, selectedClasses, startAt, endAt, durationMinutes, tokenCode)
                .onSuccess { _snackbarMessage.value = "Jadwal ujian '$title' berhasil dibuat." }
                .onFailure { _snackbarMessage.value = friendlyFirestoreMessage(it) }
        }
    }

    fun deleteExam(examId: String) {
        viewModelScope.launch {
            repository.deleteExam(examId)
                .onSuccess {
                    if (_selectedExamForDetail.value?.id == examId) {
                        _selectedExamForDetail.value = null
                    }
                    _snackbarMessage.value = "Jadwal ujian dihapus."
                }
                .onFailure { _snackbarMessage.value = friendlyFirestoreMessage(it) }
        }
    }

    fun addQuestion(
        examId: String,
        orderIndex: Long,
        questionType: QuestionType,
        prompt: String,
        optionA: String,
        optionB: String,
        optionC: String,
        optionD: String,
        correctOption: String,
        points: Long
    ) {
        viewModelScope.launch {
            repository.addQuestion(
                examId, orderIndex, questionType, prompt,
                optionA, optionB, optionC, optionD, correctOption, points
            )
                .onSuccess { _snackbarMessage.value = "Soal #$orderIndex berhasil ditambahkan ke bank soal." }
                .onFailure { _snackbarMessage.value = friendlyFirestoreMessage(it) }
        }
    }

    fun deleteQuestion(examId: String, questionId: String) {
        viewModelScope.launch {
            repository.deleteQuestion(examId, questionId)
                .onSuccess { _snackbarMessage.value = "Soal dihapus." }
                .onFailure { _snackbarMessage.value = friendlyFirestoreMessage(it) }
        }
    }

    fun processQrScan(exam: ExamSchedule, rawToken: String) {
        val students = (studentsState.value as? UiState.Success)?.data ?: emptyList()
        val scannerName = (profileState.value as? UiState.Success)?.data?.fullName ?: "Petugas"
        viewModelScope.launch {
            repository.recordQrAttendance(
                exam = exam,
                rawQrToken = rawToken,
                scannerRole = _effectiveRole.value,
                scannerName = scannerName,
                knownStudents = students
            )
                .onSuccess { outcome -> _qrScanOutcome.value = outcome }
                .onFailure { _snackbarMessage.value = friendlyFirestoreMessage(it) }
        }
    }

    fun gradeEssay(submission: ExamSubmission, essayScores: Map<String, Long>) {
        viewModelScope.launch {
            repository.gradeEssaySubmission(submission.id, essayScores, submission.autoScore)
                .onSuccess { _snackbarMessage.value = "Nilai esai untuk ${submission.studentName} berhasil disimpan." }
                .onFailure { _snackbarMessage.value = friendlyFirestoreMessage(it) }
        }
    }

    fun grantProctor(uid: String, email: String, fullName: String) {
        viewModelScope.launch {
            repository.grantProctorRole(uid, email, fullName)
                .onSuccess { _snackbarMessage.value = "Akses Petugas diberikan kepada $fullName." }
                .onFailure { _snackbarMessage.value = friendlyFirestoreMessage(it) }
        }
    }

    fun revokeProctor(uid: String) {
        viewModelScope.launch {
            repository.revokeProctorRole(uid)
                .onSuccess { _snackbarMessage.value = "Akses Petugas dicabut." }
                .onFailure { _snackbarMessage.value = friendlyFirestoreMessage(it) }
        }
    }

    fun seedSampleData() {
        viewModelScope.launch {
            repository.seedSampleSchoolData()
                .onSuccess { _snackbarMessage.value = "Data contoh SMP Negeri 01 Banding Agung berhasil dibuat." }
                .onFailure { _snackbarMessage.value = friendlyFirestoreMessage(it) }
        }
    }

    // --- Exam Runner Lifecycle ---
    fun launchExam(exam: ExamSchedule, student: Student) {
        viewModelScope.launch {
            repository.getQuestionsForExam(exam.id, bootstrappedAdmin)
                .onSuccess { questions ->
                    startExamSession(exam, student, questions)
                }
                .onFailure { error ->
                    _snackbarMessage.value = friendlyFirestoreMessage(error)
                }
        }
    }

    fun startExamSession(exam: ExamSchedule, student: Student, questions: List<ExamQuestion>) {
        if (questions.isEmpty()) {
            _snackbarMessage.value = "Ujian ini belum memiliki butir soal."
            return
        }
        viewModelScope.launch {
            repository.startOrResumeSubmission(exam, student, questions)
                .onSuccess { submission ->
                    val durationSec = exam.durationMinutes * 60L
                    val elapsedSec = submission.startedAt?.toDate()?.let {
                        ((System.currentTimeMillis() - it.time) / 1000L).coerceAtLeast(0L)
                    } ?: 0L
                    val remaining = (durationSec - elapsedSec).coerceAtLeast(60L)
                    _activeExamSession.value = ActiveExamSession(
                        exam = exam,
                        student = student,
                        submission = submission,
                        questions = questions,
                        draftAnswers = submission.answers,
                        remainingSeconds = remaining
                    )
                    if (submission.status == "in_progress") {
                        startCountdownTimer()
                    }
                }
                .onFailure { _snackbarMessage.value = friendlyFirestoreMessage(it) }
        }
    }

    private fun startCountdownTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val current = _activeExamSession.value ?: break
                if (current.submission.status != "in_progress") break
                val next = current.remainingSeconds - 1L
                if (next <= 0L) {
                    _activeExamSession.value = current.copy(remainingSeconds = 0L)
                    submitActiveExam()
                    break
                } else {
                    _activeExamSession.value = current.copy(remainingSeconds = next)
                }
            }
        }
    }

    fun updateDraftAnswer(questionId: String, answer: String) {
        val current = _activeExamSession.value ?: return
        if (current.submission.status != "in_progress") return
        val updatedAnswers = current.draftAnswers.toMutableMap().apply {
            if (answer.isBlank()) remove(questionId) else put(questionId, answer)
        }
        _activeExamSession.value = current.copy(
            draftAnswers = updatedAnswers,
            isSaving = true,
            lastSavedLabel = "Menyimpan..."
        )
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(800L)
            repository.autosaveAnswers(current.submission.id, updatedAnswers)
                .onSuccess {
                    _activeExamSession.value = _activeExamSession.value?.copy(
                        isSaving = false,
                        lastSavedLabel = "Tersimpan otomatis"
                    )
                }
                .onFailure {
                    _activeExamSession.value = _activeExamSession.value?.copy(
                        isSaving = false,
                        lastSavedLabel = "Gagal sinkron"
                    )
                }
        }
    }

    fun submitActiveExam() {
        val current = _activeExamSession.value ?: return
        timerJob?.cancel()
        autosaveJob?.cancel()
        viewModelScope.launch {
            _activeExamSession.value = current.copy(isSaving = true, lastSavedLabel = "Mengumpulkan...")
            repository.submitExam(current.submission.id, current.draftAnswers, current.questions)
                .onSuccess { updatedSub ->
                    _activeExamSession.value = _activeExamSession.value?.copy(
                        submission = updatedSub,
                        isSaving = false,
                        lastSavedLabel = "Terkumpul"
                    )
                    _snackbarMessage.value = "Jawaban ujian berhasil dikumpulkan!"
                }
                .onFailure { error ->
                    _activeExamSession.value = _activeExamSession.value?.copy(
                        isSaving = false,
                        lastSavedLabel = "Gagal mengumpulkan"
                    )
                    _snackbarMessage.value = friendlyFirestoreMessage(error)
                }
        }
    }

    fun closeExamSession() {
        timerJob?.cancel()
        autosaveJob?.cancel()
        _activeExamSession.value = null
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        autosaveJob?.cancel()
    }

    private companion object {
        const val TAG = "MainViewModel"
    }
}
