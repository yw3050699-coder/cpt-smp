package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.firestore.QrScanOutcome
import com.example.data.model.AttendanceRecord
import com.example.data.model.ExamSchedule
import com.example.data.model.UserRole
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SectionCard
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
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    role: UserRole,
    examsState: UiState<List<ExamSchedule>>,
    attendanceState: UiState<List<AttendanceRecord>>,
    scanOutcome: QrScanOutcome?,
    onScanToken: (ExamSchedule, String) -> Unit,
    onClearOutcome: () -> Unit
) {
    if (role != UserRole.ADMIN && role != UserRole.PROCTOR) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            EmptyStateCard(
                icon = Icons.Rounded.QrCodeScanner,
                title = "Khusus Petugas & Admin",
                description = "Halaman pemindaian absensi QR hanya tersedia untuk akun Petugas Pengawas atau Admin Sekolah."
            )
        }
        return
    }

    val context = LocalContext.current
    val exams = (examsState as? UiState.Success)?.data.orEmpty()
    val attendance = (attendanceState as? UiState.Success)?.data.orEmpty()

    var selectedExamId by remember(exams) {
        val now = System.currentTimeMillis()
        val active = exams.firstOrNull {
            val s = it.startAt?.toDate()?.time ?: 0L
            val e = it.endAt?.toDate()?.time ?: 0L
            now in s..e
        } ?: exams.firstOrNull()
        mutableStateOf(active?.id)
    }
    val selectedExam = exams.firstOrNull { it.id == selectedExamId }

    var examDropdownExpanded by remember { mutableStateOf(false) }
    var manualToken by remember { mutableStateOf("") }
    var showCamera by remember { mutableStateOf(false) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) showCamera = true
    }

    var lastScannedToken by remember { mutableStateOf("") }
    var lastScanTimeMs by remember { mutableLongStateOf(0L) }

    val examAttendance = remember(attendance, selectedExam) {
        if (selectedExam == null) emptyList()
        else attendance.filter { it.examId == selectedExam.id }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text("Scanner Absensi QR", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Validasi jadwal ujian aktif, token QR siswa, dan anti-absensi ganda.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (exams.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.QrCodeScanner,
                    title = "Belum ada jadwal ujian",
                    description = "Buat jadwal ujian terlebih dahulu di tab Ujian sebelum memindai absensi QR siswa."
                )
            }
            return@LazyColumn
        }

        item {
            SectionCard {
                ExposedDropdownMenuBox(
                    expanded = examDropdownExpanded,
                    onExpandedChange = { examDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedExam?.title ?: "Pilih Jadwal Ujian",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Jadwal Ujian Aktif") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = examDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = examDropdownExpanded,
                        onDismissRequest = { examDropdownExpanded = false }
                    ) {
                        exams.forEach { exam ->
                            DropdownMenuItem(
                                text = { Text("${exam.title} (${exam.subjectName})") },
                                onClick = {
                                    selectedExamId = exam.id
                                    examDropdownExpanded = false
                                    onClearOutcome()
                                }
                            )
                        }
                    }
                }

                selectedExam?.let { exam ->
                    Spacer(modifier = Modifier.height(8.dp))
                    val now = System.currentTimeMillis()
                    val s = exam.startAt?.toDate()?.time ?: 0L
                    val e = exam.endAt?.toDate()?.time ?: 0L
                    val inWindow = now in s..e
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        StatusPill(
                            text = if (inWindow) "Dalam Rentang Waktu" else if (now < s) "Belum Mulai" else "Waktu Habis",
                            containerColor = if (inWindow) StatusSuccessContainer else StatusWarningContainer,
                            contentColor = if (inWindow) StatusSuccess else StatusWarning
                        )
                        Text(
                            text = "${examAttendance.size} hadir",
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.primary
                        )
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
                    Text("Kamera Pemindai QR", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(
                        onClick = {
                            if (hasCameraPermission) {
                                showCamera = !showCamera
                            } else {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.testTag("toggle_camera_button")
                    ) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (showCamera && hasCameraPermission) "Tutup Kamera" else "Buka Kamera")
                    }
                }

                if (showCamera && hasCameraPermission && selectedExam != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    QrCameraPreviewBox(
                        onQrDetected = { token ->
                            val now = System.currentTimeMillis()
                            if (token != lastScannedToken || now - lastScanTimeMs > 3000L) {
                                lastScannedToken = token
                                lastScanTimeMs = now
                                onScanToken(selectedExam, token)
                            }
                        }
                    )
                } else if (!hasCameraPermission) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.camera_permission_rationale),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Atau Input Token QR Manual:", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = manualToken,
                        onValueChange = { manualToken = it },
                        placeholder = { Text("SMPN1-2026001-...") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("manual_qr_input")
                    )
                    Button(
                        onClick = {
                            selectedExam?.let {
                                onScanToken(it, manualToken)
                                manualToken = ""
                            }
                        },
                        enabled = selectedExam != null && manualToken.isNotBlank(),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("manual_qr_submit")
                    ) {
                        Text("Catat")
                    }
                }
            }
        }

        if (scanOutcome != null) {
            item {
                ScanOutcomeBanner(outcome = scanOutcome, onDismiss = onClearOutcome)
            }
        }

        item {
            Text(
                text = "Daftar Hadir QR (${examAttendance.size})",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (examAttendance.isEmpty()) {
            item {
                Text(
                    text = "Belum ada peserta tercatat hadir pada jadwal ujian ini.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(examAttendance, key = { it.id }) { rec ->
                Card(
                    shape = RoundedCornerShape(14.dp),
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
                            Text(rec.studentName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "NIS ${rec.nis} • ${rec.className}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            StatusPill("Hadir", StatusSuccessContainer, StatusSuccess)
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
}

@Composable
private fun ScanOutcomeBanner(outcome: QrScanOutcome, onDismiss: () -> Unit) {
    val (bg, fg, icon, title, subtitle) = when (outcome) {
        is QrScanOutcome.Success -> listOf(
            StatusSuccessContainer,
            StatusSuccess,
            Icons.Rounded.CheckCircle,
            "Hadir Tercatat!",
            "${outcome.record.studentName} (${outcome.record.nis} • ${outcome.record.className}) berhasil absen."
        )
        is QrScanOutcome.Duplicate -> listOf(
            StatusWarningContainer,
            StatusWarning,
            Icons.Rounded.WarningAmber,
            "Absensi Ganda Ditolak",
            "${outcome.existing.studentName} sudah tercatat hadir pada ${formatTimestampId(outcome.existing.scannedAt?.toDate())}."
        )
        is QrScanOutcome.Rejected -> listOf(
            StatusErrorContainer,
            StatusError,
            Icons.Rounded.ErrorOutline,
            "Pemindaian Ditolak",
            outcome.reason
        )
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = bg as Color),
        border = BorderStroke(1.dp, (fg as Color).copy(alpha = 0.4f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Icon(
                imageVector = icon as androidx.compose.ui.graphics.vector.ImageVector,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title as String, style = MaterialTheme.typography.titleSmall, color = fg, fontWeight = FontWeight.Bold)
                Text(subtitle as String, style = MaterialTheme.typography.bodySmall, color = fg)
            }
            TextButton(onClick = onDismiss) {
                Text("Tutup", color = fg)
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun QrCameraPreviewBox(
    onQrDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }
    val previewView = remember { PreviewView(context) }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            scanner.close()
        }
    }

    LaunchedEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        scanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                val raw = barcodes.firstOrNull()?.rawValue
                                if (!raw.isNullOrBlank()) {
                                    onQrDetected(raw)
                                }
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                    }
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            } catch (e: Exception) {
                Log.e("QrScanner", "Failed to bind CameraX", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
    }
}
