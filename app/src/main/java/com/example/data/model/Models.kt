package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class UserRole(val wireValue: String, val label: String) {
    ADMIN("admin", "Admin"),
    PROCTOR("proctor", "Petugas"),
    STUDENT("student", "Siswa");

    companion object {
        fun fromWire(value: String?): UserRole =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: STUDENT
    }
}

enum class QuestionType(val wireValue: String, val label: String) {
    PG("PG", "Pilihan Ganda"),
    ESSAY("ESSAY", "Esai");

    companion object {
        fun fromWire(value: String?): QuestionType =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: PG
    }
}

enum class SubmissionStatus(val wireValue: String, val label: String) {
    IN_PROGRESS("in_progress", "Sedang Dikerjakan"),
    SUBMITTED("submitted", "Menunggu Nilai Esai"),
    GRADED("graded", "Selesai Dinilai");

    companion object {
        fun fromWire(value: String?): SubmissionStatus =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: IN_PROGRESS
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val email: String = "",
    val role: String = UserRole.STUDENT.wireValue,
    val studentId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class RoleAssignment(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val fullName: String = "",
    val grantedByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SchoolClass(
    @DocumentId val id: String = "",
    val name: String = "",
    val gradeLevel: Long = 7L,
    val homeroomTeacher: String = "",
    val capacity: Long = 36L,
    val published: Boolean = true,
    val ownerId: String = "",
    val staffUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Subject(
    @DocumentId val id: String = "",
    val code: String = "",
    val name: String = "",
    val published: Boolean = true,
    val ownerId: String = "",
    val staffUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Student(
    @DocumentId val id: String = "",
    val nis: String = "",
    val fullName: String = "",
    val gender: String = "L",
    val classId: String = "",
    val className: String = "",
    val qrToken: String = "",
    val linkedEmail: String = "",
    val authUserId: String = "",
    val ownerId: String = "",
    val staffUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ExamSchedule(
    @DocumentId val id: String = "",
    val title: String = "",
    val subjectId: String = "",
    val subjectName: String = "",
    val classIds: List<String> = emptyList(),
    val classNames: List<String> = emptyList(),
    val startAt: Timestamp? = null,
    val endAt: Timestamp? = null,
    val durationMinutes: Long = 60L,
    val tokenCode: String = "",
    val published: Boolean = true,
    val ownerId: String = "",
    val staffUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ExamQuestion(
    @DocumentId val id: String = "",
    val examId: String = "",
    val orderIndex: Long = 0L,
    val questionType: String = QuestionType.PG.wireValue,
    val prompt: String = "",
    val optionA: String = "",
    val optionB: String = "",
    val optionC: String = "",
    val optionD: String = "",
    val correctOption: String = "A",
    val points: Long = 10L,
    val published: Boolean = true,
    val ownerId: String = "",
    val staffUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AttendanceRecord(
    @DocumentId val id: String = "",
    val examId: String = "",
    val examTitle: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val nis: String = "",
    val className: String = "",
    val qrToken: String = "",
    val studentAuthUid: String = "",
    val scannedByUid: String = "",
    val scannedByName: String = "",
    val status: String = "present",
    val ownerId: String = "",
    val staffUids: List<String> = emptyList(),
    @ServerTimestamp val scannedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ExamSubmission(
    @DocumentId val id: String = "",
    val examId: String = "",
    val examTitle: String = "",
    val subjectName: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val nis: String = "",
    val className: String = "",
    val studentAuthUid: String = "",
    val answers: Map<String, String> = emptyMap(),
    val essayScores: Map<String, Long> = emptyMap(),
    val autoScore: Long = 0L,
    val essayScore: Long = 0L,
    val totalScore: Long = 0L,
    val maxScore: Long = 0L,
    val status: String = SubmissionStatus.IN_PROGRESS.wireValue,
    val ownerId: String = "",
    val staffUids: List<String> = emptyList(),
    @ServerTimestamp val startedAt: Timestamp? = null,
    val submittedAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
