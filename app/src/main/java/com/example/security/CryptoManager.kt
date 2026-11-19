package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

interface KeyProvider {
    fun getOrCreateAesKey(): SecretKey
}

class AndroidKeyStoreProvider(
    private val alias: String = "smart_attendance_master_aes_v1"
) : KeyProvider {
    override fun getOrCreateAesKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.getKey(alias, null)?.let { return it as SecretKey }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}

class SoftwareTestKeyProvider(rawKey: ByteArray = ByteArray(32) { (it * 13 + 7).toByte() }) : KeyProvider {
    private val key = SecretKeySpec(rawKey.copyOf(32), "AES")
    override fun getOrCreateAesKey(): SecretKey = key
}

class CryptoManager(
    private val context: Context,
    private val keyProvider: KeyProvider = defaultKeyProvider()
) {
    companion object {
        private const val GCM_TAG_BITS = 128
        private const val IV_LENGTH_BYTES = 12
        private const val PBKDF2_ITERATIONS = 120_000
        private const val PBKDF2_KEY_BITS = 256

        private fun defaultKeyProvider(): KeyProvider {
            return try {
                val ks = KeyStore.getInstance("AndroidKeyStore")
                ks.load(null)
                AndroidKeyStoreProvider()
            } catch (_: Throwable) {
                SoftwareTestKeyProvider()
            }
        }
    }

    fun encryptBytes(plainBytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider.getOrCreateAesKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainBytes)
        return ByteBuffer.allocate(1 + iv.size + cipherText.size)
            .put(iv.size.toByte())
            .put(iv)
            .put(cipherText)
            .array()
    }

    fun decryptBytes(encryptedBlob: ByteArray): ByteArray {
        require(encryptedBlob.size > IV_LENGTH_BYTES + 1) { "Corrupted encrypted payload" }
        val buffer = ByteBuffer.wrap(encryptedBlob)
        val ivLen = buffer.get().toInt() and 0xFF
        val iv = ByteArray(ivLen)
        buffer.get(iv)
        val cipherText = ByteArray(buffer.remaining())
        buffer.get(cipherText)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keyProvider.getOrCreateAesKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(cipherText)
    }

    fun encryptString(plainText: String): String {
        val encrypted = encryptBytes(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    fun decryptString(encryptedBase64: String): String {
        val raw = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        return decryptBytes(raw).toString(Charsets.UTF_8)
    }

    fun encryptEmbedding(embedding: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(embedding.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        embedding.forEach { buffer.putFloat(it) }
        return encryptBytes(buffer.array())
    }

    fun decryptEmbedding(encryptedBlob: ByteArray): FloatArray {
        val plain = decryptBytes(encryptedBlob)
        val count = plain.size / 4
        val buffer = ByteBuffer.wrap(plain).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(count) { buffer.float }
    }

    fun saveEncryptedDocument(fileName: String, plainBytes: ByteArray): String {
        val dir = File(context.filesDir, "secure_docs").apply { mkdirs() }
        val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(dir, "$safeName.enc")
        file.writeBytes(encryptBytes(plainBytes))
        return file.absolutePath
    }

    fun readEncryptedDocument(absolutePath: String): ByteArray? {
        val file = File(absolutePath)
        if (!file.exists()) return null
        return decryptBytes(file.readBytes())
    }

    fun hashPassword(password: String): Pair<String, String> {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(password, salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP) to Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    fun verifyPassword(password: String, saltB64: String, expectedHashB64: String): Boolean {
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val expectedHash = Base64.decode(expectedHashB64, Base64.NO_WRAP)
        val actualHash = pbkdf2(password, salt)
        return MessageDigest.isEqual(actualHash, expectedHash)
    }

    private fun pbkdf2(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }
}
