package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ExamSchedule
import com.example.data.model.ExamSubmission
import com.example.data.model.Student
import com.example.data.model.SubmissionStatus
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SectionCard
import com.example.ui.components.StatusPill
import com.example.ui.components.formatTimestampId
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessContainer
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningContainer
import com.example.ui.viewmodel.UiState
import com.example.util.PdfReportPrinter
import com.example.util.QrGenerator

@Composable
fun StudentPortalScreen(
    role: UserRole,
    profileState: UiState<UserProfile>,
    studentsState: UiState<List<Student>>,
    examsState: UiState<List<ExamSchedule>>,
    submissionsState: UiState<List<ExamSubmission>>,
    onBeginExam: (ExamSchedule, Student) -> Unit
) {
    val context = LocalContext.current
    val profile = (profileState as? UiState.Success)?.data
    val students = (studentsState as? UiState.Success)?.data.orEmpty()
    val exams = (examsState as? UiState.Success)?.data.orEmpty()
    val submissions = (submissionsState as? UiState.Success)?.data.orEmpty()

    var previewStudentId by remember(students, profile) {
        val own = students.firstOrNull { it.id == profile?.studentId || it.authUserId == profile?.userId }
            ?: students.firstOrNull()
        mutableStateOf(own?.id)
    }
    val activeStudent = students.firstOrNull { it.id == previewStudentId }

    var examPendingToken by remember { mutableStateOf<ExamSchedule?>(null) }
    var tokenInput by remember { mutableStateOf("") }
    var tokenError by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text("Portal Peserta & Kartu QR", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = if (role == UserRole.ADMIN) {
                        "Mode pratinjau peserta untuk menguji kartu QR dan alur pengerjaan ujian."
                    } else {
                        "Tunjukkan kartu QR saat absensi dan kerjakan ujian aktif Anda."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (role == UserRole.ADMIN && students.size > 1) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    students.take(10).forEach { st ->
                        FilterChip(
                            selected = st.id == activeStudent?.id,
                            onClick = { previewStudentId = st.id },
                            label = { Text("${st.fullName} (${st.className})") }
                        )
                    }
                }
            }
        }

        item {
            if (activeStudent == null) {
                EmptyStateCard(
                    icon = Icons.Rounded.Badge,
                    title = "Akun Belum Tertaut ke Data Siswa",
                    description = "Minta Admin Sekolah menambahkan email Google Anda (${profile?.email ?: "-"}) pada data siswa SMP Negeri 01 Banding Agung agar Kartu QR & ujian Anda muncul otomatis."
                )
            } else {
                val qrBmp = remember(activeStudent.qrToken) {
                    QrGenerator.generateQrImageBitmap(activeStudent.qrToken, 480)
                }
                SectionCard {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            StatusPill(
                                text = "KARTU PESERTA RESMI",
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(activeStudent.fullName, style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = "NIS ${activeStudent.nis} • Kelas ${activeStudent.className}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (qrBmp != null) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Image(
                                    bitmap = qrBmp,
                                    contentDescription = "QR Peserta ${activeStudent.fullName}",
                                    modifier = Modifier
                                        .size(104.dp)
                                        .padding(6.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = activeStudent.qrToken,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = { PdfReportPrinter.printStudentQrCard(context, activeStudent) }) {
                            Icon(Icons.Rounded.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cetak Kartu")
                        }
                    }
                }
            }
        }

        item {
            Text("Daftar Ujian Tersedia", style = MaterialTheme.typography.titleMedium)
        }

        val relevantExams = if (activeStudent == null) {
            exams
        } else {
            exams.filter { it.classIds.isEmpty() || activeStudent.classId in it.classIds }
        }

        if (relevantExams.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.Quiz,
                    title = "Tidak ada jadwal ujian",
                    description = "Belum ada ujian terjadwal untuk rombel Anda saat ini."
                )
            }
        } else {
            items(relevantExams, key = { it.id }) { exam ->
                val sub = activeStudent?.let { st ->
                    submissions.firstOrNull { it.examId == exam.id && it.studentId == st.id }
                }
                val now = System.currentTimeMillis()
                val startMs = exam.startAt?.toDate()?.time ?: 0L
                val endMs = exam.endAt?.toDate()?.time ?: 0L
                val isOpen = now in startMs..endMs

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(exam.subjectName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                            if (sub != null) {
                                val status = SubmissionStatus.fromWire(sub.status)
                                StatusPill(
                                    text = if (status == SubmissionStatus.IN_PROGRESS) "Lanjutkan" else "${status.label} (${sub.totalScore}/${sub.maxScore})",
                                    containerColor = if (status == SubmissionStatus.GRADED) StatusSuccessContainer else StatusWarningContainer,
                                    contentColor = if (status == SubmissionStatus.GRADED) StatusSuccess else StatusWarning
                                )
                            } else {
                                StatusPill(
                                    text = if (isOpen) "Dibuka" else if (now < startMs) "Belum Mulai" else "Ditutup",
                                    containerColor = if (isOpen) StatusSuccessContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isOpen) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(exam.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Durasi ${exam.durationMinutes} menit • ${formatTimestampId(exam.startAt?.toDate())}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        if (activeStudent != null) {
                            val canEnter = isOpen && (sub == null || sub.status == SubmissionStatus.IN_PROGRESS.wireValue)
                            Button(
                                onClick = {
                                    if (sub != null && sub.status == SubmissionStatus.IN_PROGRESS.wireValue) {
                                        onBeginExam(exam, activeStudent)
                                    } else {
                                        tokenInput = if (role == UserRole.ADMIN) exam.tokenCode else ""
                                        tokenError = null
                                        examPendingToken = exam
                                    }
                                },
                                enabled = canEnter,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("start_exam_${exam.id}")
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    when {
                                        sub != null && sub.status == SubmissionStatus.IN_PROGRESS.wireValue -> "Lanjutkan Ujian"
                                        sub != null -> "Sudah Dikumpulkan"
                                        isOpen -> "Kerjakan Ujian"
                                        else -> "Di Luar Jadwal"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    examPendingToken?.let { exam ->
        AlertDialog(
            onDismissRequest = { examPendingToken = null },
            title = { Text("Masukkan Token Ujian") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Masukkan token untuk memulai '${exam.title}'.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = {
                            tokenInput = it.uppercase()
                            tokenError = null
                        },
                        label = { Text("Token Ujian") },
                        singleLine = true,
                        isError = tokenError != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("exam_token_input")
                    )
                    if (tokenError != null) {
                        Text(
                            text = tokenError.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tokenInput.trim().equals(exam.tokenCode.trim(), ignoreCase = true)) {
                            val st = activeStudent
                            examPendingToken = null
                            if (st != null) onBeginExam(exam, st)
                        } else {
                            tokenError = "Token ujian tidak sesuai."
                        }
                    },
                    modifier = Modifier.testTag("confirm_exam_token")
                ) { Text("Mulai Ujian") }
            },
            dismissButton = {
                TextButton(onClick = { examPendingToken = null }) { Text("Batal") }
            }
        )
    }
}
