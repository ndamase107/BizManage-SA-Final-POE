package com.example.bizmanagezz.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object SyncScheduler {

    const val ACCOUNT_ID = "account_id"

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedule(context: Context, accountId: String) {
        require(accountId.isNotBlank())

        val manager = WorkManager.getInstance(
            context.applicationContext
        )

        val input = workDataOf(
            ACCOUNT_ID to accountId
        )

        // Periodic work provides recovery if the process stops after
        // a database write but before immediate work is scheduled.
        val periodic =
            PeriodicWorkRequestBuilder<InventorySyncWorker>(
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
            "inventory-periodic-$accountId",
            ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )

        val immediate =
            OneTimeWorkRequestBuilder<InventorySyncWorker>()
                .setInputData(input)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .build()

        // A request made during an existing run is retained.
        manager.enqueueUniqueWork(
            "inventory-immediate-$accountId",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            immediate
        )
    }

    fun cancel(context: Context, accountId: String) {
        val manager = WorkManager.getInstance(
            context.applicationContext
        )

        manager.cancelUniqueWork(
            "inventory-immediate-$accountId"
        )

        manager.cancelUniqueWork(
            "inventory-periodic-$accountId"
        )
    }
}