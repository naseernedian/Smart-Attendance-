package com.example.security

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

enum class BiometricAvailability {
    AVAILABLE,
    NONE_ENROLLED,
    UNAVAILABLE
}

object BiometricAuthHelper {
    private const val BIOMETRIC_AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK

    private const val PHONE_BIOMETRIC_OR_CREDENTIAL_API30 =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun checkAvailability(context: Context): BiometricAvailability {
        val manager = BiometricManager.from(context)
        val biometricStatus = manager.canAuthenticate(BIOMETRIC_AUTHENTICATORS)
        if (biometricStatus == BiometricManager.BIOMETRIC_SUCCESS) {
            return BiometricAvailability.AVAILABLE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (manager.canAuthenticate(PHONE_BIOMETRIC_OR_CREDENTIAL_API30) == BiometricManager.BIOMETRIC_SUCCESS) {
                return BiometricAvailability.AVAILABLE
            }
        } else {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            if (km?.isDeviceSecure == true) {
                return BiometricAvailability.AVAILABLE
            }
        }
        return when (biometricStatus) {
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
            else -> BiometricAvailability.UNAVAILABLE
        }
    }

    fun authenticate(
        context: Context,
        title: String = "Biometric Login",
        subtitle: String = "Use your Android phone's biometric to sign in",
        negativeButtonText: String = "Use Password",
        onSucceeded: () -> Unit,
        onError: (String) -> Unit
    ) {
        val activity = context.findFragmentActivity()
        if (activity == null) {
            onError("Biometric prompt requires an active FragmentActivity.")
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSucceeded()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Biometric not recognized. Please try again.")
                }
            }
        )

        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(PHONE_BIOMETRIC_OR_CREDENTIAL_API30)
        } else {
            val hasEnrolledBiometric = BiometricManager.from(activity)
                .canAuthenticate(BIOMETRIC_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
            if (hasEnrolledBiometric) {
                builder.setAllowedAuthenticators(BIOMETRIC_AUTHENTICATORS)
                builder.setNegativeButtonText(negativeButtonText)
            } else {
                @Suppress("DEPRECATION")
                builder.setDeviceCredentialAllowed(true)
            }
        }

        prompt.authenticate(builder.build())
    }

    private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
        is FragmentActivity -> this
        is ContextWrapper -> baseContext.findFragmentActivity()
        else -> null
    }
}
