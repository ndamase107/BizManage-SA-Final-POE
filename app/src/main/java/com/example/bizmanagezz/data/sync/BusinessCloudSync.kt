package com.example.bizmanagezz.data.sync

import androidx.room.withTransaction
import com.example.bizmanagezz.BuildConfig
import com.example.bizmanagezz.data.local.AppDatabase
import com.example.bizmanagezz.data.local.CloudProductVersionEntity
import com.example.bizmanagezz.data.local.CustomerEntity
import com.example.bizmanagezz.data.local.ExpenseEntity
import com.example.bizmanagezz.data.local.SyncMetadataEntity
import com.example.bizmanagezz.data.local.SyncOperationEntity
import com.example.bizmanagezz.domain.SaleRules
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.UUID
import java.util.concurrent.TimeUnit

data class CloudCustomerRecord(
    val id: String,
    val name: String,
    val phone: String,
    val email: String,
    val version: Long
)

data class CloudExpenseRecord(
    val id: String,
    val description: String,
    val category: String,
    val amountCents: Long,
    val date: String,
    val version: Long
)

data class BusinessCloudPage<T>(
    val items: List<T>,
    val nextCursor: String?
)

interface BusinessCloudApi {

    @GET("customers")
    suspend fun getCustomers(
        @Header("Authorization") authorization: String,
        @Query("after") after: String? = null
    ): BusinessCloudPage<CloudCustomerRecord>

    @PUT("customers/{id}")
    suspend fun saveCustomer(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Header("Idempotency-Key") operationId: String,
        @Body request: JsonObject
    ): CloudCustomerRecord

    @GET("expenses")
    suspend fun getExpenses(
        @Header("Authorization") authorization: String,
        @Query("after") after: String? = null
    ): BusinessCloudPage<CloudExpenseRecord>

    @PUT("expenses/{id}")
    suspend fun saveExpense(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Header("Idempotency-Key") operationId: String,
        @Body request: JsonObject
    ): CloudExpenseRecord
}

object BusinessCloudSync {

    private val gson = Gson()

