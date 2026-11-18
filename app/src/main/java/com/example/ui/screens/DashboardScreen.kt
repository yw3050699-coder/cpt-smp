package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Class
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AttendanceRecord
import com.example.data.model.ExamSchedule
import com.example.data.model.ExamSubmission
import com.example.data.model.RoleAssignment
import com.example.data.model.SchoolClass
import com.example.data.model.Student
import com.example.data.model.SubmissionStatus
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.preferences.AppPreferences
import com.example.data.preferences.ThemeMode
import com.example.ui.components.SchoolHeroBanner
import com.example.ui.components.SectionCard
import com.example.ui.components.StatTileCard
import com.example.ui.components.StatusPill
import com.example.ui.components.formatTimestampId
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessContainer
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningContainer
import com.example.ui.viewmodel.UiState
import com.example.util.ExamNotificationHelper

@Composable
fun DashboardScreen(
    currentUserId: String,
    profileState: UiState<UserProfile>,
    role: UserRole,
    appPreferences: AppPreferences,
    isDarkThemeActive: Boolean,
    classesState: UiState<List<SchoolClass>>,
    studentsState: UiState<List<Student>>,
    examsState: UiState<List<ExamSchedule>>,
    attendanceState: UiState<List<AttendanceRecord>>,
    submissionsState: UiState<List<ExamSubmission>>,
    proctorsState: UiState<List<RoleAssignment>>,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetNotificationsEnabled: (Boolean) -> Unit,
    onSeedSampleData: () -> Unit,
    onGrantProctor: (String, String, String) -> Unit,
    onRevokeProctor: (String) -> Unit,
    onNavigateToScanner: () -> Unit,
    onNavigateToExams: () -> Unit,
    onNavigateToStudents: () -> Unit,
    onNavigateToPortal: () -> Unit,
    onSignOut: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val classes = (classesState as? UiState.Success)?.data.orEmpty()
    val students = (studentsState as? UiState.Success)?.data.orEmpty()
    val exams = (examsState as? UiState.Success)?.data.orEmpty()
    val attendance = (attendanceState as? UiState.Success)?.data.orEmpty()
    val submissions = (submissionsState as? UiState.Success)?.data.orEmpty()
    val proctors = (proctorsState as? UiState.Success)?.data.orEmpty()
    val profile = (profileState as? UiState.Success)?.data

    val nowMs = System.currentTimeMillis()
    val activeExamsCount = exams.count { exam ->
        val start = exam.startAt?.toDate()?.time ?: 0L
        val end = exam.endAt?.toDate()?.time ?: 0L
        nowMs in start..end
    }
    val pendingEssaysCount = submissions.count { it.status == SubmissionStatus.SUBMITTED.wireValue }

    var showStaffDialog by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onSetNotificationsEnabled(true)
            ExamNotificationHelper.showNotification(
                context = context,
                enabled = true,
                title = "Notifikasi E-Ujian Aktif",
                message = "Anda akan menerima pembaruan absensi QR, pengumpulan ujian, dan nilai."
            )
            onShowMessage("Notifikasi diaktifkan.")
        } else {
            onSetNotificationsEnabled(false)
            onShowMessage("Izin notifikasi ditolak. Aktifkan melalui pengaturan sistem jika diperlukan.")
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SchoolHeroBanner(
                title = "SMP Negeri 01 Banding Agung",
                subtitle = "${profile?.fullName ?: "Pengguna"} • ${profile?.email ?: ""}",
                badgeText = "PERAN: ${role.label.uppercase()}",
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                onSetThemeMode(if (isDarkThemeActive) ThemeMode.LIGHT else ThemeMode.DARK)
                            },
                            modifier = Modifier.testTag("hero_theme_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isDarkThemeActive) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                contentDescription = if (isDarkThemeActive) "Mode Terang" else "Mode Gelap",
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = onSignOut,
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = "Keluar",
                                tint = Color.White
                            )
                        }
                    }
                }
            )
        }

        if (role == UserRole.ADMIN && classes.isEmpty() && students.isEmpty() && exams.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Mulai Cepat Tahun Ajaran",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Isi rombel VII-A s.d. IX-A, mata pelajaran inti, data contoh siswa QR, dan 1 jadwal ujian aktif beserta soal PG & Esai untuk langsung mencoba aplikasi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onSeedSampleData,
                            modifier = Modifier.testTag("seed_sample_button")
                        ) {
                            Text("Isi Data Contoh SMPN 1")
                        }
                    }
                }
            }
        }

        if (role == UserRole.ADMIN || role == UserRole.PROCTOR) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StatTileCard(
                        label = "Peserta Didik",
                        value = "${students.size}",
                        icon = Icons.Rounded.Groups,
                        accentColor = MaterialTheme.colorScheme.primary,
                        caption = "Kapasitas 1.000",
                        modifier = Modifier.weight(1f)
                    )
                    StatTileCard(
                        label = "Rombel VII–IX",
                        value = "${classes.size}",
                        icon = Icons.Rounded.Class,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        caption = "Kelas aktif",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StatTileCard(
                        label = "Jadwal Aktif",
                        value = "$activeExamsCount",
                        icon = Icons.Rounded.Assignment,
                        accentColor = Color(0xFF0D9488),
                        caption = "Dari ${exams.size} jadwal",
                        modifier = Modifier.weight(1f)
                    )
                    StatTileCard(
                        label = "Perlu Nilai Esai",
                        value = "$pendingEssaysCount",
                        icon = Icons.Rounded.RateReview,
                        accentColor = Color(0xFFD97706),
                        caption = "${attendance.size} hadir QR",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                SectionCard {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Kapasitas Sekolah",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${students.size} / 1.000 siswa",
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { (students.size / 1000f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onNavigateToScanner,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_scan_button")
                        ) {
                            Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan QR")
                        }
                        OutlinedButton(
                            onClick = onNavigateToExams,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_exams_button")
                        ) {
                            Text("Kelola Ujian")
                        }
                    }
                    if (role == UserRole.ADMIN) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = onNavigateToStudents,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Data Siswa")
                            }
                            OutlinedButton(
                                onClick = { showStaffDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("manage_proctors_button")
                            ) {
                                Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Petugas (${proctors.size})")
                            }
                        }
                    }
                }
            }
        } else {
            item {
                SectionCard {
                    Text(
                        text = "Portal Peserta Ujian",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tunjukkan Kartu QR saat absensi di ruang ujian dan kerjakan ujian sesuai jadwal yang aktif.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onNavigateToPortal,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Buka Kartu QR & Ujian Saya")
                    }
                }
            }
        }

        item {
            SectionCard {
                Text(
                    text = "Pengaturan Tampilan & Notifikasi",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (isDarkThemeActive) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Mode Gelap", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Tema aktif: ${appPreferences.themeMode.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = isDarkThemeActive,
                        onCheckedChange = { checked ->
                            onSetThemeMode(if (checked) ThemeMode.DARK else ThemeMode.LIGHT)
                        },
                        modifier = Modifier.testTag("dark_mode_switch")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = appPreferences.themeMode == mode,
                            onClick = { onSetThemeMode(mode) },
                            label = { Text(mode.label) },
                            modifier = Modifier.testTag("theme_${mode.wireValue}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (appPreferences.notificationsEnabled) {
                                Icons.Rounded.NotificationsActive
                            } else {
                                Icons.Rounded.NotificationsOff
                            },
                            contentDescription = null,
                            tint = if (appPreferences.notificationsEnabled) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Notifikasi E-Ujian", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = if (appPreferences.notificationsEnabled) {
                                    "Aktif untuk absensi QR, ujian & nilai"
                                } else {
                                    "Nonaktif — ketuk untuk mengaktifkan"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = appPreferences.notificationsEnabled,
                        onCheckedChange = { enable ->
                            if (!enable) {
                                onSetNotificationsEnabled(false)
                                onShowMessage("Notifikasi dinonaktifkan.")
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !ExamNotificationHelper.hasNotificationPermission(context)
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onSetNotificationsEnabled(true)
                                ExamNotificationHelper.showNotification(
                                    context = context,
                                    enabled = true,
                                    title = "Notifikasi E-Ujian Aktif",
                                    message = "Anda akan menerima pembaruan absensi QR, pengumpulan ujian, dan nilai."
                                )
                                onShowMessage("Notifikasi diaktifkan.")
                            }
                        },
                        modifier = Modifier.testTag("notifications_switch")
                    )
                }

                if (appPreferences.notificationsEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            val sent = ExamNotificationHelper.showNotification(
                                context = context,
                                enabled = true,
                                title = "Pengingat E-Ujian SMPN 1",
                                message = "Sistem notifikasi berjalan normal. Jadwal aktif saat ini: $activeExamsCount ujian."
                            )
                            onShowMessage(
                                if (sent) "Notifikasi uji dikirim." else "Izin notifikasi sistem belum aktif."
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_notification_button")
                    ) {
                        Text("Kirim Notifikasi Uji")
                    }
                }
            }
        }

        item {
            SectionCard {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ID Akun Anda",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = currentUserId,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(currentUserId))
                        onShowMessage("UID akun disalin ke clipboard.")
                    }) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Salin UID")
                    }
                }
            }
        }

        item {
            SectionCard {
                Text(
                    text = "Aktivitas Absensi QR Terbaru",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (attendance.isEmpty()) {
                    Text(
                        text = "Belum ada catatan kehadiran QR yang masuk.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    attendance.take(6).forEach { rec ->
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rec.studentName,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "${rec.nis} • ${rec.className} • ${rec.examTitle}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                StatusPill(
                                    text = "Hadir",
                                    containerColor = StatusSuccessContainer,
                                    contentColor = StatusSuccess
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = formatTimestampId(rec.scannedAt?.toDate()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        if (submissions.isNotEmpty()) {
            item {
                SectionCard {
                    Text(
                        text = "Pengumpulan Ujian Terbaru",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    submissions.take(5).forEach { sub ->
                        val status = SubmissionStatus.fromWire(sub.status)
                        val (bg, fg) = when (status) {
                            SubmissionStatus.GRADED -> StatusSuccessContainer to StatusSuccess
                            SubmissionStatus.SUBMITTED -> StatusWarningContainer to StatusWarning
                            SubmissionStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(sub.studentName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${sub.examTitle} • ${sub.className}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                StatusPill(text = status.label, containerColor = bg, contentColor = fg)
                                Text(
                                    text = "${sub.totalScore}/${sub.maxScore}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showStaffDialog) {
        ManageProctorsDialog(
            proctors = proctors,
            onDismiss = { showStaffDialog = false },
            onAddProctor = onGrantProctor,
            onRevokeProctor = onRevokeProctor
        )
    }
}

@Composable
private fun ManageProctorsDialog(
    proctors: List<RoleAssignment>,
    onDismiss: () -> Unit,
    onAddProctor: (String, String, String) -> Unit,
    onRevokeProctor: (String) -> Unit
) {
    var uid by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kelola Petugas Pengawas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Masukkan UID akun Google pengawas/guru untuk memberikan akses Scanner QR & penilaian esai.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = uid,
                    onValueChange = { uid = it },
                    label = { Text("UID Akun Petugas") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Nama Lengkap") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Google") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (proctors.isNotEmpty()) {
                    Text("Daftar Petugas Aktif:", style = MaterialTheme.typography.titleSmall)
                    proctors.forEach { p ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(p.fullName, style = MaterialTheme.typography.labelLarge)
                                        Text(p.email, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                IconButton(onClick = { onRevokeProctor(p.userId) }) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = "Cabut akses",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAddProctor(uid, email, fullName)
                    uid = ""
                    email = ""
                    fullName = ""
                },
                enabled = uid.isNotBlank() && email.isNotBlank() && fullName.isNotBlank()
            ) {
                Text("Tambah Petugas")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}
