package com.example.reports

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.local.AttendanceRecordEntity
import com.example.domain.AttendanceStatus
import com.example.domain.ExportFormat
import com.example.domain.ReportPeriod
import com.example.domain.SchoolConstants
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class GeneratedReportFile(
    val file: File,
    val format: ExportFormat,
    val recordCount: Int
)

class ReportGenerator(private val context: Context) {

    private fun reportsDir(): File = File(context.filesDir, "reports").apply { mkdirs() }

    fun generateReport(
        records: List<AttendanceRecordEntity>,
        period: ReportPeriod,
        periodRangeLabel: String,
        classFilter: String?,
        format: ExportFormat
    ): GeneratedReportFile {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safePeriod = period.name.lowercase(Locale.US)
        val fileName = "GPS61673_${safePeriod}_attendance_$stamp.${format.extension}"
        val outFile = File(reportsDir(), fileName)

        when (format) {
            ExportFormat.CSV -> writeCsv(outFile, records, period, periodRangeLabel, classFilter)
            ExportFormat.PDF -> writePdf(outFile, records, period, periodRangeLabel, classFilter)
            ExportFormat.XLSX -> writeXlsx(outFile, records, period, periodRangeLabel, classFilter)
        }
        return GeneratedReportFile(file = outFile, format = format, recordCount = records.size)
    }

    private fun writeCsv(
        file: File,
        records: List<AttendanceRecordEntity>,
        period: ReportPeriod,
        periodRangeLabel: String,
        classFilter: String?
    ) {
        val sb = StringBuilder()
        sb.append('\uFEFF') // UTF-8 BOM for Microsoft Excel
        sb.appendLine(csvRow(listOf(SchoolConstants.SCHOOL_NAME, "EMIS Code: ${SchoolConstants.EMIS_CODE}", "${period.label} Report", periodRangeLabel, classFilter ?: "All Classes")))
        sb.appendLine(csvRow(listOf("Student Name", "Father Name", "Class", "Roll Number", "Status", "Date", "Time", "Face Match %", "Sync Status")))
        records.forEach { rec ->
            sb.appendLine(
                csvRow(
                    listOf(
                        rec.studentNameSnapshot,
                        rec.fatherNameSnapshot,
                        rec.classNameSnapshot,
                        rec.rollNumberSnapshot,
                        rec.status,
                        rec.attendanceDate,
                        extractTimeLabel(rec.markedAtIso, rec.markedAtEpochMs),
                        "${(rec.verifiedSimilarity * 100).toInt()}%",
                        rec.syncStatus
                    )
                )
            )
        }
        file.writeText(sb.toString(), Charsets.UTF_8)
    }

    private fun csvRow(cells: List<String>): String =
        cells.joinToString(",") { cell ->
            val escaped = cell.replace("\"", "\"\"")
            "\"$escaped\""
        }

    private fun writePdf(
        file: File,
        records: List<AttendanceRecordEntity>,
        period: ReportPeriod,
        periodRangeLabel: String,
        classFilter: String?
    ) {
        val nativeWorked = runCatching {
            writeAndroidPdfDocument(file, records, period, periodRangeLabel, classFilter)
        }.isSuccess
        if (!nativeWorked) {
            writePureKotlinPdf(file, records, period, periodRangeLabel, classFilter)
        }
    }

