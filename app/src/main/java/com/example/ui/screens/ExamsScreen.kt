package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AttendanceRecord
import com.example.data.model.ExamQuestion
import com.example.data.model.ExamSchedule
import com.example.data.model.ExamSubmission
import com.example.data.model.QuestionType
import com.example.data.model.SchoolClass
import com.example.data.model.Subject
import com.example.data.model.SubmissionStatus
import com.example.data.model.UserRole
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.components.formatTimestampId
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorContainer
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessContainer
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningContainer
import com.example.ui.viewmodel.UiState
import com.example.util.PdfReportPrinter
import java.util.Date

@Composable
fun ExamsScreen(
    role: UserRole,
    subjectsState: UiState<List<Subject>>,
    classesState: UiState<List<SchoolClass>>,
    examsState: UiState<List<ExamSchedule>>,
    selectedExam: ExamSchedule?,
    questionsState: UiState<List<ExamQuestion>>,
    attendanceState: UiState<List<AttendanceRecord>>,
    submissionsState: UiState<List<ExamSubmission>>,
    onSelectExam: (ExamSchedule?) -> Unit,
    onAddSubject: (String, String) -> Unit,
    onAddExam: (String, Subject, List<SchoolClass>, Date, Date, Long, String) -> Unit,
    onDeleteExam: (String) -> Unit,
    onAddQuestion: (String, Long, QuestionType, String, String, String, String, String, String, Long) -> Unit,
    onDeleteQuestion: (String, String) -> Unit,
    onGradeEssay: (ExamSubmission, Map<String, Long>) -> Unit
) {
    if (selectedExam != null) {
        BackHandler { onSelectExam(null) }
        ExamDetailScreen(
            exam = selectedExam,
            role = role,
            questionsState = questionsState,
            attendance = (attendanceState as? UiState.Success)?.data.orEmpty().filter { it.examId == selectedExam.id },
            submissions = (submissionsState as? UiState.Success)?.data.orEmpty().filter { it.examId == selectedExam.id },
            onBack = { onSelectExam(null) },
            onAddQuestion = { order, type, prompt, a, b, c, d, correct, pts ->
                onAddQuestion(selectedExam.id, order, type, prompt, a, b, c, d, correct, pts)
            },
            onDeleteQuestion = { qId -> onDeleteQuestion(selectedExam.id, qId) },
            onGradeEssay = onGradeEssay
        )
        return
    }

    val subjects = (subjectsState as? UiState.Success)?.data.orEmpty()
    val classes = (classesState as? UiState.Success)?.data.orEmpty()
    val exams = (examsState as? UiState.Success)?.data.orEmpty()

    var showAddSubjectDialog by remember { mutableStateOf(false) }
    var showAddExamDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            if (role == UserRole.ADMIN) {
                FloatingActionButton(
                    onClick = { showAddExamDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("add_exam_fab")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Buat Jadwal Ujian")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text("Jadwal & Bank Soal", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "${exams.size} jadwal ujian • ${subjects.size} mata pelajaran",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (role == UserRole.ADMIN) {
                    OutlinedButton(
                        onClick = { showAddSubjectDialog = true },
                        modifier = Modifier.testTag("add_subject_button")
                    ) {
                        Icon(Icons.Rounded.Book, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mapel")
                    }
                }
            }

            if (subjects.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    subjects.forEach { sub ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = "${sub.code} • ${sub.name}",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (examsState) {
                is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    EmptyStateCard(
                        icon = Icons.Rounded.Assignment,
                        title = "Gagal memuat jadwal ujian",
                        description = examsState.message
                    )
                }
                is UiState.Success -> {
                    if (exams.isEmpty()) {
                        EmptyStateCard(
                            icon = Icons.Rounded.Assignment,
                            title = "Belum ada jadwal ujian",
                            description = "Buat mata pelajaran dan jadwal ujian untuk mengelola bank soal, absensi QR, serta hasil nilai siswa.",
                            actionLabel = if (role == UserRole.ADMIN) {
                                if (subjects.isEmpty()) "Tambah Mapel" else "Buat Jadwal Ujian"
                            } else null,
                            onAction = if (role == UserRole.ADMIN) {
                                {
                                    if (subjects.isEmpty()) showAddSubjectDialog = true
                                    else showAddExamDialog = true
                                }
                            } else null
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(exams, key = { it.id }) { exam ->
                                ExamScheduleCard(
                                    exam = exam,
                                    canDelete = role == UserRole.ADMIN,
                                    onClick = { onSelectExam(exam) },
                                    onDelete = { onDeleteExam(exam.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSubjectDialog) {
        AddSubjectDialog(
            onDismiss = { showAddSubjectDialog = false },
            onSubmit = { code, name ->
                onAddSubject(code, name)
                showAddSubjectDialog = false
            }
        )
    }

    if (showAddExamDialog) {
        if (subjects.isEmpty()) {
            AlertDialog(
                onDismissRequest = { showAddExamDialog = false },
                title = { Text("Mata Pelajaran Diperlukan") },
                text = { Text("Tambahkan minimal 1 mata pelajaran sebelum membuat jadwal ujian.") },
                confirmButton = {
                    Button(onClick = {
                        showAddExamDialog = false
                        showAddSubjectDialog = true
                    }) { Text("Tambah Mapel") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddExamDialog = false }) { Text("Batal") }
                }
            )
        } else {
            AddExamDialog(
                subjects = subjects,
                classes = classes,
                onDismiss = { showAddExamDialog = false },
                onSubmit = { title, sub, clsList, start, end, dur, token ->
                    onAddExam(title, sub, clsList, start, end, dur, token)
                    showAddExamDialog = false
                }
            )
        }
    }
}

@Composable
private fun ExamScheduleCard(
    exam: ExamSchedule,
    canDelete: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val nowMs = System.currentTimeMillis()
    val startMs = exam.startAt?.toDate()?.time ?: 0L
    val endMs = exam.endAt?.toDate()?.time ?: 0L
    val (statusLabel, statusBg, statusFg) = when {
        nowMs in startMs..endMs -> Triple("Berlangsung", StatusSuccessContainer, StatusSuccess)
        nowMs < startMs -> Triple("Terjadwal", StatusWarningContainer, StatusWarning)
        else -> Triple("Selesai", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("exam_card_${exam.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                StatusPill(text = statusLabel, containerColor = statusBg, contentColor = statusFg)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "TOKEN: ${exam.tokenCode}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (canDelete) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Hapus Ujian", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(exam.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${exam.subjectName} • ${exam.durationMinutes} menit",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Mulai: ${formatTimestampId(exam.startAt?.toDate())} — Selesai: ${formatTimestampId(exam.endAt?.toDate())}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (exam.classNames.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Rombel: ${exam.classNames.joinToString(", ")}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamDetailScreen(
    exam: ExamSchedule,
    role: UserRole,
    questionsState: UiState<List<ExamQuestion>>,
    attendance: List<AttendanceRecord>,
    submissions: List<ExamSubmission>,
    onBack: () -> Unit,
    onAddQuestion: (Long, QuestionType, String, String, String, String, String, String, Long) -> Unit,
    onDeleteQuestion: (String) -> Unit,
    onGradeEssay: (ExamSubmission, Map<String, Long>) -> Unit
) {
    val context = LocalContext.current
    val questions = (questionsState as? UiState.Success)?.data.orEmpty()
    var activeTab by remember { mutableIntStateOf(0) }
    var showAddQuestionDialog by remember { mutableStateOf(false) }
    var submissionToGrade by remember { mutableStateOf<ExamSubmission?>(null) }

    Scaffold(
        floatingActionButton = {
            if (role == UserRole.ADMIN && activeTab == 0) {
                FloatingActionButton(
                    onClick = { showAddQuestionDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("add_question_fab")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Tambah Soal")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(exam.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${exam.subjectName} • Token ${exam.tokenCode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = { PdfReportPrinter.printExamReport(context, exam, submissions, attendance) },
                    modifier = Modifier.padding(end = 8.dp).testTag("print_report_button")
                ) {
                    Icon(Icons.Rounded.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("PDF")
                }
            }

            SecondaryTabRow(selectedTabIndex = activeTab) {
                Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text("Soal (${questions.size})") })
                Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text("Hadir (${attendance.size})") })
                Tab(selected = activeTab == 2, onClick = { activeTab = 2 }, text = { Text("Hasil (${submissions.size})") })
            }

            when (activeTab) {
                0 -> {
                    if (questions.isEmpty()) {
                        Box(Modifier.padding(16.dp)) {
                            EmptyStateCard(
                                icon = Icons.Rounded.Quiz,
                                title = "Bank soal masih kosong",
                                description = "Tambahkan soal Pilihan Ganda (PG) dan Esai untuk ujian ini.",
                                actionLabel = if (role == UserRole.ADMIN) "Tambah Soal" else null,
                                onAction = if (role == UserRole.ADMIN) { { showAddQuestionDialog = true } } else null
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 88.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(questions, key = { it.id }) { q ->
                                QuestionBankItemCard(
                                    question = q,
                                    canDelete = role == UserRole.ADMIN,
                                    onDelete = { onDeleteQuestion(q.id) }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    if (attendance.isEmpty()) {
                        Box(Modifier.padding(16.dp)) {
                            EmptyStateCard(
                                icon = Icons.Rounded.Assignment,
                                title = "Belum ada absensi QR",
                                description = "Petugas dapat memindai kartu QR siswa pada tab Scan QR saat ujian berlangsung."
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(attendance, key = { it.id }) { rec ->
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp)
                                    ) {
                                        Column {
                                            Text(rec.studentName, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                "NIS ${rec.nis} • ${rec.className} • Petugas: ${rec.scannedByName}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        StatusPill("Hadir", StatusSuccessContainer, StatusSuccess)
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    if (submissions.isEmpty()) {
                        Box(Modifier.padding(16.dp)) {
                            EmptyStateCard(
                                icon = Icons.Rounded.RateReview,
                                title = "Belum ada pengumpulan ujian",
                                description = "Hasil nilai PG otomatis dan penilaian esai akan tampil di sini."
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(submissions, key = { it.id }) { sub ->
                                val status = SubmissionStatus.fromWire(sub.status)
                                val hasEssay = questions.any { QuestionType.fromWire(it.questionType) == QuestionType.ESSAY }
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(sub.studentName, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                "NIS ${sub.nis} • ${sub.className} • PG: ${sub.autoScore} | Esai: ${sub.essayScore}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            StatusPill(
                                                text = status.label,
                                                containerColor = if (status == SubmissionStatus.GRADED) StatusSuccessContainer else StatusWarningContainer,
                                                contentColor = if (status == SubmissionStatus.GRADED) StatusSuccess else StatusWarning
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "${sub.totalScore}/${sub.maxScore}",
                                                style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            if (hasEssay && (role == UserRole.ADMIN || role == UserRole.PROCTOR)) {
                                                TextButton(onClick = { submissionToGrade = sub }) {
                                                    Text("Nilai Esai")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddQuestionDialog) {
        AddQuestionDialog(
            nextOrder = (questions.maxOfOrNull { it.orderIndex } ?: 0L) + 1L,
            onDismiss = { showAddQuestionDialog = false },
            onSubmit = { order, type, prompt, a, b, c, d, correct, pts ->
                onAddQuestion(order, type, prompt, a, b, c, d, correct, pts)
                showAddQuestionDialog = false
            }
        )
    }

    submissionToGrade?.let { sub ->
        GradeEssayDialog(
            submission = sub,
            essayQuestions = questions.filter { QuestionType.fromWire(it.questionType) == QuestionType.ESSAY },
            onDismiss = { submissionToGrade = null },
            onSave = { scores ->
                onGradeEssay(sub, scores)
                submissionToGrade = null
            }
        )
    }
}

@Composable
private fun QuestionBankItemCard(
    question: ExamQuestion,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    val type = QuestionType.fromWire(question.questionType)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(
                        text = "#${question.orderIndex} • ${type.label}",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "${question.points} poin",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                if (canDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Hapus Soal", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(question.prompt, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (type == QuestionType.PG) {
                Spacer(modifier = Modifier.height(8.dp))
                listOf("A" to question.optionA, "B" to question.optionB, "C" to question.optionC, "D" to question.optionD)
                    .filter { it.second.isNotBlank() }
                    .forEach { (key, text) ->
                        val isKey = key.equals(question.correctOption, ignoreCase = true)
                        Text(
                            text = "$key. $text" + if (isKey) "  ✓" else "",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isKey) FontWeight.Bold else FontWeight.Normal,
                            color = if (isKey) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
            }
        }
    }
}

@Composable
private fun AddSubjectDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Mata Pelajaran") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Kode Mapel (cth: MTK)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Mata Pelajaran") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(code, name) },
                enabled = code.trim().length >= 2 && name.trim().length >= 2
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExamDialog(
    subjects: List<Subject>,
    classes: List<SchoolClass>,
    onDismiss: () -> Unit,
    onSubmit: (String, Subject, List<SchoolClass>, Date, Date, Long, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf(subjects.first()) }
    var subExpanded by remember { mutableStateOf(false) }
    val selectedClasses = remember { mutableStateListOf<SchoolClass>().apply { addAll(classes) } }
    var duration by remember { mutableStateOf("60") }
    var tokenCode by remember { mutableStateOf("UJIAN1") }
    var windowPreset by remember { mutableIntStateOf(0) } // 0=active now 4h, 1=active 24h, 2=upcoming tomorrow

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buat Jadwal Ujian") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Judul Ujian (cth: PTS IPA VII)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(expanded = subExpanded, onExpandedChange = { subExpanded = it }) {
                    OutlinedTextField(
                        value = selectedSubject.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Mata Pelajaran") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = subExpanded, onDismissRequest = { subExpanded = false }) {
                        subjects.forEach { sub ->
                            DropdownMenuItem(
                                text = { Text("${sub.code} - ${sub.name}") },
                                onClick = {
                                    selectedSubject = sub
                                    subExpanded = false
                                }
                            )
                        }
                    }
                }
                if (classes.isNotEmpty()) {
                    Text("Rombel Peserta:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        classes.forEach { cls ->
                            val isSelected = selectedClasses.any { it.id == cls.id }
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (isSelected) selectedClasses.removeAll { it.id == cls.id }
                                    else selectedClasses.add(cls)
                                },
                                label = { Text(cls.name) }
                            )
                        }
                    }
                }
                Text("Rentang Waktu Aktif QR & Ujian:", style = MaterialTheme.typography.labelMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    FilterChip(selected = windowPreset == 0, onClick = { windowPreset = 0 }, label = { Text("Aktif 4 Jam") })
                    FilterChip(selected = windowPreset == 1, onClick = { windowPreset = 1 }, label = { Text("Aktif 24 Jam") })
                    FilterChip(selected = windowPreset == 2, onClick = { windowPreset = 2 }, label = { Text("Besok (Terjadwal)") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = duration,
                        onValueChange = { duration = it.filter(Char::isDigit) },
                        label = { Text("Durasi (mnt)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = tokenCode,
                        onValueChange = { tokenCode = it.uppercase() },
                        label = { Text("Token") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val now = System.currentTimeMillis()
                    val (start, end) = when (windowPreset) {
                        1 -> Date(now - 60_000L) to Date(now + 24 * 3600_000L)
                        2 -> Date(now + 24 * 3600_000L) to Date(now + 26 * 3600_000L)
                        else -> Date(now - 60_000L) to Date(now + 4 * 3600_000L)
                    }
                    onSubmit(
                        title,
                        selectedSubject,
                        selectedClasses.toList(),
                        start,
                        end,
                        duration.toLongOrNull()?.coerceIn(5L, 300L) ?: 60L,
                        tokenCode
                    )
                },
                enabled = title.trim().length >= 2 && tokenCode.trim().length >= 3
            ) { Text("Simpan Jadwal") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun AddQuestionDialog(
    nextOrder: Long,
    onDismiss: () -> Unit,
    onSubmit: (Long, QuestionType, String, String, String, String, String, String, Long) -> Unit
) {
    var qType by remember { mutableStateOf(QuestionType.PG) }
    var prompt by remember { mutableStateOf("") }
    var optA by remember { mutableStateOf("") }
    var optB by remember { mutableStateOf("") }
    var optC by remember { mutableStateOf("") }
    var optD by remember { mutableStateOf("") }
    var correct by remember { mutableStateOf("A") }
    var points by remember { mutableStateOf("10") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Butir Soal #$nextOrder") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = qType == QuestionType.PG,
                        onClick = { qType = QuestionType.PG },
                        label = { Text("Pilihan Ganda") }
                    )
                    FilterChip(
                        selected = qType == QuestionType.ESSAY,
                        onClick = { qType = QuestionType.ESSAY },
                        label = { Text("Esai") }
                    )
                }
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Pertanyaan / Soal") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                if (qType == QuestionType.PG) {
                    OutlinedTextField(value = optA, onValueChange = { optA = it }, label = { Text("Pilihan A") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = optB, onValueChange = { optB = it }, label = { Text("Pilihan B") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = optC, onValueChange = { optC = it }, label = { Text("Pilihan C (opsional)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = optD, onValueChange = { optD = it }, label = { Text("Pilihan D (opsional)") }, modifier = Modifier.fillMaxWidth())
                    Text("Kunci Jawaban:", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("A", "B", "C", "D").forEach { key ->
                            FilterChip(selected = correct == key, onClick = { correct = key }, label = { Text(key) })
                        }
                    }
                }
                OutlinedTextField(
                    value = points,
                    onValueChange = { points = it.filter(Char::isDigit) },
                    label = { Text("Bobot Nilai") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(
                        nextOrder,
                        qType,
                        prompt,
                        optA,
                        optB,
                        optC,
                        optD,
                        correct,
                        points.toLongOrNull()?.coerceIn(1L, 100L) ?: 10L
                    )
                },
                enabled = prompt.trim().length >= 2 && (qType == QuestionType.ESSAY || (optA.isNotBlank() && optB.isNotBlank()))
            ) { Text("Simpan Soal") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun GradeEssayDialog(
    submission: ExamSubmission,
    essayQuestions: List<ExamQuestion>,
    onDismiss: () -> Unit,
    onSave: (Map<String, Long>) -> Unit
) {
    val scoresMap = remember(submission.id) {
        mutableStateMapOf<String, String>().apply {
            essayQuestions.forEach { q ->
                put(q.id, (submission.essayScores[q.id] ?: 0L).toString())
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Penilaian Esai • ${submission.studentName}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                essayQuestions.forEach { q ->
                    val ans = submission.answers[q.id].orEmpty().ifBlank { "(Tidak dijawab)" }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Soal #${q.orderIndex} (Maks ${q.points} poin)", style = MaterialTheme.typography.labelMedium)
                            Text(q.prompt, style = MaterialTheme.typography.bodySmall)
                            Text("Jawaban Siswa: $ans", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            OutlinedTextField(
                                value = scoresMap[q.id].orEmpty(),
                                onValueChange = { v -> scoresMap[q.id] = v.filter(Char::isDigit) },
                                label = { Text("Skor Esai (0..${q.points})") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsed = essayQuestions.associate { q ->
                    val raw = scoresMap[q.id]?.toLongOrNull() ?: 0L
                    q.id to raw.coerceIn(0L, q.points)
                }
                onSave(parsed)
            }) { Text("Simpan Nilai") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
