package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.QuestionType
import com.example.data.model.SubmissionStatus
import com.example.ui.components.SectionCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessContainer
import com.example.ui.viewmodel.ActiveExamSession

@Composable
fun ExamRunnerScreen(
    session: ActiveExamSession,
    onSelectAnswer: (String, String) -> Unit,
    onSubmitExam: () -> Unit,
    onExit: () -> Unit
) {
    BackHandler { onExit() }

    val questions = session.questions
    var currentIndex by remember { mutableIntStateOf(0) }
    var showSubmitConfirm by remember { mutableStateOf(false) }

    val mins = session.remainingSeconds / 60L
    val secs = session.remainingSeconds % 60L
    val timerText = String.format("%02d:%02d", mins, secs)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Keluar Ujian")
                    }
                    Column {
                        Text(session.exam.title, style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.CloudDone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = session.lastSavedLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Schedule,
                            contentDescription = "Sisa waktu",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = timerText,
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (session.submission.status != SubmissionStatus.IN_PROGRESS.wireValue) {
                val status = SubmissionStatus.fromWire(session.submission.status)
                SectionCard {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = "Selesai",
                            tint = StatusSuccess,
                            modifier = Modifier.size(56.dp)
                        )
                        Text("Ujian Telah Dikumpulkan", style = MaterialTheme.typography.headlineSmall)
                        StatusPill(status.label, StatusSuccessContainer, StatusSuccess)
                        Text(
                            text = "Skor Pilihan Ganda: ${session.submission.autoScore}  •  Skor Total: ${session.submission.totalScore}/${session.submission.maxScore}",
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily)
                        )
                        Text(
                            text = "Jika terdapat soal esai, nilai akhir akan diperbarui setelah dikoreksi oleh petugas/guru.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                            Text("Kembali ke Portal")
                        }
                    }
                }
                return@Column
            }

            // Question navigator pills
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                questions.forEachIndexed { idx, q ->
                    val answered = !session.draftAnswers[q.id].isNullOrBlank()
                    val selected = idx == currentIndex
                    FilterChip(
                        selected = selected,
                        onClick = { currentIndex = idx },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = if (answered) StatusSuccessContainer else MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = if (answered) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        label = { Text("${idx + 1}") }
                    )
                }
            }

            val currentQuestion = questions.getOrNull(currentIndex)
            if (currentQuestion != null) {
                val qType = QuestionType.fromWire(currentQuestion.questionType)
                val currentAnswer = session.draftAnswers[currentQuestion.id].orEmpty()

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SectionCard {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StatusPill(
                                text = "Soal ${currentIndex + 1} dari ${questions.size} • ${qType.label}",
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "${currentQuestion.points} poin",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = currentQuestion.prompt,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (qType == QuestionType.PG) {
                        val options = listOf(
                            "A" to currentQuestion.optionA,
                            "B" to currentQuestion.optionB,
                            "C" to currentQuestion.optionC,
                            "D" to currentQuestion.optionD
                        ).filter { it.second.isNotBlank() }

                        options.forEach { (key, text) ->
                            val isChosen = currentAnswer.equals(key, ignoreCase = true)
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isChosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(
                                    width = if (isChosen) 2.dp else 1.dp,
                                    color = if (isChosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .clickable { onSelectAnswer(currentQuestion.id, key) }
                                    .testTag("option_${currentIndex}_$key")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    RadioButton(
                                        selected = isChosen,
                                        onClick = { onSelectAnswer(currentQuestion.id, key) }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "$key. $text",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isChosen) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = currentAnswer,
                            onValueChange = { onSelectAnswer(currentQuestion.id, it) },
                            label = { Text("Tulis Jawaban Esai Anda") },
                            minLines = 5,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("essay_input_$currentIndex")
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { if (currentIndex > 0) currentIndex-- },
                    enabled = currentIndex > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Sebelumnya")
                }
                if (currentIndex < questions.lastIndex) {
                    Button(
                        onClick = { currentIndex++ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Berikutnya")
                    }
                } else {
                    Button(
                        onClick = { showSubmitConfirm = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("submit_exam_button")
                    ) {
                        Text("Kumpulkan")
                    }
                }
            }
        }
    }

    if (showSubmitConfirm) {
        val answeredCount = questions.count { !session.draftAnswers[it.id].isNullOrBlank() }
        AlertDialog(
            onDismissRequest = { showSubmitConfirm = false },
            title = { Text("Kumpulkan Jawaban Ujian?") },
            text = {
                Text("Anda telah menjawab $answeredCount dari ${questions.size} soal. Setelah dikumpulkan, jawaban tidak dapat diubah lagi.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSubmitConfirm = false
                        onSubmitExam()
                    },
                    modifier = Modifier.testTag("confirm_submit_exam")
                ) { Text("Ya, Kumpulkan") }
            },
            dismissButton = {
                TextButton(onClick = { showSubmitConfirm = false }) { Text("Periksa Lagi") }
            }
        )
    }
}
