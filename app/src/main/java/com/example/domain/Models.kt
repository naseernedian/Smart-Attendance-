package com.example.domain

enum class AttendanceStatus(val label: String) {
    PRESENT("PRESENT"),
    ABSENT("ABSENT"),
    LEAVE("LEAVE");

    companion object {
        fun fromString(value: String): AttendanceStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PRESENT
    }
}

enum class SyncStatus {
    PENDING,
    SYNCED
}

enum class AdminAuthorizationStatus(val displayLabel: String) {
    PENDING_AUTHORIZATION("Pending Authorization"),
    APPROVED("Authorized"),
    REJECTED("Rejected");

    companion object {
        fun fromString(value: String): AdminAuthorizationStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PENDING_AUTHORIZATION
    }
}

enum class ReportPeriod(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly")
}

enum class ExportFormat(val label: String, val extension: String, val mimeType: String) {
    PDF("PDF", "pdf", "application/pdf"),
    XLSX("Excel (.xlsx)", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    CSV("CSV", "csv", "text/csv")
}

data class FaceMatchResult(
    val isMatch: Boolean,
    val similarityScore: Float,
    val thresholdUsed: Float,
    val engineName: String,
    val message: String
)

data class FaceCaptureOutput(
    val embedding: FloatArray,
    val thumbnailBytes: ByteArray,
    val engineName: String,
    val qualityNote: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FaceCaptureOutput) return false
        return embedding.contentEquals(other.embedding) &&
            thumbnailBytes.contentEquals(other.thumbnailBytes) &&
            engineName == other.engineName
    }

    override fun hashCode(): Int {
        var result = embedding.contentHashCode()
        result = 31 * result + thumbnailBytes.contentHashCode()
        result = 31 * result + engineName.hashCode()
        return result
    }
}
