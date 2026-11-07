package com.example.bizmanagezz.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.bizmanagezz.BuildConfig
import com.example.bizmanagezz.data.repository.SettingsStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface NotificationDeliveryApi {
    @POST("notifications/register")
    suspend fun register(
        @Header("Authorization") authorization: String,
        @Body body: JsonObject
    ): JsonObject

    @POST("notifications/test")
    suspend fun sendTest(
        @Header("Authorization") authorization: String,
        @Body body: JsonObject
    ): JsonObject
}

private object NotificationDeliveryClient {
    val api: NotificationDeliveryApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NotificationDeliveryApi::class.java)
    }
}

@Composable
fun NotificationDeliveryCheck() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Test notification delivery",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                "Register this device with your account and " +
                        "send it a notification through the backend."
            )

            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    message = null

                    scope.launch {
                        try {
                            val auth = FirebaseAuth.getInstance()

                            val user = checkNotNull(auth.currentUser) {
                                "Sign in before sending a notification."
                            }

                            val accountId = user.uid

                            val enabled = SettingsStore(context)
                                .readSettings()
                                .notificationsEnabled

                            check(enabled) {
                                "Enable business notifications in " +
                                        "Settings and save your settings first."
                            }

                            val deviceToken = FirebaseMessaging
                                .getInstance()
                                .token
                                .await()

                            val idToken = user.getIdToken(false)
                                .await()
                                .token

                            check(!idToken.isNullOrBlank()) {
                                "Your session could not be verified."
                            }

                            check(auth.currentUser?.uid == accountId) {
                                "Account changed. Try again."
                            }

                            val authorization = "Bearer $idToken"

                            val registration = JsonObject().apply {
                                addProperty("token", deviceToken)
                                addProperty("enabled", enabled)
                            }

                            NotificationDeliveryClient.api.register(
                                authorization,
                                registration
                            )

                            check(auth.currentUser?.uid == accountId) {
                                "Account changed. Try again."
                            }

                            NotificationDeliveryClient.api.sendTest(
                                authorization,
                                JsonObject().apply {
                                    addProperty("token", deviceToken)
                                }
                            )

                            message =
                                "FCM accepted the test. Pull down the " +
                                        "Android notification shade to check delivery."
                        } catch (
                            cancelled: CancellationException
                        ) {
                            throw cancelled
                        } catch (error: HttpException) {
                            val explanation = runCatching {
                                val body = error.response()
                                    ?.errorBody()
                                    ?.string()

                                body?.let {
                                    JsonParser.parseString(it)
                                        .asJsonObject
                                        .get("error")
                                        ?.asString
                                }
                            }.getOrNull()

                            message = explanation
                                ?: "Server response: ${error.code()}."
                        } catch (error: Exception) {
                            message = error.message
                                ?: "Could not send the test notification."
                        } finally {
                            busy = false
                        }
                    }
                }
            ) {
                Text(
                    if (busy) {
                        "Sending..."
                    } else {
                        "Register and send test"
                    }
                )
            }

            message?.let {
                Text(it)
            }
        }
    }
}