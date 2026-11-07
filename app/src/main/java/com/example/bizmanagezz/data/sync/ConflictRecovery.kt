package com.example.bizmanagezz.data.sync

import androidx.room.withTransaction
import com.example.bizmanagezz.BuildConfig
import com.example.bizmanagezz.data.local.*
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

interface ConflictReadApi {
    @GET("{collection}")
    suspend fun page(
        @Header("Authorization") authorization: String,
        @Path("collection") collection: String,
        @Query("after") after: String? = null
    ): JsonObject
}

data class ConflictReview(
    val accountId: String,
    val entityKey: String,
    val collection: String,
    val recordId: String,
    val operations: List<SyncOperationEntity>,
    val localSnapshot: JsonObject,
    val cloudRecord: JsonObject?,
    val cloudSales: Map<String, JsonObject>
) {
    val pendingSaleCount: Int
        get() = operations.count { it.type == "SALE" }

    val localSummary: String
        get() = summary(
            localSnapshot.get("record"),
            cloud = false
        )

    val cloudSummary: String
        get() = summary(cloudRecord, cloud = true)

    private fun summary(
        element: JsonElement?,
        cloud: Boolean
    ): String {
        if (element == null || element.isJsonNull) {
            return "Record does not exist."
        }

        val record = element.asJsonObject

        if (record.get("deleted")?.asBoolean == true) {
            return "Product has been deleted."
        }

        val title = record.get("name")
            ?: record.get("description")

        val lines = mutableListOf(
            title?.asString ?: "Record"
        )

        record.get("quantity")?.let {
            lines.add("Stock: ${it.asInt}")
        }

        if (collection == "expenses") {
            val amount = if (cloud) {
                record.get("amountCents").asLong / 100.0
            } else {
                record.get("amount").asDouble
            }

            lines.add(
                String.format(
                    Locale.US,
                    "Amount: R%.2f",
                    amount
                )
            )
        }

        record.get("phone")?.let {
            if (!it.isJsonNull && it.asString.isNotBlank()) {
                lines.add("Phone: ${it.asString}")
            }
        }

        record.get("version")?.let {
            lines.add("Cloud version: ${it.asLong}")
        }

        return lines.joinToString("\n")
    }
}

object ConflictRecovery {
    private val gson = Gson()

    private val api: ConflictReadApi by lazy {
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
            .create(ConflictReadApi::class.java)
    }

    suspend fun review(
        db: AppDatabase,
        accountId: String
    ): ConflictReview {
        return SyncLocks.withAccountLock(accountId) {
            loadReview(db, accountId)
        }
    }

    suspend fun useCloud(
        db: AppDatabase,
        approved: ConflictReview
    ) {
        SyncLocks.withAccountLock(approved.accountId) {
            val fresh = loadReview(db, approved.accountId)

            check(
                fresh.entityKey == approved.entityKey &&
                        fresh.operations == approved.operations &&
                        fresh.localSnapshot == approved.localSnapshot &&
                        fresh.cloudRecord == approved.cloudRecord &&
                        fresh.cloudSales == approved.cloudSales
            ) {
                "The record changed. Close this review and open it again."
            }

            db.withTransaction {
                SyncQueue.requireAccount(approved.accountId)

                val head = db.syncDao().oldestOperation()

                check(
                    head?.id == approved.operations.first().id &&
                            head.status == "BLOCKED"
                ) {
                    "The queue changed. Open the review again."
                }

                val currentOperations = db.syncDao()
                    .getOperations()
                    .filter { it.entityId == approved.entityKey }

                check(currentOperations == approved.operations) {
                    "New local changes were added. Open the review again."
                }

                check(
                    snapshot(
                        db,
                        approved.collection,
                        approved.recordId,
                        currentOperations
                    ) == approved.localSnapshot
                ) {
                    "Local data changed. Open the review again."
                }

                // Archive before removing any queued operation or local sale.
                val archive = JsonObject().apply {
                    addProperty(
                        "resolvedAt",
                        System.currentTimeMillis()
                    )
                    addProperty("choice", "USE_CLOUD")
                    addProperty("entityKey", approved.entityKey)
                    add(
                        "operations",
                        gson.toJsonTree(approved.operations)
                    )
                    add("local", approved.localSnapshot)
                    add(
                        "cloud",
                        gson.toJsonTree(approved.cloudRecord)
                    )
                    add(
                        "cloudSales",
                        gson.toJsonTree(approved.cloudSales)
                    )
                }

                db.syncDao().saveMetadata(
                    SyncMetadataEntity(
                        key = "conflict_archive_${UUID.randomUUID()}",
                        value = archive.toString()
                    )
                )

                for (
                operation in approved.operations.filter {
                    it.type == "SALE"
                }
                ) {
                    val previousDate = db.checkoutDao()
                        .getSale(operation.operationId)
                        ?.date

                    db.syncDao().removeConflictSaleLine(
                        operation.operationId
                    )
                    db.syncDao().removeConflictSale(
                        operation.operationId
                    )

                    // A sale that reached the cloud is not discarded.
                    approved.cloudSales[operation.operationId]?.let {
                        restoreCloudSale(db, it, previousDate)
                    }
                }

                applyCloudRecord(db, approved)

                for (operation in approved.operations) {
                    db.syncDao().acknowledge(operation.id)
                }

                db.syncDao().saveProductVersion(
                    CloudProductVersionEntity(
                        productId = approved.entityKey,
                        version = approved.cloudRecord
                            ?.get("version")
                            ?.asLong
                            ?: 0L
                    )
                )
            }
        }

        SyncQueue.schedule(approved.accountId)
    }

