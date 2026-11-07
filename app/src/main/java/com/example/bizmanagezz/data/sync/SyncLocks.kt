package com.example.bizmanagezz.data.sync

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object SyncLocks {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withAccountLock(
        accountId: String,
        action: suspend () -> T
    ): T {
        val mutex = locks.getOrPut(accountId) { Mutex() }

        return mutex.withLock {
            action()
        }
    }
}