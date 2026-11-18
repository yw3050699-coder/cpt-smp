package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Class
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.SchoolClass
import com.example.data.model.Student
import com.example.data.model.UserRole
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.UiState
import com.example.util.PdfReportPrinter
import com.example.util.QrGenerator

@Composable
fun StudentsScreen(
    role: UserRole,
    classesState: UiState<List<SchoolClass>>,
    studentsState: UiState<List<Student>>,
    onAddClass: (String, Long, String, Long) -> Unit,
    onAddStudent: (String, String, String, SchoolClass, String) -> Unit,
    onDeleteStudent: (String) -> Unit
) {
    val classes = (classesState as? UiState.Success)?.data.orEmpty()
    val students = (studentsState as? UiState.Success)?.data.orEmpty()

    var searchQuery by remember { mutableStateOf("") }
    var selectedClassFilter by remember { mutableStateOf<String?>(null) }
    var showAddClassDialog by remember { mutableStateOf(false) }
    var showAddStudentDialog by remember { mutableStateOf(false) }
    var selectedStudentForQr by remember { mutableStateOf<Student?>(null) }

    val filteredStudents by remember(students, searchQuery, selectedClassFilter) {
        derivedStateOf {
            students.filter { student ->
                val matchesClass = selectedClassFilter == null || student.classId == selectedClassFilter
                val q = searchQuery.trim()
                val matchesQuery = q.isBlank() ||
                    student.fullName.contains(q, ignoreCase = true) ||
                    student.nis.contains(q, ignoreCase = true) ||
                    student.className.contains(q, ignoreCase = true)
                matchesClass && matchesQuery
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            if (role == UserRole.ADMIN) {
                FloatingActionButton(
                    onClick = { showAddStudentDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("add_student_fab")
                ) {
                    Icon(Icons.Rounded.PersonAdd, contentDescription = "Tambah Siswa")
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
                    Text("Data Siswa & Rombel", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "${students.size} / 1.000 siswa • ${classes.size} rombel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (role == UserRole.ADMIN) {
                    OutlinedButton(
                        onClick = { showAddClassDialog = true },
                        modifier = Modifier.testTag("add_class_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rombel")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text("Cari NIS, nama siswa, atau rombel...") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("student_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                FilterChip(
                    selected = selectedClassFilter == null,
                    onClick = { selectedClassFilter = null },
                    label = { Text("Semua (${students.size})") }
                )
                classes.forEach { cls ->
                    val count = students.count { it.classId == cls.id }
                    FilterChip(
                        selected = selectedClassFilter == cls.id,
                        onClick = {
                            selectedClassFilter = if (selectedClassFilter == cls.id) null else cls.id
                        },
                        label = { Text("${cls.name} ($count)") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (studentsState) {
                is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    EmptyStateCard(
                        icon = Icons.Rounded.Class,
                        title = "Gagal memuat data siswa",
                        description = studentsState.message
                    )
                }
                is UiState.Success -> {
                    if (filteredStudents.isEmpty()) {
                        EmptyStateCard(
                            icon = Icons.Rounded.PersonAdd,
                            title = if (classes.isEmpty()) "Belum ada rombel" else "Belum ada siswa",
                            description = if (classes.isEmpty()) {
                                "Buat rombel kelas VII, VIII, atau IX terlebih dahulu sebelum menambahkan data siswa."
                            } else {
                                "Tambahkan NIS, nama lengkap, jenis kelamin, dan rombel siswa untuk membuat kartu QR otomatis."
                            },
                            actionLabel = if (role == UserRole.ADMIN) {
                                if (classes.isEmpty()) "Tambah Rombel" else "Tambah Siswa"
                            } else null,
                            onAction = if (role == UserRole.ADMIN) {
                                {
                                    if (classes.isEmpty()) showAddClassDialog = true
                                    else showAddStudentDialog = true
                                }
                            } else null
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredStudents, key = { it.id }) { student ->
                                StudentRowCard(
                                    student = student,
                                    canDelete = role == UserRole.ADMIN,
                                    onShowQr = { selectedStudentForQr = student },
                                    onDelete = { onDeleteStudent(student.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddClassDialog) {
        AddClassDialog(
            onDismiss = { showAddClassDialog = false },
            onSubmit = { name, grade, teacher, cap ->
                onAddClass(name, grade, teacher, cap)
                showAddClassDialog = false
            }
        )
    }

    if (showAddStudentDialog) {
        if (classes.isEmpty()) {
            AlertDialog(
                onDismissRequest = { showAddStudentDialog = false },
                title = { Text("Rombel Diperlukan") },
                text = { Text("Silakan buat minimal 1 rombel (contoh: VII-A) sebelum menambahkan siswa.") },
                confirmButton = {
                    Button(onClick = {
                        showAddStudentDialog = false
                        showAddClassDialog = true
                    }) {
                        Text("Buat Rombel")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddStudentDialog = false }) { Text("Batal") }
                }
            )
        } else {
            AddStudentDialog(
                classes = classes,
                onDismiss = { showAddStudentDialog = false },
                onSubmit = { nis, name, gender, cls, email ->
                    onAddStudent(nis, name, gender, cls, email)
                    showAddStudentDialog = false
                }
            )
        }
    }

    selectedStudentForQr?.let { student ->
        StudentQrCardDialog(
            student = student,
            onDismiss = { selectedStudentForQr = null }
        )
    }
}

@Composable
private fun StudentRowCard(
    student: Student,
    canDelete: Boolean,
    onShowQr: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onShowQr)
            .testTag("student_card_${student.nis}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Text(
                    text = student.fullName.firstOrNull()?.uppercase() ?: "S",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = student.fullName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "NIS ${student.nis} • ${if (student.gender == "P") "P" else "L"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (student.linkedEmail.isNotBlank()) {
                    Text(
                        text = student.linkedEmail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            StatusPill(
                text = student.className,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
            IconButton(onClick = onShowQr) {
                Icon(Icons.Rounded.QrCode2, contentDescription = "Lihat Kartu QR")
            }
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Hapus Siswa",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun AddClassDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, Long, String, Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var gradeLevel by remember { mutableLongStateOf(7L) }
    var teacher by remember { mutableStateOf("") }
    var capacity by remember { mutableStateOf("36") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Rombongan Belajar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tingkat Kelas", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7L to "VII", 8L to "VIII", 9L to "IX").forEach { (lvl, label) ->
                        FilterChip(
                            selected = gradeLevel == lvl,
                            onClick = { gradeLevel = lvl },
                            label = { Text("Kelas $label") }
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Rombel (cth: VII-A)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("Wali Kelas (opsional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = capacity,
                    onValueChange = { capacity = it.filter(Char::isDigit) },
                    label = { Text("Kapasitas Siswa") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(name, gradeLevel, teacher, capacity.toLongOrNull()?.coerceIn(1L, 100L) ?: 36L)
                },
                enabled = name.trim().length >= 2
            ) {
                Text("Simpan Rombel")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddStudentDialog(
    classes: List<SchoolClass>,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, SchoolClass, String) -> Unit
) {
    var nis by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("L") }
    var selectedClass by remember { mutableStateOf(classes.first()) }
    var linkedEmail by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Data Siswa") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = nis,
                    onValueChange = { nis = it },
                    label = { Text("NIS (Nomor Induk Siswa)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Nama Lengkap Siswa") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = gender == "L",
                        onClick = { gender = "L" },
                        label = { Text("Laki-laki") }
                    )
                    FilterChip(
                        selected = gender == "P",
                        onClick = { gender = "P" },
                        label = { Text("Perempuan") }
                    )
                }
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedClass.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Rombel") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        classes.forEach { cls ->
                            DropdownMenuItem(
                                text = { Text(cls.name) },
                                onClick = {
                                    selectedClass = cls
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = linkedEmail,
                    onValueChange = { linkedEmail = it },
                    label = { Text("Email Google Siswa (opsional)") },
                    placeholder = { Text("Agar siswa otomatis terhubung") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(nis, fullName, gender, selectedClass, linkedEmail) },
                enabled = nis.trim().length >= 3 && fullName.trim().length >= 2
            ) {
                Text("Simpan & Buat QR")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
fun StudentQrCardDialog(
    student: Student,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val qrBitmap = remember(student.qrToken) { QrGenerator.generateQrImageBitmap(student.qrToken, 512) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kartu Peserta QR") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.padding(4.dp)
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap,
                            contentDescription = "Kode QR ${student.fullName}",
                            modifier = Modifier
                                .size(200.dp)
                                .padding(12.dp)
                        )
                    }
                }
                Text(student.fullName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "NIS ${student.nis} • Kelas ${student.className}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = student.qrToken,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { PdfReportPrinter.printStudentQrCard(context, student) }) {
                Icon(Icons.Rounded.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Cetak PDF")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}
