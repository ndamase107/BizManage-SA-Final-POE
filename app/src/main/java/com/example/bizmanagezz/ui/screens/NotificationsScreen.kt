package com.example.bizmanagezz.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun NotificationsScreen() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var permissionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var token by remember {
        mutableStateOf<String?>(null)
    }

    var loading by remember {
        mutableStateOf(false)
    }

    var message by remember {
        mutableStateOf<String?>(null)
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            permissionGranted = granted

            message = if (granted) {
                "Notification permission granted."
            } else {
                "Notifications are disabled. You can enable " +
                        "them in Android settings."
            }
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Notifications",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            "Receive stock alerts and business updates."
        )

        Card(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    if (permissionGranted) {
                        "Notification permission is granted."
                    } else {
                        "Notification permission is needed."
                    }
                )

                Button(
                    enabled = !permissionGranted,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            permissionLauncher.launch(
                                Manifest.permission.POST_NOTIFICATIONS
                            )
                        }
                    }
                ) {
                    Text("Allow notifications")
                }

                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APP_NOTIFICATION_SETTINGS
                            ).putExtra(
                                Settings.EXTRA_APP_PACKAGE,
                                context.packageName
                            )
                        )
                    }
                ) {
                    Text("Android notification settings")
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Messaging connection",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    if (token == null) {
                        "Check whether this device can register with FCM."
                    } else {
                        "FCM device token received."
                    }
                )

                Button(
                    enabled = !loading,
                    onClick = {
                        loading = true
                        message = null

                        scope.launch {
                            try {
                                token = FirebaseMessaging
                                    .getInstance()
                                    .token
                                    .await()

                                message =
                                    "FCM connection successful. " +
                                            "Backend registration is the next step."
                            } catch (
                                cancelled: CancellationException
                            ) {
                                throw cancelled
                            } catch (_: Exception) {
                                message =
                                    "Could not obtain the FCM token. " +
                                            "Check the internet connection " +
                                            "and Google Play services."
                            } finally {
                                loading = false
                            }
                        }
                    }
                ) {
                    Text(
                        if (loading) {
                            "Checking..."
                        } else {
                            "Check messaging connection"
                        }
                    )
                }

                if (token != null) {
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(
                                AnnotatedString(
                                    checkNotNull(token)
                                )
                            )

                            message =
                                "Device token copied. " +
                                        "Do not publish it."
                        }
                    ) {
                        Text("Copy device token")
                    }
                }
            }
        }

        NotificationDeliveryCheck()

        message?.let {
            Text(it)
        }

        TextButton(
            onClick = {
                permissionGranted =
                    Build.VERSION.SDK_INT < 33 ||
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED

                message = if (
                    NotificationManagerCompat.from(context)
                        .areNotificationsEnabled()
                ) {
                    "Android notifications are enabled."
                } else {
                    "Android notifications are disabled."
                }
            }
        ) {
            Text("Refresh permission status")
        }

        Text(
            "Also enable business notifications in the app's Settings.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}