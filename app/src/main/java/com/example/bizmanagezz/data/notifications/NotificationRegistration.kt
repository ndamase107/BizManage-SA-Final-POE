package com.example.bizmanagezz.data.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.example.bizmanagezz.BuildConfig
import com.example.bizmanagezz.data.repository.SettingsStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

interface DeviceRegistrationApi {
    @POST("notifications/register")
    suspend fun register(
        @Header("Authorization") authorization: String,
        @Body request: JsonObject
    ): JsonObject
}

object NotificationRegistration {
    private const val ACCOUNT_ID = "account_id"

    internal val locks =
        ConcurrentHashMap<String, Mutex>()

    internal val api: DeviceRegistrationApi by lazy {
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
            .create(DeviceRegistrationApi::class.java)
    }

    fun schedule(context: Context, accountId: String) {
        if (
            FirebaseAuth.getInstance().currentUser?.uid != accountId
        ) {
            return
        }

        val manager = WorkManager.getInstance(
            context.applicationContext
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val input = workDataOf(ACCOUNT_ID to accountId)

        val periodic =
            PeriodicWorkRequestBuilder<DeviceRegistrationWorker>(
                15,
                TimeUnit.MINUTES
            )
                .setInputData(input)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .build()

        manager.enqueueUniquePeriodicWork(
            "notification-registration-periodic-$accountId",
            ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )

        val immediate =
            OneTimeWorkRequestBuilder<DeviceRegistrationWorker>()
                .setInputData(input)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .build()

        manager.enqueueUniqueWork(
            "notification-registration-$accountId",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            immediate
        )
    }

    internal fun accountId(parameters: WorkerParameters): String? {
        return parameters.inputData.getString(ACCOUNT_ID)
    }
}

class DeviceRegistrationWorker(
    context: Context,
    private val parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val accountId = NotificationRegistration
            .accountId(parameters)
            ?: return Result.failure()

        val mutex = NotificationRegistration.locks
            .getOrPut(accountId) { Mutex() }

        return mutex.withLock {
            register(accountId)
        }
    }

    private suspend fun register(accountId: String): Result {
        try {
            val auth = FirebaseAuth.getInstance()

            val user = auth.currentUser
                ?: return Result.success()

            if (user.uid != accountId) {
                return Result.success()
            }

            val deviceToken = FirebaseMessaging
                .getInstance()
                .token
                .await()

            val idToken = user.getIdToken(
                runAttemptCount > 0
            ).await().token

            if (auth.currentUser?.uid != accountId) {
                return Result.success()
            }

            if (idToken.isNullOrBlank()) {
                return Result.retry()
            }

            val permissionGranted =
                Build.VERSION.SDK_INT < 33 ||
                        ContextCompat.checkSelfPermission(
                            applicationContext,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED

            val enabled = SettingsStore(applicationContext)
                .readSettings()
                .notificationsEnabled &&
                    permissionGranted &&
                    NotificationManagerCompat
                        .from(applicationContext)
                        .areNotificationsEnabled()

            val request = JsonObject().apply {
                addProperty("token", deviceToken)
                addProperty("enabled", enabled)
            }

            if (auth.currentUser?.uid != accountId) {
                return Result.success()
            }

            NotificationRegistration.api.register(
                "Bearer $idToken",
                request
            )

            return Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Registration is safe to retry with the latest token.
            return Result.retry()
        }
    }
}