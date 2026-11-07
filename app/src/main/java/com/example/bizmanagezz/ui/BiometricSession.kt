package com.example.bizmanagezz.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.bizmanagezz.data.local.AccountStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BiometricSession(
    onLogout: () -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) {
        requireNotNull(context.findFragmentActivity()) {
            "Biometric authentication requires FragmentActivity."
        }
    }

    val uid = AccountStorage.currentUid()
    val scope = rememberCoroutineScope()

    val preferences = remember(context, uid) {
        val suffix = AccountStorage.suffix(context, uid)

        context.applicationContext.getSharedPreferences(
            "bizmanage_biometric$suffix",
            Context.MODE_PRIVATE
        )
    }

    var enabled by remember(preferences) {
        mutableStateOf(
            preferences.getBoolean("enabled", false)
        )
    }

    // Deliberately not saved across process recreation or rotation.
    var unlocked by remember { mutableStateOf(false) }
    var skipped by remember { mutableStateOf(false) }
    var prompting by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val biometricManager = remember(context) {
        BiometricManager.from(context)
    }

    val prompt = remember(activity, preferences) {
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {

                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    prompting = false

                    if (
                        !activity.lifecycle.currentState.isAtLeast(
                            Lifecycle.State.STARTED
                        )
                    ) {
                        return
                    }

                    if (enabled) {
                        unlocked = true
                        error = null

                        Log.d(
                            "BizManageBiometric",
                            "Session unlocked"
                        )
                    } else {
                        saving = true

                        scope.launch {
                            try {
                                val saved = withContext(Dispatchers.IO) {
                                    preferences.edit()
                                        .putBoolean("enabled", true)
                                        .commit()
                                }

                                if (saved) {
                                    enabled = true
                                    unlocked =
                                        activity.lifecycle.currentState.isAtLeast(
                                            Lifecycle.State.STARTED
                                        )
                                    error = null
                                } else {
                                    error =
                                        "Could not save biometric settings. Check available storage."
                                }
                            } finally {
                                saving = false
                            }
                        }
                    }
                }

                override fun onAuthenticationFailed() {
                    error =
                        "Biometric not recognised. Try again in the system prompt."
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    prompting = false

                    error = when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_CANCELED -> {
                            "Authentication cancelled."
                        }

                        BiometricPrompt.ERROR_LOCKOUT,
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                            "Biometric authentication is locked. Unlock your phone normally, then try again."
                        }

                        else -> {
                            "Biometric authentication is unavailable. Check device security settings."
                        }
                    }
                }
            }
        )
    }

    DisposableEffect(activity, prompt) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                unlocked = false

                if (prompting) {
                    prompt.cancelAuthentication()
                    prompting = false
                }
            }
        }

        activity.lifecycle.addObserver(observer)

        onDispose {
            activity.lifecycle.removeObserver(observer)
            prompt.cancelAuthentication()
        }
    }

    if ((enabled && unlocked) || (!enabled && skipped)) {
        content()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (enabled) {
                "Unlock BizManage SA"
            } else {
                "Protect your business"
            },
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = if (enabled) {
                "Verify your fingerprint or supported face authentication to continue."
            } else {
                "Enable a biometric lock for your signed-in account."
            }
        )

        Spacer(Modifier.height(24.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !prompting && !saving,
            onClick = {
                error = null

                val capability = biometricManager.canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG
                )

                when (capability) {
                    BiometricManager.BIOMETRIC_SUCCESS -> {
                        val promptInfo =
                            BiometricPrompt.PromptInfo.Builder()
                                .setTitle("BizManage SA")
                                .setSubtitle(
                                    if (enabled) {
                                        "Unlock your business"
                                    } else {
                                        "Confirm biometric protection"
                                    }
                                )
                                .setAllowedAuthenticators(
                                    BiometricManager.Authenticators.BIOMETRIC_STRONG
                                )
                                .setNegativeButtonText("Cancel")
                                .build()

                        prompting = true

                        try {
                            prompt.authenticate(promptInfo)
                        } catch (_: Exception) {
                            prompting = false
                            error =
                                "Could not open biometric authentication."
                        }
                    }

                    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                        error =
                            "Set up a fingerprint or supported face authentication in device security settings."
                    }

                    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                        error =
                            "This device does not support strong biometric authentication."
                    }

                    else -> {
                        error =
                            "Biometric authentication is temporarily unavailable."
                    }
                }
            }
        ) {
            Text(
                when {
                    saving -> "Saving..."
                    prompting -> "Waiting for verification..."
                    enabled -> "Unlock"
                    else -> "Enable Biometric Lock"
                }
            )
        }

        error?.let {
            Spacer(Modifier.height(12.dp))

            Text(
                text = it,
                color = MaterialTheme.colorScheme.error
            )
        }

        TextButton(
            enabled = !prompting && !saving,
            onClick = {
                try {
                    val intent = if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    ) {
                        Intent(Settings.ACTION_BIOMETRIC_ENROLL)
                            .putExtra(
                                Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                                BiometricManager.Authenticators.BIOMETRIC_STRONG
                            )
                    } else {
                        Intent(Settings.ACTION_SECURITY_SETTINGS)
                    }

                    activity.startActivity(intent)
                } catch (_: Exception) {
                    error =
                        "Open your device Settings and set up biometric security."
                }
            }
        ) {
            Text("Device Security Settings")
        }

        // Skipping is allowed only before biometric locking is enabled.
        if (!enabled) {
            TextButton(
                enabled = !prompting && !saving,
                onClick = { skipped = true }
            ) {
                Text("Not Now")
            }
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !prompting && !saving,
            onClick = onLogout
        ) {
            Text("Sign Out")
        }
    }
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context = this

    while (current is ContextWrapper) {
        if (current is FragmentActivity) {
            return current
        }

        val next = current.baseContext

        if (next === current) {
            return null
        }

        current = next
    }

    return current as? FragmentActivity
}