package com.example.bizmanagezz.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.bizmanagezz.data.local.AccountStorage
import com.example.bizmanagezz.data.local.AppDatabase
import com.example.bizmanagezz.data.sync.SyncQueue
import kotlinx.coroutines.CancellationException

@Composable
fun ObserveLocalChanges(
    vararg tables: String,
    onError: (Exception) -> Unit,
    onChanged: suspend () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current

    val accountId = remember {
        AccountStorage.currentUid()
    }

    val database = remember(context, accountId) {
        AppDatabase.getInstance(context, accountId)
    }

    val currentOnChanged = rememberUpdatedState(onChanged)
    val currentOnError = rememberUpdatedState(onError)

    val tableKey = tables.joinToString(",")

    LaunchedEffect(
        database,
        lifecycleOwner,
        tableKey
    ) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(
            Lifecycle.State.STARTED
        ) {
            database.invalidationTracker.createFlow(
                *tables,
                emitInitialState = true
            ).collect {
                try {
                    SyncQueue.requireAccount(accountId)
                    currentOnChanged.value()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    currentOnError.value(error)
                }
            }
        }
    }
}