    private fun writeAndroidPdfDocument(
        file: File,
        records: List<AttendanceRecordEntity>,
        period: ReportPeriod,
        periodRangeLabel: String,
        classFilter: String?
    ) {
        val pdf = PdfDocument()
        val pageWidth = 595 // A4 portrait @ 72 dpi
        val pageHeight = 842
        val margin = 32f

        val headerPaint = Paint().apply {
            color = Color.rgb(15, 81, 50)
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint().apply {
            color = Color.rgb(40, 55, 48)
            textSize = 10f
        }
        val thPaint = Paint().apply {
            color = Color.WHITE
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val cellPaint = Paint().apply {
            color = Color.BLACK
            textSize = 8.5f
        }
        val headerBgPaint = Paint().apply { color = Color.rgb(15, 81, 50) }
        val altRowPaint = Paint().apply { color = Color.rgb(242, 246, 244) }
        val linePaint = Paint().apply {
            color = Color.rgb(200, 212, 206)
            strokeWidth = 0.75f
        }

        val presentCount = records.count { it.status == AttendanceStatus.PRESENT.name }
        val absentCount = records.count { it.status == AttendanceStatus.ABSENT.name }
        val leaveCount = records.count { it.status == AttendanceStatus.LEAVE.name }

        val colX = floatArrayOf(32f, 130f, 224f, 290f, 342f, 405f, 472f)
        val headers = listOf("Student Name", "Father Name", "Class", "Roll", "Status", "Date", "Time")

        var pageNum = 1
        var index = 0
        try {
            do {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                val page = pdf.startPage(pageInfo)
                val canvas = page.canvas

                var y = 40f
                canvas.drawText("${SchoolConstants.APP_TITLE} — ${SchoolConstants.SCHOOL_NAME}", margin, y, headerPaint)
                y += 16f
                canvas.drawText("EMIS Code: ${SchoolConstants.EMIS_CODE}  |  ${period.label} Attendance Report ($periodRangeLabel)  |  Class: ${classFilter ?: "All"}", margin, y, subPaint)
                y += 14f
                canvas.drawText("Summary: Total=${records.size}   PRESENT=$presentCount   ABSENT=$absentCount   LEAVE=$leaveCount   Page $pageNum", margin, y, subPaint)
                y += 14f

                canvas.drawRect(margin, y, pageWidth - margin, y + 20f, headerBgPaint)
                headers.forEachIndexed { col, title ->
                    canvas.drawText(title, colX[col] + 3f, y + 13.5f, thPaint)
                }
                y += 22f

                if (records.isEmpty()) {
                    canvas.drawText("No attendance records stored for the selected period.", margin + 4f, y + 16f, cellPaint)
                } else {
                    var rowOnPage = 0
                    while (index < records.size && y < pageHeight - 48f) {
                        val rec = records[index]
                        if (rowOnPage % 2 == 1) {
                            canvas.drawRect(margin, y, pageWidth - margin, y + 18f, altRowPaint)
                        }
                        val rowValues = listOf(
                            rec.studentNameSnapshot.take(18),
                            rec.fatherNameSnapshot.take(17),
                            rec.classNameSnapshot.take(12),
                            rec.rollNumberSnapshot.take(8),
                            rec.status,
                            rec.attendanceDate,
                            extractTimeLabel(rec.markedAtIso, rec.markedAtEpochMs)
                        )
                        rowValues.forEachIndexed { col, value ->
                            canvas.drawText(value, colX[col] + 3f, y + 12.5f, cellPaint)
                        }
                        canvas.drawLine(margin, y + 18f, pageWidth - margin, y + 18f, linePaint)
                        y += 18f
                        rowOnPage++
                        index++
                    }
                }

                pdf.finishPage(page)
                pageNum++
            } while (index < records.size)

            FileOutputStream(file).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
    }

    private fun writePureKotlinPdf(
        file: File,
        records: List<AttendanceRecordEntity>,
        period: ReportPeriod,
        periodRangeLabel: String,
        classFilter: String?
    ) {
        val presentCount = records.count { it.status == AttendanceStatus.PRESENT.name }
        val absentCount = records.count { it.status == AttendanceStatus.ABSENT.name }
        val leaveCount = records.count { it.status == AttendanceStatus.LEAVE.name }

        val lines = mutableListOf<String>()
        lines.add("${SchoolConstants.APP_TITLE} - ${SchoolConstants.SCHOOL_NAME} (EMIS ${SchoolConstants.EMIS_CODE})")
        lines.add("${period.label} Report ($periodRangeLabel) | Class: ${classFilter ?: "All"}")
        lines.add("Total: ${records.size} | PRESENT: $presentCount | ABSENT: $absentCount | LEAVE: $leaveCount")
        lines.add("--------------------------------------------------------------------------")
        lines.add("Student Name       | Father Name        | Class    | Roll | Status  | Date       | Time")
        if (records.isEmpty()) {
            lines.add("No attendance records stored for the selected period.")
        } else {
            records.forEach { rec ->
                val time = extractTimeLabel(rec.markedAtIso, rec.markedAtEpochMs)
                lines.add(
                    "${rec.studentNameSnapshot.take(18).padEnd(18)} | " +
                        "${rec.fatherNameSnapshot.take(18).padEnd(18)} | " +
                        "${rec.classNameSnapshot.take(8).padEnd(8)} | " +
                        "${rec.rollNumberSnapshot.take(4).padEnd(4)} | " +
                        "${rec.status.padEnd(7)} | ${rec.attendanceDate} | $time"
                )
            }
        }

        val streamContent = buildString {
            append("BT\n/F1 9 Tf\n")
            var y = 800
            lines.take(58).forEach { line ->
                val escaped = line.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
                append("1 0 0 1 32 $y Tm ($escaped) Tj\n")
                y -= 13
            }
            append("ET")
        }

        val objects = listOf(
            "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n",
            "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n",
            "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj\n",
            "4 0 obj << /Length ${streamContent.toByteArray(Charsets.US_ASCII).size} >> stream\n$streamContent\nendstream endobj\n",
            "5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Courier >> endobj\n"
        )

        val sb = StringBuilder("%PDF-1.4\n")
        val offsets = mutableListOf<Int>()
        objects.forEach { obj ->
            offsets.add(sb.toString().toByteArray(Charsets.US_ASCII).size)
            sb.append(obj)
        }
        val xrefPos = sb.toString().toByteArray(Charsets.US_ASCII).size
        sb.append("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { off ->
            sb.append("%010d 00000 n \n".format(off))
        }
        sb.append("trailer << /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xrefPos\n%%EOF\n")
        file.writeText(sb.toString(), Charsets.US_ASCII)
    }

    /**
     * Generates a genuine OOXML SpreadsheetML (.xlsx) workbook with zero external dependencies,
     * compatible with Microsoft Excel, LibreOffice Calc, and Google Sheets.
     */
    private fun writeXlsx(
        file: File,
        records: List<AttendanceRecordEntity>,
        period: ReportPeriod,
        periodRangeLabel: String,
        classFilter: String?
    ) {
        val rows = mutableListOf<List<String>>()
        rows.add(listOf(SchoolConstants.SCHOOL_NAME, "EMIS Code: ${SchoolConstants.EMIS_CODE}", "${period.label} Report", periodRangeLabel, classFilter ?: "All Classes"))
        rows.add(listOf("Student Name", "Father Name", "Class", "Roll Number", "Status", "Date", "Time", "Face Match %", "Sync Status"))
        records.forEach { rec ->
            rows.add(
                listOf(
                    rec.studentNameSnapshot,
                    rec.fatherNameSnapshot,
                    rec.classNameSnapshot,
                    rec.rollNumberSnapshot,
                    rec.status,
                    rec.attendanceDate,
                    extractTimeLabel(rec.markedAtIso, rec.markedAtEpochMs),
                    "${(rec.verifiedSimilarity * 100).toInt()}%",
                    rec.syncStatus
                )
            )
        }

        val sheetXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            append("<cols>")
            listOf(22, 22, 16, 12, 12, 14, 12, 14, 12).forEachIndexed { idx, width ->
                append("""<col min="${idx + 1}" max="${idx + 1}" width="$width" customWidth="1"/>""")
            }
            append("</cols>")
            append("<sheetData>")
            rows.forEachIndexed { rowIdx, cells ->
                val rowNum = rowIdx + 1
                append("""<row r="$rowNum">""")
                cells.forEachIndexed { colIdx, value ->
                    val colLetter = ('A' + colIdx)
                    val styleAttr = if (rowIdx == 1) """ s="1"""" else ""
                    append("""<c r="$colLetter$rowNum" t="inlineStr"$styleAttr><is><t>${xmlEscape(value)}</t></is></c>""")
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }

        val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>""".trimIndent()

        val rootRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""".trimIndent()

        val workbookXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Attendance" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>""".trimIndent()

        val workbookRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""".trimIndent()

        val stylesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF0F5132"/></patternFill></fill>
  </fills>
  <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="2">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
  </cellXfs>
</styleSheet>""".trimIndent()

        ZipOutputStream(FileOutputStream(file)).use { zip ->
            writeZipEntry(zip, "[Content_Types].xml", contentTypesXml)
            writeZipEntry(zip, "_rels/.rels", rootRelsXml)
            writeZipEntry(zip, "xl/workbook.xml", workbookXml)
            writeZipEntry(zip, "xl/_rels/workbook.xml.rels", workbookRelsXml)
            writeZipEntry(zip, "xl/styles.xml", stylesXml)
            writeZipEntry(zip, "xl/worksheets/sheet1.xml", sheetXml)
        }
    }

    private fun writeZipEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun xmlEscape(value: String): String =
        value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private fun extractTimeLabel(iso: String, epochMs: Long): String {
        val tIndex = iso.indexOf('T')
        if (tIndex != -1 && iso.length >= tIndex + 9) {
            return iso.substring(tIndex + 1, tIndex + 9)
        }
        return SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(epochMs))
    }
}