    private val api: BusinessCloudApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(
                GsonConverterFactory.create()
            )
            .build()
            .create(BusinessCloudApi::class.java)
    }

    fun customerKey(id: String): String = "customer:$id"

    fun expenseKey(id: String): String = "expense:$id"

    /**
     * Called inside SyncQueue's initialization transaction.
     * Existing customer and expense records are queued once.
     */
    suspend fun bootstrap(db: AppDatabase) {
        if (
            db.syncDao().getMetadata(
                "business_bootstrap"
            ) == "ready"
        ) {
            return
        }

        for (customer in db.businessDao().getCustomers()) {
            enqueueCustomer(db, customer)
        }

        for (expense in db.businessDao().getExpenses()) {
            enqueueExpense(db, expense)
        }

        db.syncDao().saveMetadata(
            SyncMetadataEntity(
                key = "business_bootstrap",
                value = "ready"
            )
        )
    }

    suspend fun enqueueCustomer(
        db: AppDatabase,
        customer: CustomerEntity
    ) {
        val request = JsonObject().apply {
            addProperty("id", customer.id)
            addProperty("name", customer.name)
            addProperty("phone", customer.phone)
            addProperty("email", customer.email)
            addProperty("expectedVersion", 0)
        }

        enqueue(
            db = db,
            type = "CUSTOMER_PUT",
            key = customerKey(customer.id),
            request = request
        )
    }

    suspend fun enqueueExpense(
        db: AppDatabase,
        expense: ExpenseEntity
    ) {
        val request = JsonObject().apply {
            addProperty("id", expense.id)
            addProperty("description", expense.description)
            addProperty("category", expense.category)
            addProperty(
                "amountCents",
                SaleRules.priceToCents(expense.amount)
            )
            addProperty("date", expense.date)
            addProperty("expectedVersion", 0)
        }

        enqueue(
            db = db,
            type = "EXPENSE_PUT",
            key = expenseKey(expense.id),
            request = request
        )
    }

    private suspend fun enqueue(
        db: AppDatabase,
        type: String,
        key: String,
        request: JsonObject
    ) {
        val payload = JsonObject().apply {
            addProperty("prepared", false)
            add("request", request)
        }

        db.syncDao().enqueue(
            SyncOperationEntity(
                operationId = UUID.randomUUID().toString(),
                type = type,
                entityId = key,
                payload = gson.toJson(payload)
            )
        )
    }

    suspend fun push(
        db: AppDatabase,
        accountId: String,
        authorization: String,
        operation: SyncOperationEntity,
        payload: JsonObject
    ) {
        SyncQueue.requireAccount(accountId)

        val request = payload.getAsJsonObject("request")
        val id = request.get("id").asString

        when (operation.type) {
            "CUSTOMER_PUT" -> {
                require(operation.entityId == customerKey(id))

                val saved = api.saveCustomer(
                    authorization = authorization,
                    id = id,
                    operationId = operation.operationId,
                    request = request
                )

                SyncQueue.requireAccount(accountId)
                require(saved.id == id)

                db.withTransaction {
                    db.syncDao().saveProductVersion(
                        CloudProductVersionEntity(
                            productId = operation.entityId,
                            version = saved.version
                        )
                    )

                    db.syncDao().acknowledge(operation.id)
                }
            }

            "EXPENSE_PUT" -> {
                require(operation.entityId == expenseKey(id))

                val saved = api.saveExpense(
                    authorization = authorization,
                    id = id,
                    operationId = operation.operationId,
                    request = request
                )

                SyncQueue.requireAccount(accountId)
                require(saved.id == id)

                db.withTransaction {
                    db.syncDao().saveProductVersion(
                        CloudProductVersionEntity(
                            productId = operation.entityId,
                            version = saved.version
                        )
                    )

                    db.syncDao().acknowledge(operation.id)

                    // Do not mark a newer local edit as synchronized.
                    if (
                        !db.syncDao().hasOutstandingOperation(
                            operation.entityId
                        )
                    ) {
                        val current = db.businessDao()
                            .getExpenses()
                            .firstOrNull { it.id == id }

                        if (current != null) {
                            db.businessDao().saveExpense(
                                current.copy(synced = true)
                            )
                        }
                    }
                }
            }

            else -> error("Unsupported business operation")
        }
    }

    suspend fun pull(
        db: AppDatabase,
        accountId: String,
        authorization: String
    ) {
        pullCustomers(db, accountId, authorization)
        pullExpenses(db, accountId, authorization)
    }

    private suspend fun pullCustomers(
        db: AppDatabase,
        accountId: String,
        authorization: String
    ) {
        var cursor: String? = null
        val visited = mutableSetOf<String>()

        do {
            SyncQueue.requireAccount(accountId)

            val page = api.getCustomers(
                authorization,
                cursor
            )

            SyncQueue.requireAccount(accountId)

            db.withTransaction {
                for (customer in page.items) {
                    val key = customerKey(customer.id)

                    if (
                        db.syncDao().hasOutstandingOperation(key)
                    ) {
                        continue
                    }

                    db.businessDao().saveCustomer(
                        CustomerEntity(
                            id = customer.id,
                            name = customer.name,
                            phone = customer.phone,
                            email = customer.email
                        )
                    )

                    db.syncDao().saveProductVersion(
                        CloudProductVersionEntity(
                            productId = key,
                            version = customer.version
                        )
                    )
                }
            }

            cursor = page.nextCursor

            if (cursor != null) {
                check(visited.add(cursor)) {
                    "Repeated customer page cursor"
                }
            }
        } while (cursor != null)
    }

    private suspend fun pullExpenses(
        db: AppDatabase,
        accountId: String,
        authorization: String
    ) {
        var cursor: String? = null
        val visited = mutableSetOf<String>()

        do {
            SyncQueue.requireAccount(accountId)

            val page = api.getExpenses(
                authorization,
                cursor
            )

            SyncQueue.requireAccount(accountId)

            db.withTransaction {
                for (expense in page.items) {
                    val key = expenseKey(expense.id)

                    if (
                        db.syncDao().hasOutstandingOperation(key)
                    ) {
                        continue
                    }

                    db.businessDao().saveExpense(
                        ExpenseEntity(
                            id = expense.id,
                            description = expense.description,
                            category = expense.category,
                            amount = expense.amountCents / 100.0,
                            date = expense.date,
                            synced = true
                        )
                    )

                    db.syncDao().saveProductVersion(
                        CloudProductVersionEntity(
                            productId = key,
                            version = expense.version
                        )
                    )
                }
            }

            cursor = page.nextCursor

            if (cursor != null) {
                check(visited.add(cursor)) {
                    "Repeated expense page cursor"
                }
            }
        } while (cursor != null)
    }
}