    private suspend fun loadReview(
        db: AppDatabase,
        accountId: String
    ): ConflictReview {
        SyncQueue.requireAccount(accountId)

        val head = db.syncDao().oldestOperation()

        check(head != null && head.status == "BLOCKED") {
            "There is no blocked change to review."
        }

        val collection = when (head.type) {
            "PRODUCT_PUT", "PRODUCT_DELETE", "SALE" -> "products"
            "CUSTOMER_PUT" -> "customers"
            "EXPENSE_PUT" -> "expenses"
            else -> error("This operation needs technical review.")
        }

        val recordId = when (collection) {
            "customers" -> head.entityId.removePrefix("customer:")
            "expenses" -> head.entityId.removePrefix("expense:")
            else -> head.entityId
        }

        val (operations, local) = db.withTransaction {
            val queuedOperations = db.syncDao()
                .getOperations()
                .filter { it.entityId == head.entityId }

            val localSnapshot = snapshot(
                db,
                collection,
                recordId,
                queuedOperations
            )

            queuedOperations to localSnapshot
        }

        val user = checkNotNull(
            FirebaseAuth.getInstance().currentUser
        )

        val token = user.getIdToken(false).await().token

        check(!token.isNullOrBlank()) {
            "Sign in again before reviewing cloud data."
        }

        SyncQueue.requireAccount(accountId)

        val authorization = "Bearer $token"

        val cloud = findRecords(
            accountId,
            authorization,
            collection,
            setOf(recordId)
        )[recordId]

        val saleIds = operations
            .filter { it.type == "SALE" }
            .map { it.operationId }
            .toSet()

        val cloudSales = if (saleIds.isEmpty()) {
            emptyMap()
        } else {
            findRecords(
                accountId,
                authorization,
                "sales",
                saleIds
            )
        }

        return ConflictReview(
            accountId = accountId,
            entityKey = head.entityId,
            collection = collection,
            recordId = recordId,
            operations = operations,
            localSnapshot = local,
            cloudRecord = cloud,
            cloudSales = cloudSales
        )
    }

    private suspend fun findRecords(
        accountId: String,
        authorization: String,
        collection: String,
        ids: Set<String>
    ): Map<String, JsonObject> {
        val found = mutableMapOf<String, JsonObject>()
        val visited = mutableSetOf<String>()
        var cursor: String? = null

        do {
            SyncQueue.requireAccount(accountId)

            val page = api.page(
                authorization,
                collection,
                cursor
            )

            SyncQueue.requireAccount(accountId)

            for (element in page.getAsJsonArray("items")) {
                val record = element.asJsonObject
                val id = record.get("id").asString

                if (id in ids) {
                    found[id] = record
                }
            }

            val next = page.get("nextCursor")

            cursor = if (next == null || next.isJsonNull) {
                null
            } else {
                next.asString
            }

            if (cursor != null) {
                check(visited.add(cursor)) {
                    "Repeated cloud page cursor."
                }
            }
        } while (cursor != null && found.size < ids.size)

        return found
    }

