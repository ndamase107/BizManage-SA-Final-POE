package com.example.bizmanagezz.data.sync

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.bizmanagezz.data.api.ApiClient
import com.example.bizmanagezz.data.api.CloudProductWrite
import com.example.bizmanagezz.data.api.CloudSaleWrite
import com.example.bizmanagezz.data.local.AppDatabase
import com.example.bizmanagezz.data.local.CloudProductVersionEntity
import com.example.bizmanagezz.data.local.ProductEntity
import com.example.bizmanagezz.data.local.SyncMetadataEntity
import com.example.bizmanagezz.data.local.SyncOperationEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class InventorySyncWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    private val gson = Gson()

    override suspend fun doWork(): Result {
        val accountId = inputData.getString(
            SyncScheduler.ACCOUNT_ID
        ) ?: return Result.failure()

        // A worker for one account must never use another account's token.
        if (!isCurrentAccount(accountId)) {
            return Result.success()
        }

        return SyncLocks.withAccountLock(accountId) {
            runSync(accountId)
        }
    }

    private suspend fun runSync(accountId: String): Result {
        var database: AppDatabase? = null
        var activeOperation: SyncOperationEntity? = null

        try {
            ensureCurrentAccount(accountId)

            val db = AppDatabase.getInstance(
                applicationContext,
                accountId
            )

            database = db

            // Repository integration will set this only after local
            // inventory has been safely placed into the outgoing queue.
            if (
                db.syncDao().getMetadata(
                    "inventory_bootstrap"
                ) != "ready"
            ) {
                return Result.success()
            }

            // Bound each run so a large queue cannot run indefinitely.
            repeat(100) {
                ensureCurrentAccount(accountId)

                val operation = db.syncDao()
                    .oldestOperation()
                    ?: return@repeat

                activeOperation = operation

                if (operation.status == "BLOCKED") {
                    return Result.failure()
                }

                processOperation(
                    db = db,
                    accountId = accountId,
                    operation = operation
                )

                activeOperation = null
            }

            // Do not pull over changes still waiting to be uploaded.
            if (db.syncDao().oldestOperation() != null) {
                return Result.retry()
            }

            pullProducts(db, accountId)

            BusinessCloudSync.pull(
                db = db,
                accountId = accountId,
                authorization = authorization(accountId)
            )

            db.syncDao().saveMetadata(
                SyncMetadataEntity(
                    key = "last_inventory_sync",
                    value = System.currentTimeMillis().toString()
                )
            )

            Log.d(TAG, "Inventory synchronization completed")
            return Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: AccountChangedException) {
            return Result.success()
        } catch (error: HttpException) {
            val code = error.code()
            val operation = activeOperation

            if (
                operation != null &&
                code in setOf(400, 403, 404, 409, 422)
            ) {
                database?.syncDao()?.markBlocked(
                    operation.id,
                    when (code) {
                        409 -> "Cloud data changed. Review this operation."
                        403 -> "The server refused this operation."
                        404 -> "The cloud record was not found."
                        else -> "The server rejected this operation."
                    }
                )

                return Result.failure()
            }

            if (operation != null) {
                database?.syncDao()?.recordRetry(
                    operation.id,
                    "Server response: $code. Waiting to retry."
                )
            }

            return Result.retry()
        } catch (_: IOException) {
            activeOperation?.let { operation ->
                database?.syncDao()?.recordRetry(
                    operation.id,
                    "Connection unavailable. Waiting to retry."
                )
            }

            return Result.retry()
        } catch (error: Exception) {
            // Temporary authentication-service and network failures
            // must retain the queue and use WorkManager backoff.
            val temporaryFailure = generateSequence<Throwable>(
                error
            ) {
                it.cause
            }.take(8).any { cause ->
                cause is java.io.IOException ||
                    cause is com.google.firebase.FirebaseNetworkException ||
                    cause is com.google.firebase.FirebaseTooManyRequestsException
            }

            if (temporaryFailure) {
                activeOperation?.let { operation ->
                    database?.syncDao()?.recordRetry(
                        operation.id,
                        "Temporary connection failure. Retrying automatically."
                    )
                }

                Log.w(
                    TAG,
                    "Temporary sync failure: ${error.javaClass.simpleName}"
                )

                return Result.retry()
            }
            // Do not log tokens, request bodies, or customer details.
            Log.w(
                TAG,
                "Synchronization stopped: ${error.javaClass.simpleName}"
            )

            activeOperation?.let { operation ->
                database?.syncDao()?.markBlocked(
                    operation.id,
                    "Synchronization needs review."
                )
            }

            return Result.failure()
        }
    }

    private suspend fun processOperation(
        db: AppDatabase,
        accountId: String,
        operation: SyncOperationEntity
    ) {
        val payload = preparePayload(db, operation)
        val authorization = authorization(accountId)

        when (operation.type) {
            "PRODUCT_PUT" -> {
                val request = gson.fromJson(
                    payload.getAsJsonObject("request"),
                    CloudProductWrite::class.java
                )

                val result = ApiClient.api.saveProduct(
                    authorization = authorization,
                    id = operation.entityId,
                    product = request,
                    operationId = operation.operationId
                )

                ensureCurrentAccount(accountId)

                require(result.id == operation.entityId)

                db.withTransaction {
                    db.syncDao().saveProductVersion(
                        CloudProductVersionEntity(
                            productId = result.id,
                            version = result.version
                        )
                    )

                    db.syncDao().acknowledge(operation.id)
                }
            }

            "PRODUCT_DELETE" -> {
                val expectedVersion = payload
                    .getAsJsonObject("request")
                    .get("expectedVersion")
                    .asLong

                ApiClient.api.deleteProduct(
                    authorization = authorization,
                    id = operation.entityId,
                    expectedVersion = expectedVersion,
                    operationId = operation.operationId
                )

                ensureCurrentAccount(accountId)

                db.withTransaction {
                    db.syncDao().saveProductVersion(
                        CloudProductVersionEntity(
                            productId = operation.entityId,
                            version = expectedVersion + 1
                        )
                    )

                    db.syncDao().acknowledge(operation.id)
                }
            }

            "SALE" -> {
                val request = gson.fromJson(
                    payload.getAsJsonObject("request"),
                    CloudSaleWrite::class.java
                )

                require(request.id == operation.operationId)
                require(request.productId == operation.entityId)

                val result = ApiClient.api.createSale(
                    authorization = authorization,
                    sale = request
                )

                ensureCurrentAccount(accountId)

                require(result.id == request.id)

                val expectedTotal = Math.multiplyExact(
                    request.expectedPriceCents,
                    request.quantity.toLong()
                )

                require(result.totalCents == expectedTotal)

                db.withTransaction {
                    val previousVersion = db.syncDao()
                        .getProductVersion(operation.entityId)
                        ?.version
                        ?: 0L

                    // A sale advances the cloud product version once.
                    // This acknowledgement and queue removal are atomic.
                    db.syncDao().saveProductVersion(
                        CloudProductVersionEntity(
                            productId = operation.entityId,
                            version = previousVersion + 1
                        )
                    )

                    db.syncDao().markSaleSynced(request.id)
                    db.syncDao().acknowledge(operation.id)
                }
            }

            "CUSTOMER_PUT", "EXPENSE_PUT" -> {
                BusinessCloudSync.push(
                    db = db,
                    accountId = accountId,
                    authorization = authorization,
                    operation = operation,
                    payload = payload
                )
            }

            else -> error("Unsupported synchronization operation")
        }
    }

    private suspend fun preparePayload(
        db: AppDatabase,
        operation: SyncOperationEntity
    ): JsonObject {
        return db.withTransaction {
            val payload = JsonParser.parseString(
                operation.payload
            ).asJsonObject

            if (
                !payload.has("prepared") ||
                !payload.get("prepared").asBoolean
            ) {
                val request = payload.getAsJsonObject("request")

                when (operation.type) {
                    "PRODUCT_PUT", "PRODUCT_DELETE", "CUSTOMER_PUT", "EXPENSE_PUT" -> {
                        val version = db.syncDao()
                            .getProductVersion(operation.entityId)
                            ?.version
                            ?: 0L

                        request.addProperty(
                            "expectedVersion",
                            version
                        )
                    }

                    "SALE" -> Unit

                    else -> error("Unsupported operation")
                }

                payload.addProperty("prepared", true)

                // Persist before the network request. If the process dies,
                // the next attempt sends exactly the same request.
                db.syncDao().updatePayload(
                    operation.id,
                    payload.toString()
                )
            }

            payload
        }
    }

    private suspend fun pullProducts(
        db: AppDatabase,
        accountId: String
    ) {
        var cursor: String? = null
        val visitedCursors = mutableSetOf<String>()

        do {
            ensureCurrentAccount(accountId)

            val page = ApiClient.api.getProducts(
                authorization = authorization(accountId),
                after = cursor
            )

            ensureCurrentAccount(accountId)

            db.withTransaction {
                for (product in page.items) {
                    // New local changes can arrive while a pull is running.
                    // Never replace a product with outstanding local work.
                    if (
                        db.syncDao().hasOutstandingOperation(
                            product.id
                        )
                    ) {
                        continue
                    }

                    if (product.deleted) {
                        db.productDao().delete(product.id)
                    } else {
                        db.productDao().upsert(
                            ProductEntity(
                                id = product.id,
                                name = product.name,
                                category = product.category,
                                costPrice =
                                    product.costPriceCents / 100.0,
                                sellingPrice =
                                    product.sellingPriceCents / 100.0,
                                quantity = product.quantity,
                                minimumStock = product.minimumStock,
                                barcode = product.barcode
                            )
                        )
                    }

                    db.syncDao().saveProductVersion(
                        CloudProductVersionEntity(
                            productId = product.id,
                            version = product.version
                        )
                    )
                }
            }

            cursor = page.nextCursor

            if (cursor != null) {
                check(visitedCursors.add(cursor)) {
                    "Repeated cloud page cursor"
                }
            }
        } while (cursor != null)
    }

    private suspend fun authorization(
        accountId: String
    ): String {
        ensureCurrentAccount(accountId)

        val user = checkNotNull(
            FirebaseAuth.getInstance().currentUser
        )

        // Refresh on later WorkManager attempts after an authentication error.
        val token = user.getIdToken(runAttemptCount > 0)
            .await()
            .token

        ensureCurrentAccount(accountId)

        check(!token.isNullOrBlank()) {
            "No authentication token"
        }

        return "Bearer $token"
    }

    private fun isCurrentAccount(accountId: String): Boolean {
        return FirebaseAuth.getInstance()
            .currentUser
            ?.uid == accountId
    }

    private fun ensureCurrentAccount(accountId: String) {
        if (!isCurrentAccount(accountId)) {
            throw AccountChangedException()
        }
    }

    private class AccountChangedException : Exception()

    companion object {
        private const val TAG = "InventorySync"

        private val accountLocks =
            ConcurrentHashMap<String, Mutex>()
    }
}