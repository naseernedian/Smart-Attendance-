package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.biometrics.FaceRecognitionEngine
import com.example.biometrics.QrCodeManager
import com.example.domain.UsernameValidationResult
import com.example.domain.Validators
import com.example.security.CryptoManager
import com.example.security.SoftwareTestKeyProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.sin
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ValidatorsAndBiometricsTest {

    private fun unitVector(seed: Double, size: Int = 256): FloatArray {
        val raw = FloatArray(size) { i -> sin(seed * (i + 1)).toFloat() }
        var sum = 0f
        raw.forEach { sum += it * it }
        val norm = sqrt(sum)
        return FloatArray(size) { raw[it] / norm }
    }

    @Test
    fun `cnic and mobile username validation accepts raw 03XXXXXXXXX and 13-digit formats only`() {
        val rawCnic = Validators.parseAdminUsername("2120312345671")
        assertTrue(rawCnic is UsernameValidationResult.ValidCnic)
        assertEquals("2120312345671", (rawCnic as UsernameValidationResult.ValidCnic).cnic)

        val rawMobile = Validators.parseAdminUsername("03001234567")
        assertTrue(rawMobile is UsernameValidationResult.ValidMobile)
        assertEquals("03001234567", (rawMobile as UsernameValidationResult.ValidMobile).mobile)

        assertTrue(Validators.parseAdminUsername("21203-1234567-1") is UsernameValidationResult.Invalid)
        assertTrue(Validators.parseAdminUsername("0300-1234567") is UsernameValidationResult.Invalid)
        assertTrue(Validators.parseAdminUsername("+923001234567") is UsernameValidationResult.Invalid)
        assertTrue(Validators.parseAdminUsername("admin123") is UsernameValidationResult.Invalid)
    }

    @Test
    fun `qr payload generation and tamper rejection works offline`() {
        val payload = QrCodeManager.buildStudentQrPayload("stu-uuid-1", "Class 3", "14")
        val parsed = QrCodeManager.parseStudentQrPayload(payload)
        assertNotNull(parsed)
        assertEquals("stu-uuid-1", parsed?.studentUid)
        assertEquals("Class 3", parsed?.className)
        assertEquals("14", parsed?.rollNumber)

        val tampered = payload.dropLast(2) + "ff"
        assertNull(QrCodeManager.parseStudentQrPayload(tampered))

        val bmp = QrCodeManager.generateQrBitmap(payload, 128)
        assertEquals(128, bmp.width)
        assertEquals(128, bmp.height)
    }

    @Test
    fun `crypto manager encrypts embeddings and verifies pbkdf2 passwords`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = CryptoManager(context, SoftwareTestKeyProvider())

        val original = unitVector(1.5)
        val encrypted = crypto.encryptEmbedding(original)
        val decrypted = crypto.decryptEmbedding(encrypted)
        assertArrayEquals(original, decrypted, 1e-6f)

        val (salt, hash) = crypto.hashPassword("SecurePass99")
        assertTrue(crypto.verifyPassword("SecurePass99", salt, hash))
        assertFalse(crypto.verifyPassword("WrongPass99", salt, hash))
    }

    @Test
    fun `face recognition matching verifies matching face and rejects different face`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = FaceRecognitionEngine(context)

        val ref = unitVector(2.1)
        val samePerson = unitVector(2.1)
        val otherPerson = unitVector(8.7)

        val passResult = engine.compareEmbeddings(ref, samePerson)
        assertTrue(passResult.isMatch)
        assertTrue(passResult.message.contains("VERIFIED"))

        val failResult = engine.compareEmbeddings(ref, otherPerson)
        assertFalse(failResult.isMatch)
        assertTrue(failResult.message.contains("NOT VERIFIED"))
    }
}
