package com.example.bizmanagezz.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.bizmanagezz.data.local.AccountStorage
import com.example.bizmanagezz.data.local.AppDatabase
import com.example.bizmanagezz.data.sync.ConflictRecovery
import com.example.bizmanagezz.data.sync.ConflictReview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ConflictReviewButton() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    val accountId = remember {
        AccountStorage.currentUid()
    }

    val database = remember(context, accountId) {
        AppDatabase.getInstance(context, accountId)
    }

    var review by remember {
        mutableStateOf<ConflictReview?>(null)
    }

    var busy by remember { mutableStateOf(false) }
    var confirmed by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    OutlinedButton(
        enabled = !busy,
        onClick = {
            busy = true
            message = null
            confirmed = false

            scope.launch {
                try {
                    review = ConflictRecovery.review(
                        database,
                        accountId
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    message = error.message
                        ?: "Could not load the conflict."
                } finally {
                    busy = false
                }
            }
        }
    ) {
        Text(
            if (busy) "Loading..." else "Review blocked change"
        )
    }

    message?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall
        )
    }

    review?.let { current ->
        AlertDialog(
            onDismissRequest = {
                if (!busy) review = null
            },
            title = {
                Text("Review saved changes")
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "On this device",
                        style = MaterialTheme.typography.titleSmall
                    )

                    Text(current.localSummary)

                    HorizontalDivider()

                    Text(
                        "In the cloud",
                        style = MaterialTheme.typography.titleSmall
                    )

                    Text(current.cloudSummary)

                    HorizontalDivider()

                    Text(
                        "${current.operations.size} queued change(s) " +
                                "for this record will be archived."
                    )

                    if (current.pendingSaleCount > 0) {
                        Text(
                            "${current.pendingSaleCount} pending sale(s) " +
                                    "are affected. Sales absent from the cloud " +
                                    "will be removed from active local history " +
                                    "and archived. Cloud sales will be retained.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Text(
                        "The cloud record will not be changed. " +
                                "Your device will use the cloud version. " +
                                "If it does not exist in the cloud, it will " +
                                "be removed from active local records."
                    )

                    Row {
                        Checkbox(
                            checked = confirmed,
                            enabled = !busy,
                            onCheckedChange = {
                                confirmed = it
                            }
                        )

                        Text(
                            text = "I understand and want to use " +
                                    "the cloud version.",
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }

                    message?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = confirmed && !busy,
                    onClick = {
                        busy = true
                        message = null

                        scope.launch {
                            try {
                                ConflictRecovery.useCloud(
                                    database,
                                    current
                                )

                                review = null
                                message =
                                    "Cloud version applied. " +
                                            "Rejected local changes were archived."
                            } catch (
                                cancelled: CancellationException
                            ) {
                                throw cancelled
                            } catch (error: Exception) {
                                message = error.message
                                    ?: "Could not resolve this conflict."
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) {
                    Text(
                        if (busy) "Applying..." else "Use cloud version"
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { review = null }
                ) {
                    Text("Keep pending")
                }
            }
        )
    }
}