    private suspend fun snapshot(
        db: AppDatabase,
        collection: String,
        id: String,
        operations: List<SyncOperationEntity>
    ): JsonObject {
        val record: Any? = when (collection) {
            "products" -> db.productDao().getProduct(id)

            "customers" -> db.businessDao()
                .getCustomers()
                .firstOrNull { it.id == id }

            "expenses" -> db.businessDao()
                .getExpenses()
                .firstOrNull { it.id == id }

            else -> error("Unsupported collection.")
        }

        val saleSnapshots = operations
            .filter { it.type == "SALE" }
            .map {
                JsonObject().apply {
                    add(
                        "sale",
                        gson.toJsonTree(
                            db.checkoutDao()
                                .getSale(it.operationId)
                        )
                    )
                    add(
                        "line",
                        gson.toJsonTree(
                            db.checkoutDao()
                                .getSaleLine(it.operationId)
                        )
                    )
                }
            }

        return JsonObject().apply {
            add("record", gson.toJsonTree(record))
            add("sales", gson.toJsonTree(saleSnapshots))
        }
    }

    private suspend fun applyCloudRecord(
        db: AppDatabase,
        review: ConflictReview
    ) {
        val record = review.cloudRecord
        val id = review.recordId

        when (review.collection) {
            "products" -> {
                if (
                    record == null ||
                    record.get("deleted")?.asBoolean == true
                ) {
                    db.productDao().delete(id)
                } else {
                    db.productDao().upsert(
                        ProductEntity(
                            id = id,
                            name = record.get("name").asString,
                            category = record.get("category").asString,
                            costPrice =
                                record.get("costPriceCents").asLong / 100.0,
                            sellingPrice =
                                record.get("sellingPriceCents").asLong / 100.0,
                            quantity = record.get("quantity").asInt,
                            minimumStock =
                                record.get("minimumStock").asInt,
                            barcode = record.get("barcode").asString
                        )
                    )
                }
            }

            "customers" -> {
                if (record == null) {
                    db.syncDao().removeConflictCustomer(id)
                } else {
                    db.businessDao().saveCustomer(
                        CustomerEntity(
                            id = id,
                            name = record.get("name").asString,
                            phone = record.get("phone").asString,
                            email = record.get("email").asString
                        )
                    )
                }
            }

            "expenses" -> {
                if (record == null) {
                    db.syncDao().removeConflictExpense(id)
                } else {
                    db.businessDao().saveExpense(
                        ExpenseEntity(
                            id = id,
                            description =
                                record.get("description").asString,
                            category = record.get("category").asString,
                            amount =
                                record.get("amountCents").asLong / 100.0,
                            date = record.get("date").asString,
                            synced = true
                        )
                    )
                }
            }
        }
    }

    private suspend fun restoreCloudSale(
        db: AppDatabase,
        sale: JsonObject,
        previousDate: String?
    ) {
        val timestamp = sale.get("createdAt")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject

        val seconds = (
                timestamp?.get("seconds")
                    ?: timestamp?.get("_seconds")
                )?.asLong

        val date = if (seconds != null) {
            SimpleDateFormat(
                "yyyy-MM-dd HH:mm",
                Locale.US
            ).format(Date(seconds * 1000))
        } else {
            checkNotNull(previousDate) {
                "Cloud sale date could not be read."
            }
        }

        val customer = sale.get("customerName")
            ?.takeUnless { it.isJsonNull }
            ?.asString

        val id = sale.get("id").asString

        db.businessDao().saveSale(
            SaleEntity(
                id = id,
                customerName = customer,
                total = sale.get("totalCents").asLong / 100.0,
                date = date,
                synced = true
            )
        )

        db.checkoutDao().insertSaleLine(
            SaleLineEntity(
                saleId = id,
                productId = sale.get("productId").asString,
                productName = sale.get("productName").asString,
                quantity = sale.get("quantity").asInt,
                unitPriceCents = sale.get("unitPriceCents").asLong,
                unitCostCents = sale.get("unitCostCents").asLong,
                totalCents = sale.get("totalCents").asLong
            )
        )
    }
}