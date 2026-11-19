package com.example.domain

sealed class UsernameValidationResult {
    data class ValidCnic(val cnic: String) : UsernameValidationResult()
    data class ValidMobile(val mobile: String) : UsernameValidationResult()
    data class Invalid(val message: String) : UsernameValidationResult()
}

object Validators {
    private val CNIC_REGEX = Regex("^\\d{13}$")
    private val MOBILE_REGEX = Regex("^03\\d{9}$")
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    /**
     * Validates Pakistani CNIC in raw 13-digit format: XXXXXXXXXXXXX.
     */
    fun validateCnic(input: String): String? {
        val trimmed = input.trim()
        return if (CNIC_REGEX.matches(trimmed)) trimmed else null
    }

    /**
     * Validates Pakistani mobile number in 11-digit format: 03XXXXXXXXX.
     */
    fun validateMobile(input: String): String? {
        val trimmed = input.trim()
        return if (MOBILE_REGEX.matches(trimmed)) trimmed else null
    }

    fun normalizeMobile(input: String): String? = validateMobile(input)

    fun parseAdminUsername(input: String): UsernameValidationResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return UsernameValidationResult.Invalid(
                "Enter Mobile (03XXXXXXXXX) or CNIC (XXXXXXXXXXXXX)."
            )
        }
        validateCnic(trimmed)?.let { cnic ->
            return UsernameValidationResult.ValidCnic(cnic)
        }
        validateMobile(trimmed)?.let { mobile ->
            return UsernameValidationResult.ValidMobile(mobile)
        }
        return UsernameValidationResult.Invalid(
            "Invalid username. Use Mobile (03XXXXXXXXX) or CNIC (XXXXXXXXXXXXX)."
        )
    }

    fun isValidEmail(input: String): Boolean = EMAIL_REGEX.matches(input.trim())

    fun validatePassword(password: String): String? {
        if (password.length < 8) return "Password must be at least 8 characters."
        if (!password.any { it.isDigit() } || !password.any { it.isLetter() }) {
            return "Password must include letters and numbers."
        }
        return null
    }
}
