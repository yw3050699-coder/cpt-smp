package com.example.data.firestore

import android.content.Context
import com.example.R
import com.example.data.model.AttendanceRecord
import com.example.data.model.ExamQuestion
import com.example.data.model.ExamSchedule
import com.example.data.model.ExamSubmission
import com.example.data.model.QuestionType
import com.example.data.model.RoleAssignment
import com.example.data.model.SchoolClass
import com.example.data.model.Student
import com.example.data.model.Subject
import com.example.data.model.SubmissionStatus
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

sealed class QrScanOutcome {
    data class Success(val record: AttendanceRecord) : QrScanOutcome()
    data class Duplicate(val existing: AttendanceRecord) : QrScanOutcome()
    data class Rejected(val reason: String) : QrScanOutcome()
}

class ExamRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    companion object {
        const val BOOTSTRAPPED_ADMIN_EMAIL = "yw3050699@gmail.com"
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Silakan masuk dengan Google terlebih dahulu.")
    }

    fun isBootstrappedAdmin(user: FirebaseUser? = auth.currentUser): Boolean {
        return user != null &&
            user.isEmailVerified &&
            user.email?.equals(BOOTSTRAPPED_ADMIN_EMAIL, ignoreCase = true) == true
    }

    suspend fun ensureUserProfileAndRole(user: FirebaseUser): Result<UserProfile> {
        val path = "users/${user.uid}"
        return try {
            val uid = user.uid
            val email = user.email.orEmpty().trim().lowercase()
            val displayName = user.displayName?.takeIf { it.isNotBlank() }
                ?: email.substringBefore("@").ifBlank { "Pengguna" }

            val isAdminDoc = try {
                db.collection("admins").document(uid).get().await().exists()
            } catch (_: Exception) {
                false
            }
            val isProctorDoc = try {
                db.collection("proctors").document(uid).get().await().exists()
            } catch (_: Exception) {
                false
            }

            val userRef = db.collection("users").document(uid)
            val snap = userRef.get().await()

            val effectiveRole = when {
                isBootstrappedAdmin(user) || isAdminDoc -> UserRole.ADMIN
                isProctorDoc -> UserRole.PROCTOR
                else -> UserRole.fromWire(snap.getString("role"))
            }

            // Try claiming pre-linked student record if email is verified
            var claimedStudentId = snap.getString("studentId")
            if (user.isEmailVerified && email.isNotBlank()) {
                val matchingStudents = try {
                    db.collection("students")
                        .whereEqualTo("linkedEmail", email)
                        .get()
                        .await()
                        .toObjects(Student::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } catch (_: Exception) {
                    emptyList()
                }
                val student = matchingStudents.firstOrNull()
                if (student != null) {
                    claimedStudentId = student.id
                    if (student.authUserId.isBlank()) {
                        try {
                            db.collection("students").document(student.id).update(
                                mapOf(
                                    "authUserId" to uid,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                        } catch (_: Exception) {
                            // Ignore if already claimed or restricted
                        }
                    }
                }
            }

            if (!snap.exists()) {
                val initialRole = if (isBootstrappedAdmin(user)) UserRole.ADMIN.wireValue else UserRole.STUDENT.wireValue
                val payload = mapOf(
                    "userId" to uid,
                    "fullName" to displayName.take(100),
                    "email" to email.ifBlank { "user@smpn1.local" }.take(150),
                    "role" to initialRole,
                    "studentId" to claimedStudentId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                userRef.set(payload).await()
            } else if (claimedStudentId != null && claimedStudentId != snap.getString("studentId")) {
                userRef.update(
                    mapOf(
                        "studentId" to claimedStudentId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val finalProfile = userRef.get().await()
                .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: UserProfile(
                    id = uid,
                    userId = uid,
                    fullName = displayName,
                    email = email,
                    role = effectiveRole.wireValue,
                    studentId = claimedStudentId
                )

            Result.success(finalProfile.copy(role = effectiveRole.wireValue))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private fun <T> observeQuery(
        query: Query,
        path: String,
        mapper: (List<DocumentSnapshot>) -> List<T>
    ): Flow<List<T>> = flow {
        val uid = requireUserId()
        query.snapshots()
            .map { snap -> mapper(snap.documents) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observeClasses(uid: String, bootstrappedAdmin: Boolean): Flow<List<SchoolClass>> {
        val base = db.collection("classes")
        val query = if (bootstrappedAdmin) base else base.whereEqualTo("published", true)
        return observeQuery(query, "classes") { docs ->
            docs.mapNotNull { it.toObject(SchoolClass::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .sortedWith(compareBy<SchoolClass> { it.gradeLevel }.thenBy { it.name })
        }
    }

    fun observeSubjects(uid: String, bootstrappedAdmin: Boolean): Flow<List<Subject>> {
        val base = db.collection("subjects")
        val query = if (bootstrappedAdmin) base else base.whereEqualTo("published", true)
        return observeQuery(query, "subjects") { docs ->
            docs.mapNotNull { it.toObject(Subject::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .sortedBy { it.name }
        }
    }

    fun observeStudents(uid: String, role: UserRole, bootstrappedAdmin: Boolean): Flow<List<Student>> {
        val base = db.collection("students")
        val query = when {
            bootstrappedAdmin -> base
            role == UserRole.ADMIN || role == UserRole.PROCTOR -> base.whereArrayContains("staffUids", uid)
            else -> base.whereEqualTo("authUserId", uid)
        }
        return observeQuery(query, "students") { docs ->
            docs.mapNotNull { it.toObject(Student::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .sortedWith(compareBy<Student> { it.className }.thenBy { it.nis })
        }
    }

    fun observeExams(uid: String, bootstrappedAdmin: Boolean): Flow<List<ExamSchedule>> {
        val base = db.collection("exams")
        val query = if (bootstrappedAdmin) base else base.whereEqualTo("published", true)
        return observeQuery(query, "exams") { docs ->
            docs.mapNotNull { it.toObject(ExamSchedule::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .sortedByDescending { it.startAt ?: Timestamp(0, 0) }
        }
    }

    fun observeQuestions(examId: String, bootstrappedAdmin: Boolean): Flow<List<ExamQuestion>> {
        val base = db.collection("exams").document(examId).collection("questions")
        val query = if (bootstrappedAdmin) base else base.whereEqualTo("published", true)
        return observeQuery(query, "exams/$examId/questions") { docs ->
            docs.mapNotNull { it.toObject(ExamQuestion::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .sortedBy { it.orderIndex }
        }
    }

    suspend fun getQuestionsForExam(examId: String, bootstrappedAdmin: Boolean): Result<List<ExamQuestion>> {
        val path = "exams/$examId/questions"
        return try {
            requireUserId()
            val base = db.collection("exams").document(examId).collection("questions")
            val query = if (bootstrappedAdmin) base else base.whereEqualTo("published", true)
            val snap = query.get().await()
            val list = snap.documents
                .mapNotNull { it.toObject(ExamQuestion::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .sortedBy { it.orderIndex }
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    fun observeAttendance(uid: String, role: UserRole, bootstrappedAdmin: Boolean): Flow<List<AttendanceRecord>> {
        val base = db.collection("attendance")
        val query = when {
            bootstrappedAdmin -> base
            role == UserRole.ADMIN || role == UserRole.PROCTOR -> base.whereArrayContains("staffUids", uid)
            else -> base.whereEqualTo("studentAuthUid", uid)
        }
        return observeQuery(query, "attendance") { docs ->
            docs.mapNotNull { it.toObject(AttendanceRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .sortedByDescending { it.scannedAt ?: Timestamp(0, 0) }
        }
    }

    fun observeSubmissions(uid: String, role: UserRole, bootstrappedAdmin: Boolean): Flow<List<ExamSubmission>> {
        val base = db.collection("submissions")
        val query = when {
            bootstrappedAdmin -> base
            role == UserRole.ADMIN || role == UserRole.PROCTOR -> base.whereArrayContains("staffUids", uid)
            else -> base.whereEqualTo("studentAuthUid", uid)
        }
        return observeQuery(query, "submissions") { docs ->
            docs.mapNotNull { it.toObject(ExamSubmission::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .sortedByDescending { it.updatedAt ?: Timestamp(0, 0) }
        }
    }

    fun observeProctors(uid: String, bootstrappedAdmin: Boolean): Flow<List<RoleAssignment>> {
        val base = db.collection("proctors")
        val query = if (bootstrappedAdmin) base else base.whereEqualTo("grantedByUid", uid)
        return observeQuery(query, "proctors") { docs ->
            docs.mapNotNull { it.toObject(RoleAssignment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .sortedBy { it.fullName }
        }
    }

    private suspend fun currentStaffUids(ownerUid: String): List<String> {
        val list = linkedSetOf(ownerUid)
        try {
            val proctors = db.collection("proctors").whereEqualTo("grantedByUid", ownerUid).get().await()
            proctors.documents.forEach { doc ->
                if (list.size < 25) list.add(doc.id)
            }
        } catch (_: Exception) {
            // Keep at least ownerUid
        }
        return list.toList()
    }

    suspend fun addClass(name: String, gradeLevel: Long, homeroomTeacher: String, capacity: Long): Result<Unit> {
        var path = "classes"
        return try {
            val uid = requireUserId()
            val cleanName = name.trim()
            require(cleanName.length in 2..40) { "Nama rombel harus 2-40 karakter." }
            require(gradeLevel in 7L..9L) { "Tingkat kelas SMP harus 7, 8, atau 9." }
            require(capacity in 1L..100L) { "Kapasitas rombel harus 1-100 siswa." }
            val docRef = db.collection("classes").document()
            path = docRef.path
            val staffUids = currentStaffUids(uid)
            docRef.set(
                mapOf(
                    "name" to cleanName,
                    "gradeLevel" to gradeLevel,
                    "homeroomTeacher" to homeroomTeacher.trim().take(100),
                    "capacity" to capacity,
                    "published" to true,
                    "ownerId" to uid,
                    "staffUids" to staffUids,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun addSubject(code: String, name: String): Result<Unit> {
        var path = "subjects"
        return try {
            val uid = requireUserId()
            val cleanCode = code.trim().uppercase()
            val cleanName = name.trim()
            require(cleanCode.length in 2..20) { "Kode mapel harus 2-20 karakter." }
            require(cleanName.length in 2..80) { "Nama mapel harus 2-80 karakter." }
            val docRef = db.collection("subjects").document()
            path = docRef.path
            val staffUids = currentStaffUids(uid)
            docRef.set(
                mapOf(
                    "code" to cleanCode,
                    "name" to cleanName,
                    "published" to true,
                    "ownerId" to uid,
                    "staffUids" to staffUids,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun addStudent(
        nis: String,
        fullName: String,
        gender: String,
        schoolClass: SchoolClass,
        linkedEmail: String
    ): Result<Student> {
        var path = "students"
        return try {
            val uid = requireUserId()
            val cleanNis = nis.trim()
            val cleanName = fullName.trim()
            val cleanGender = if (gender.uppercase() == "P") "P" else "L"
            val cleanEmail = linkedEmail.trim().lowercase()
            require(cleanNis.length in 3..30) { "NIS harus 3-30 karakter." }
            require(cleanName.length in 2..100) { "Nama siswa harus 2-100 karakter." }
            require(schoolClass.id.isNotBlank()) { "Rombel wajib dipilih." }

            val suffix = UUID.randomUUID().toString().replace("-", "").take(6).uppercase()
            val qrToken = "SMPN1-$cleanNis-$suffix".take(64)
            val docRef = db.collection("students").document()
            path = docRef.path
            val staffUids = currentStaffUids(uid)

            docRef.set(
                mapOf(
                    "nis" to cleanNis,
                    "fullName" to cleanName,
                    "gender" to cleanGender,
                    "classId" to schoolClass.id,
                    "className" to schoolClass.name,
                    "qrToken" to qrToken,
                    "linkedEmail" to cleanEmail.take(150),
                    "authUserId" to "",
                    "ownerId" to uid,
                    "staffUids" to staffUids,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(
                Student(
                    id = docRef.id,
                    nis = cleanNis,
                    fullName = cleanName,
                    gender = cleanGender,
                    classId = schoolClass.id,
                    className = schoolClass.name,
                    qrToken = qrToken,
                    linkedEmail = cleanEmail,
                    ownerId = uid,
                    staffUids = staffUids
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteStudent(studentId: String): Result<Unit> {
        val path = "students/$studentId"
        return try {
            requireUserId()
            db.collection("students").document(studentId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun addExam(
        title: String,
        subject: Subject,
        selectedClasses: List<SchoolClass>,
        startAt: Date,
        endAt: Date,
        durationMinutes: Long,
        tokenCode: String
    ): Result<String> {
        var path = "exams"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim()
            val cleanToken = tokenCode.trim().uppercase()
            require(cleanTitle.length in 2..120) { "Judul ujian harus 2-120 karakter." }
            require(subject.id.isNotBlank()) { "Mata pelajaran wajib dipilih." }
            require(!endAt.before(startAt)) { "Waktu selesai harus setelah waktu mulai." }
            require(durationMinutes in 5L..300L) { "Durasi ujian harus 5-300 menit." }
            require(cleanToken.length in 3..20) { "Token ujian harus 3-20 karakter." }

            val docRef = db.collection("exams").document()
            path = docRef.path
            val staffUids = currentStaffUids(uid)
            docRef.set(
                mapOf(
                    "title" to cleanTitle,
                    "subjectId" to subject.id,
                    "subjectName" to subject.name,
                    "classIds" to selectedClasses.map { it.id },
                    "classNames" to selectedClasses.map { it.name },
                    "startAt" to Timestamp(startAt),
                    "endAt" to Timestamp(endAt),
                    "durationMinutes" to durationMinutes,
                    "tokenCode" to cleanToken,
                    "published" to true,
                    "ownerId" to uid,
                    "staffUids" to staffUids,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteExam(examId: String): Result<Unit> {
        val path = "exams/$examId"
        return try {
            requireUserId()
            db.collection("exams").document(examId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun addQuestion(
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
    ): Result<Unit> {
        var path = "exams/$examId/questions"
        return try {
            val uid = requireUserId()
            val cleanPrompt = prompt.trim()
            require(cleanPrompt.length in 2..2000) { "Teks soal harus 2-2000 karakter." }
            require(points in 1L..100L) { "Bobot nilai soal harus 1-100." }
            if (questionType == QuestionType.PG) {
                require(optionA.isNotBlank() && optionB.isNotBlank()) { "Pilihan A dan B wajib diisi untuk Pilihan Ganda." }
            }

            val docRef = db.collection("exams").document(examId).collection("questions").document()
            path = docRef.path
            val staffUids = currentStaffUids(uid)
            docRef.set(
                mapOf(
                    "examId" to examId,
                    "orderIndex" to orderIndex.coerceIn(0L, 500L),
                    "questionType" to questionType.wireValue,
                    "prompt" to cleanPrompt,
                    "optionA" to optionA.trim().take(500),
                    "optionB" to optionB.trim().take(500),
                    "optionC" to optionC.trim().take(500),
                    "optionD" to optionD.trim().take(500),
                    "correctOption" to if (questionType == QuestionType.PG) correctOption.trim().uppercase().take(10) else "",
                    "points" to points,
                    "published" to true,
                    "ownerId" to uid,
                    "staffUids" to staffUids,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteQuestion(examId: String, questionId: String): Result<Unit> {
        val path = "exams/$examId/questions/$questionId"
        return try {
            requireUserId()
            db.collection("exams").document(examId).collection("questions").document(questionId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun recordQrAttendance(
        exam: ExamSchedule,
        rawQrToken: String,
        scannerRole: UserRole,
        scannerName: String,
        knownStudents: List<Student>
    ): Result<QrScanOutcome> {
        val token = rawQrToken.trim()
        if (token.isBlank()) {
            return Result.success(QrScanOutcome.Rejected("Token QR kosong."))
        }
        if (scannerRole != UserRole.ADMIN && scannerRole != UserRole.PROCTOR) {
            return Result.success(QrScanOutcome.Rejected("Hanya Petugas atau Admin yang dapat mencatat absensi QR."))
        }

        val nowMs = System.currentTimeMillis()
        val startMs = exam.startAt?.toDate()?.time ?: 0L
        val endMs = exam.endAt?.toDate()?.time ?: Long.MAX_VALUE
        if (nowMs < startMs) {
            return Result.success(QrScanOutcome.Rejected("Jadwal ujian '${exam.title}' belum dimulai."))
        }
        if (nowMs > endMs) {
            return Result.success(QrScanOutcome.Rejected("Waktu absensi untuk '${exam.title}' telah berakhir."))
        }

        val student = knownStudents.firstOrNull { it.qrToken.equals(token, ignoreCase = true) }
            ?: return Result.success(QrScanOutcome.Rejected("QR tidak terdaftar pada database siswa SMP Negeri 01 Banding Agung."))

        if (exam.classIds.isNotEmpty() && student.classId !in exam.classIds) {
            return Result.success(
                QrScanOutcome.Rejected("Siswa ${student.fullName} (${student.className}) tidak terjadwal pada ujian ini.")
            )
        }

        val attendanceId = "${exam.id}_${student.id}"
        val docRef = db.collection("attendance").document(attendanceId)
        return try {
            val uid = requireUserId()
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                val existingRecord = existingSnap.toObject(
                    AttendanceRecord::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: AttendanceRecord(
                    id = attendanceId,
                    examId = exam.id,
                    examTitle = exam.title,
                    studentId = student.id,
                    studentName = student.fullName,
                    nis = student.nis,
                    className = student.className,
                    qrToken = student.qrToken
                )
                return Result.success(QrScanOutcome.Duplicate(existingRecord))
            }

            val staffUids = (exam.staffUids + uid).distinct().take(25)
            val ownerId = exam.ownerId.ifBlank { uid }
            val payload = mapOf(
                "examId" to exam.id,
                "examTitle" to exam.title,
                "studentId" to student.id,
                "studentName" to student.fullName,
                "nis" to student.nis,
                "className" to student.className,
                "qrToken" to student.qrToken,
                "studentAuthUid" to student.authUserId,
                "scannedByUid" to uid,
                "scannedByName" to scannerName.ifBlank { "Petugas Ujian" }.take(100),
                "status" to "present",
                "ownerId" to ownerId,
                "staffUids" to staffUids,
                "scannedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            val saved = docRef.get().await().toObject(
                AttendanceRecord::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: AttendanceRecord(
                id = attendanceId,
                examId = exam.id,
                examTitle = exam.title,
                studentId = student.id,
                studentName = student.fullName,
                nis = student.nis,
                className = student.className,
                qrToken = student.qrToken,
                scannedByUid = uid,
                scannedByName = scannerName,
                status = "present",
                scannedAt = Timestamp.now()
            )
            Result.success(QrScanOutcome.Success(saved))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun startOrResumeSubmission(
        exam: ExamSchedule,
        student: Student,
        questions: List<ExamQuestion>
    ): Result<ExamSubmission> {
        val submissionId = "${exam.id}_${student.id}"
        val docRef = db.collection("submissions").document(submissionId)
        return try {
            val uid = requireUserId()
            val existing = docRef.get().await()
            if (existing.exists()) {
                val sub = existing.toObject(
                    ExamSubmission::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )!!
                return Result.success(sub)
            }

            val maxScore = questions.sumOf { it.points }
            val studentUid = student.authUserId.ifBlank { uid }
            val staffUids = (exam.staffUids + exam.ownerId).filter { it.isNotBlank() }.distinct().take(25)
            val ownerId = exam.ownerId.ifBlank { uid }

            val payload = mapOf(
                "examId" to exam.id,
                "examTitle" to exam.title,
                "subjectName" to exam.subjectName,
                "studentId" to student.id,
                "studentName" to student.fullName,
                "nis" to student.nis,
                "className" to student.className,
                "studentAuthUid" to studentUid,
                "answers" to emptyMap<String, String>(),
                "essayScores" to emptyMap<String, Long>(),
                "autoScore" to 0L,
                "essayScore" to 0L,
                "totalScore" to 0L,
                "maxScore" to maxScore,
                "status" to SubmissionStatus.IN_PROGRESS.wireValue,
                "ownerId" to ownerId,
                "staffUids" to staffUids,
                "startedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            val created = docRef.get().await().toObject(
                ExamSubmission::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )!!
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun autosaveAnswers(submissionId: String, answers: Map<String, String>): Result<Unit> {
        val docRef = db.collection("submissions").document(submissionId)
        return try {
            requireUserId()
            docRef.update(
                mapOf(
                    "answers" to answers,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun submitExam(
        submissionId: String,
        answers: Map<String, String>,
        questions: List<ExamQuestion>
    ): Result<ExamSubmission> {
        val docRef = db.collection("submissions").document(submissionId)
        return try {
            requireUserId()
            var autoScore = 0L
            var hasEssay = false
            var maxScore = 0L
            questions.forEach { q ->
                maxScore += q.points
                if (QuestionType.fromWire(q.questionType) == QuestionType.PG) {
                    val chosen = answers[q.id]?.trim().orEmpty()
                    if (chosen.isNotBlank() && chosen.equals(q.correctOption.trim(), ignoreCase = true)) {
                        autoScore += q.points
                    }
                } else {
                    hasEssay = true
                }
            }
            val finalStatus = if (hasEssay) SubmissionStatus.SUBMITTED else SubmissionStatus.GRADED
            docRef.update(
                mapOf(
                    "answers" to answers,
                    "autoScore" to autoScore,
                    "essayScore" to 0L,
                    "totalScore" to autoScore,
                    "maxScore" to maxScore,
                    "status" to finalStatus.wireValue,
                    "submittedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            val updated = docRef.get().await().toObject(
                ExamSubmission::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )!!
            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun gradeEssaySubmission(
        submissionId: String,
        essayScores: Map<String, Long>,
        autoScore: Long
    ): Result<Unit> {
        val docRef = db.collection("submissions").document(submissionId)
        return try {
            requireUserId()
            val essaySum = essayScores.values.sum().coerceAtLeast(0L)
            val total = (autoScore + essaySum).coerceAtLeast(0L)
            docRef.update(
                mapOf(
                    "essayScores" to essayScores,
                    "essayScore" to essaySum,
                    "totalScore" to total,
                    "status" to SubmissionStatus.GRADED.wireValue,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun grantProctorRole(targetUid: String, email: String, fullName: String): Result<Unit> {
        val cleanUid = targetUid.trim()
        val path = "proctors/$cleanUid"
        return try {
            val adminUid = requireUserId()
            require(cleanUid.matches(Regex("^[a-zA-Z0-9_\\-]{1,128}$"))) { "UID tidak valid." }
            require(email.trim().length in 3..150) { "Email petugas wajib diisi." }
            require(fullName.trim().length in 1..100) { "Nama petugas wajib diisi." }
            db.collection("proctors").document(cleanUid).set(
                mapOf(
                    "userId" to cleanUid,
                    "email" to email.trim().lowercase(),
                    "fullName" to fullName.trim(),
                    "grantedByUid" to adminUid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun revokeProctorRole(targetUid: String): Result<Unit> {
        val path = "proctors/$targetUid"
        return try {
            requireUserId()
            db.collection("proctors").document(targetUid).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun seedSampleSchoolData(): Result<Unit> {
        return try {
            val uid = requireUserId()
            val staffUids = currentStaffUids(uid)

            val cls7aRef = db.collection("classes").document("cls_7a")
            val cls8aRef = db.collection("classes").document("cls_8a")
            val cls9aRef = db.collection("classes").document("cls_9a")
            listOf(
                cls7aRef to Triple("VII-A", 7L, "Ibu Sari Rahayu, S.Pd"),
                cls8aRef to Triple("VIII-A", 8L, "Pak Ahmad Yani, S.Pd"),
                cls9aRef to Triple("IX-A", 9L, "Ibu Dewi Kartika, M.Pd")
            ).forEach { (ref, info) ->
                ref.set(
                    mapOf(
                        "name" to info.first,
                        "gradeLevel" to info.second,
                        "homeroomTeacher" to info.third,
                        "capacity" to 36L,
                        "published" to true,
                        "ownerId" to uid,
                        "staffUids" to staffUids,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val subMtkRef = db.collection("subjects").document("sub_mtk")
            val subIndRef = db.collection("subjects").document("sub_ind")
            val subIpaRef = db.collection("subjects").document("sub_ipa")
            listOf(
                subMtkRef to ("MTK" to "Matematika"),
                subIndRef to ("BIN" to "Bahasa Indonesia"),
                subIpaRef to ("IPA" to "Ilmu Pengetahuan Alam")
            ).forEach { (ref, pair) ->
                ref.set(
                    mapOf(
                        "code" to pair.first,
                        "name" to pair.second,
                        "published" to true,
                        "ownerId" to uid,
                        "staffUids" to staffUids,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val sampleStudents = listOf(
                listOf("stu_2026001", "2026001", "Rizky Pratama", "L", "cls_7a", "VII-A", "SMPN1-2026001-QR7A01"),
                listOf("stu_2026002", "2026002", "Siti Nurhaliza", "P", "cls_7a", "VII-A", "SMPN1-2026002-QR7A02"),
                listOf("stu_2026003", "2026003", "Dimas Saputra", "L", "cls_8a", "VIII-A", "SMPN1-2026003-QR8A03"),
                listOf("stu_2026004", "2026004", "Aulia Putri", "P", "cls_9a", "IX-A", "SMPN1-2026004-QR9A04")
            )
            sampleStudents.forEach { s ->
                db.collection("students").document(s[0]).set(
                    mapOf(
                        "nis" to s[1],
                        "fullName" to s[2],
                        "gender" to s[3],
                        "classId" to s[4],
                        "className" to s[5],
                        "qrToken" to s[6],
                        "linkedEmail" to "",
                        "authUserId" to if (s[0] == "stu_2026001") uid else "",
                        "ownerId" to uid,
                        "staffUids" to staffUids,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val now = System.currentTimeMillis()
            val examRef = db.collection("exams").document("exam_pas_mtk_7")
            examRef.set(
                mapOf(
                    "title" to "PAS Ganjil Matematika Kelas VII",
                    "subjectId" to "sub_mtk",
                    "subjectName" to "Matematika",
                    "classIds" to listOf("cls_7a", "cls_8a", "cls_9a"),
                    "classNames" to listOf("VII-A", "VIII-A", "IX-A"),
                    "startAt" to Timestamp(Date(now - 30 * 60 * 1000L)),
                    "endAt" to Timestamp(Date(now + 4 * 60 * 60 * 1000L)),
                    "durationMinutes" to 60L,
                    "tokenCode" to "MTK2026",
                    "published" to true,
                    "ownerId" to uid,
                    "staffUids" to staffUids,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val qList = listOf(
                mapOf(
                    "id" to "q_1",
                    "orderIndex" to 1L,
                    "questionType" to "PG",
                    "prompt" to "Hasil dari -12 + 7 x 3 adalah ...",
                    "optionA" to "-15",
                    "optionB" to "9",
                    "optionC" to "33",
                    "optionD" to "-33",
                    "correctOption" to "B",
                    "points" to 25L
                ),
                mapOf(
                    "id" to "q_2",
                    "orderIndex" to 2L,
                    "questionType" to "PG",
                    "prompt" to "Bentuk paling sederhana dari pecahan 24/36 adalah ...",
                    "optionA" to "2/3",
                    "optionB" to "3/4",
                    "optionC" to "4/6",
                    "optionD" to "6/9",
                    "correctOption" to "A",
                    "points" to 25L
                ),
                mapOf(
                    "id" to "q_3",
                    "orderIndex" to 3L,
                    "questionType" to "PG",
                    "prompt" to "Keliling sebuah persegi panjang dengan panjang 14 cm dan lebar 8 cm adalah ...",
                    "optionA" to "22 cm",
                    "optionB" to "44 cm",
                    "optionC" to "56 cm",
                    "optionD" to "112 cm",
                    "correctOption" to "B",
                    "points" to 25L
                ),
                mapOf(
                    "id" to "q_4",
                    "orderIndex" to 4L,
                    "questionType" to "ESSAY",
                    "prompt" to "Sebuah kelas di SMP Negeri 01 Banding Agung memiliki 36 siswa. Jika 5/9 bagian adalah siswa perempuan, berapa banyak siswa laki-laki? Jelaskan langkah perhitungannya!",
                    "optionA" to "",
                    "optionB" to "",
                    "optionC" to "",
                    "optionD" to "",
                    "correctOption" to "",
                    "points" to 25L
                )
            )
            qList.forEach { q ->
                val qId = q["id"] as String
                examRef.collection("questions").document(qId).set(
                    mapOf(
                        "examId" to examRef.id,
                        "orderIndex" to q["orderIndex"]!!,
                        "questionType" to q["questionType"]!!,
                        "prompt" to q["prompt"]!!,
                        "optionA" to q["optionA"]!!,
                        "optionB" to q["optionB"]!!,
                        "optionC" to q["optionC"]!!,
                        "optionD" to q["optionD"]!!,
                        "correctOption" to q["correctOption"]!!,
                        "points" to q["points"]!!,
                        "published" to true,
                        "ownerId" to uid,
                        "staffUids" to staffUids,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "seedSampleSchoolData")
            Result.failure(e)
        }
    }
}
