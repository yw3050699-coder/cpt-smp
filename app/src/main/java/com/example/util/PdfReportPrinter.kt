package com.example.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import com.example.data.model.AttendanceRecord
import com.example.data.model.ExamSchedule
import com.example.data.model.ExamSubmission
import com.example.data.model.Student
import com.example.data.model.SubmissionStatus
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfReportPrinter {

    private val dateFormatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))

    fun printExamReport(
        context: Context,
        exam: ExamSchedule,
        submissions: List<ExamSubmission>,
        attendance: List<AttendanceRecord>
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val jobName = "Laporan_${exam.title.replace(" ", "_")}"
        printManager.print(
            jobName,
            ExamReportPrintAdapter(context, exam, submissions, attendance),
            PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build()
        )
    }

    fun printStudentQrCard(context: Context, student: Student) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val jobName = "Kartu_QR_${student.nis}"
        printManager.print(
            jobName,
            StudentCardPrintAdapter(context, student),
            PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build()
        )
    }

    private class ExamReportPrintAdapter(
        private val context: Context,
        private val exam: ExamSchedule,
        private val submissions: List<ExamSubmission>,
        private val attendance: List<AttendanceRecord>
    ) : PrintDocumentAdapter() {
        private var pdfDocument: PrintedPdfDocument? = null

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?
        ) {
            pdfDocument = PrintedPdfDocument(context, newAttributes)
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder("Laporan_${exam.title}.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(1)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback
        ) {
            val doc = pdfDocument ?: return
            val page = doc.startPage(0)
            drawReportPage(page.canvas)
            doc.finishPage(page)
            try {
                FileOutputStream(destination.fileDescriptor).use { out ->
                    doc.writeTo(out)
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            } finally {
                doc.close()
                pdfDocument = null
            }
        }

        private fun drawReportPage(canvas: Canvas) {
            val titlePaint = Paint().apply {
                color = Color.parseColor("#0F2A4A")
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subPaint = Paint().apply {
                color = Color.DKGRAY
                textSize = 11f
            }
            val headerBg = Paint().apply { color = Color.parseColor("#0F2A4A") }
            val headerText = Paint().apply {
                color = Color.WHITE
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val rowPaint = Paint().apply {
                color = Color.BLACK
                textSize = 10f
            }
            val linePaint = Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 1f
            }

            var y = 48f
            canvas.drawText("SMP NEGERI 01 BANDING AGUNG", 40f, y, titlePaint)
            y += 18f
            canvas.drawText("LAPORAN HASIL UJIAN & REKAP ABSENSI QR", 40f, y, subPaint)
            y += 16f
            canvas.drawText("Ujian: ${exam.title}  |  Mapel: ${exam.subjectName}", 40f, y, rowPaint)
            y += 14f
            canvas.drawText(
                "Hadir QR: ${attendance.size} peserta  |  Mengumpulkan: ${submissions.size} peserta  |  Dicetak: ${dateFormatter.format(Date())}",
                40f,
                y,
                subPaint
            )
            y += 22f

            canvas.drawRect(40f, y, 555f, y + 22f, headerBg)
            canvas.drawText("No", 46f, y + 15f, headerText)
            canvas.drawText("NIS", 75f, y + 15f, headerText)
            canvas.drawText("Nama Peserta", 150f, y + 15f, headerText)
            canvas.drawText("Kelas", 335f, y + 15f, headerText)
            canvas.drawText("Status", 395f, y + 15f, headerText)
            canvas.drawText("Nilai", 505f, y + 15f, headerText)
            y += 32f

            submissions.sortedBy { it.nis }.take(32).forEachIndexed { idx, sub ->
                canvas.drawText("${idx + 1}", 46f, y, rowPaint)
                canvas.drawText(sub.nis.take(12), 75f, y, rowPaint)
                canvas.drawText(sub.studentName.take(28), 150f, y, rowPaint)
                canvas.drawText(sub.className.take(8), 335f, y, rowPaint)
                canvas.drawText(SubmissionStatus.fromWire(sub.status).label.take(16), 395f, y, rowPaint)
                canvas.drawText("${sub.totalScore}/${sub.maxScore}", 505f, y, rowPaint)
                y += 6f
                canvas.drawLine(40f, y, 555f, y, linePaint)
                y += 15f
            }
        }
    }

    private class StudentCardPrintAdapter(
        private val context: Context,
        private val student: Student
    ) : PrintDocumentAdapter() {
        private var pdfDocument: PrintedPdfDocument? = null

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?
        ) {
            pdfDocument = PrintedPdfDocument(context, newAttributes)
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder("Kartu_QR_${student.nis}.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(1)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback
        ) {
            val doc = pdfDocument ?: return
            val page = doc.startPage(0)
            drawCard(page.canvas)
            doc.finishPage(page)
            try {
                FileOutputStream(destination.fileDescriptor).use { out ->
                    doc.writeTo(out)
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            } finally {
                doc.close()
                pdfDocument = null
            }
        }

        private fun drawCard(canvas: Canvas) {
            val cardPaint = Paint().apply {
                color = Color.parseColor("#0F2A4A")
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            val headerPaint = Paint().apply { color = Color.parseColor("#0F2A4A") }
            val whiteBold = Paint().apply {
                color = Color.WHITE
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val labelPaint = Paint().apply {
                color = Color.DKGRAY
                textSize = 11f
            }
            val valuePaint = Paint().apply {
                color = Color.BLACK
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            canvas.drawRect(50f, 50f, 520f, 320f, cardPaint)
            canvas.drawRect(50f, 50f, 520f, 102f, headerPaint)
            canvas.drawText("KARTU PESERTA E-UJIAN", 70f, 76f, whiteBold)
            canvas.drawText("SMP NEGERI 01 BANDING AGUNG", 70f, 94f, Paint(whiteBold).apply { textSize = 10f })

            canvas.drawText("Nama Peserta", 70f, 134f, labelPaint)
            canvas.drawText(student.fullName, 70f, 152f, valuePaint)

            canvas.drawText("Nomor Induk Siswa (NIS)", 70f, 184f, labelPaint)
            canvas.drawText(student.nis, 70f, 202f, valuePaint)

            canvas.drawText("Rombel / Jenis Kelamin", 70f, 234f, labelPaint)
            val genderLabel = if (student.gender == "P") "Perempuan" else "Laki-laki"
            canvas.drawText("${student.className}  •  $genderLabel", 70f, 252f, valuePaint)

            canvas.drawText("Token QR: ${student.qrToken}", 70f, 294f, labelPaint)

            val qrBmp = QrGenerator.generateQrBitmap(student.qrToken, 240)
            if (qrBmp != null) {
                canvas.drawBitmap(qrBmp, null, Rect(360, 130, 495, 265), null)
            }
        }
    }
}
