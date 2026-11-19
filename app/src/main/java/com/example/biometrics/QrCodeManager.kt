package com.example.biometrics

import android.graphics.Bitmap
import android.graphics.Color
import com.example.domain.SchoolConstants
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.security.MessageDigest

data class ParsedStudentQr(
    val emisCode: String,
    val studentUid: String,
    val className: String,
    val rollNumber: String
)

object QrCodeManager {
    private const val PREFIX = "GPS${SchoolConstants.EMIS_CODE}|STU"

    fun buildStudentQrPayload(
        studentUid: String,
        className: String,
        rollNumber: String
    ): String {
        val core = "$PREFIX|$studentUid|${className.trim()}|${rollNumber.trim()}"
        val checksum = shortChecksum(core)
        return "$core|$checksum"
    }

    fun parseStudentQrPayload(raw: String): ParsedStudentQr? {
        val parts = raw.trim().split("|")
        if (parts.size != 6) return null
        if (parts[0] != "GPS${SchoolConstants.EMIS_CODE}" || parts[1] != "STU") return null
        val core = parts.subList(0, 5).joinToString("|")
        if (shortChecksum(core) != parts[5]) return null
        return ParsedStudentQr(
            emisCode = SchoolConstants.EMIS_CODE,
            studentUid = parts[2],
            className = parts[3],
            rollNumber = parts[4]
        )
    }

    fun generateQrBitmap(payload: String, sizePx: Int = 512): Bitmap {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    private fun shortChecksum(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.take(4).joinToString("") { "%02x".format(it) }
    }
}
