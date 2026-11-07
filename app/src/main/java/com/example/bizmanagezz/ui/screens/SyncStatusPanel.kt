package com.example.bizmanagezz.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.room.withTransaction
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.bizmanagezz.data.local.AccountStorage
import com.example.bizmanagezz.data.local.AppDatabase
import com.example.bizmanagezz.data.local.SyncOperationEntity
import com.example.bizmanagezz.data.sync.SyncQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun SyncStatusPanel() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    val accountId = remember {
        AccountStorage.currentUid()
    }

    val database = remember(context, accountId) {
        AppDatabase.getInstance(context, accountId)
    }

    var operations by remember {
        mutableStateOf<List<SyncOperationEntity>>(emptyList())
    }

    var lastSync by remember {
        mutableStateOf<Long?>(null)
    }

    var running by remember {
        mutableStateOf(false)
    }

    var loaded by remember {
        mutableStateOf(false)
    }

    var busy by remember {
        mutableStateOf(false)
    }

    var actionMessage by remember {
        mutableStateOf<String?>(null)
    }

    var readError by remember {
        mutableStateOf(false)
    }

    // Room emits whenever queue records are inserted, acknowledged,
    // or marked as blocked.
    LaunchedEffect(database) {
        database.syncDao()
            .observeOperations()
            .collect {
                operations = it
                loaded = true

                if (it.isEmpty()) {
                    actionMessage = null
                }
            }
    }

    LaunchedEffect(database, accountId) {
        while (isActive) {
            try {
                SyncQueue.requireAccount(accountId)

                val snapshot = withContext(Dispatchers.IO) {
                    val syncTime = database.syncDao()
                        .getMetadata("last_inventory_sync")
                        ?.toLongOrNull()

                    val manager = WorkManager.getInstance(context)

                    val immediate = manager
                        .getWorkInfosForUniqueWork(
                            "inventory-immediate-$accountId"
                        )
                        .get()

                    val periodic = manager
                        .getWorkInfosForUniqueWork(
                            "inventory-periodic-$accountId"
                        )
                        .get()

                    val isRunning = (immediate + periodic).any {
                        it.state == WorkInfo.State.RUNNING
                    }

                    syncTime to isRunning
                }

                lastSync = snapshot.first
                running = snapshot.second
                readError = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                readError = true
            }

            delay(2_000)
        }
    }

    val blocked = operations.filter {
        it.status == "BLOCKED"
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Business data sync",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = when {
                    !loaded -> "Checking saved changesÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦"
                    blocked.isNotEmpty() ->
                        "Sync needs attention."
                    running -> "SynchronizingÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦"
                    operations.isNotEmpty() ->
                        "${operations.size} change(s) waiting to upload."
                    lastSync != null ->
                        "No changes waiting to upload."
                    else ->
                        "Waiting for the first completed sync."
                }
            )

            lastSync?.let { timestamp ->
                Text(
                    text = "Last completed sync: ${
                        DateFormat.getDateTimeInstance(
                            DateFormat.SHORT,
                            DateFormat.SHORT
                        ).format(Date(timestamp))
                    }",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (readError) {
                Text(
                    text = "Could not refresh sync status.",
                    color = MaterialTheme.colorScheme.error
                )
            }

            operations.firstOrNull()?.let { first ->
                first.lastError?.let { explanation ->
                    Text(
                        text = explanation,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (blocked.isNotEmpty()) {
                ConflictReviewButton()

                Text(
                    text = "Your local changes are still saved. " +
                            "Retry keeps the original request and does " +
                            "not overwrite a newer cloud version.",
                    style = MaterialTheme.typography.bodySmall
                )

                OutlinedButton(
                    enabled = !busy && !running,
                    onClick = {
                        busy = true
                        actionMessage = null

                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    SyncQueue.requireAccount(accountId)

                                    database.withTransaction {
                                        for (operation in
                                        database.syncDao()
                                            .getOperations()
                                        ) {
                                            if (
                                                operation.status ==
                                                "BLOCKED"
                                            ) {
                                                database.syncDao()
                                                    .recordRetry(
                                                        operation.id,
                                                        null
                                                    )
                                            }
                                        }
                                    }
                                }

                                SyncQueue.schedule(accountId)

                                actionMessage =
                                    "Retry requested. A genuine cloud " +
                                            "conflict will remain blocked."
                            } catch (
                                cancelled: CancellationException
                            ) {
                                throw cancelled
                            } catch (_: Exception) {
                                actionMessage =
                                    "Could not request a retry."
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) {
                    Text("Retry saved changes")
                }
            }

            Button(
                enabled = loaded &&
                        !busy &&
                        !running &&
                        blocked.isEmpty(),
                onClick = {
                    busy = true
                    actionMessage = null

                    scope.launch {
                        try {
                            SyncQueue.start(database, accountId)
                            SyncQueue.schedule(accountId)

                            actionMessage =
                                "Sync requested. Internet is required."
                        } catch (
                            cancelled: CancellationException
                        ) {
                            throw cancelled
                        } catch (_: Exception) {
                            actionMessage =
                                "Could not request synchronization."
                        } finally {
                            busy = false
                        }
                    }
                }
            ) {
                Text(
                    if (running) {
                        "SynchronizingÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦"
                    } else {
                        "Sync now"
                    }
                )
            }

            actionMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                text = "Covers products, customers, expenses and " +
                        "product-based sales. Historical sales and " +
                        "business profile settings remain local.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}