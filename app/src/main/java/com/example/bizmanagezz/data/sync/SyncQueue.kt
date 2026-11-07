package com.example.bizmanagezz.data.sync

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.example.bizmanagezz.data.api.CloudProductWrite
import com.example.bizmanagezz.data.api.CloudSaleWrite
import com.example.bizmanagezz.data.local.AppDatabase
import com.example.bizmanagezz.data.local.ProductEntity
import com.example.bizmanagezz.data.local.SyncMetadataEntity
import com.example.bizmanagezz.data.local.SyncOperationEntity
import com.example.bizmanagezz.domain.SaleRules
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object SyncQueue {

    private val gson = Gson()

    private val startedAccounts =
        ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    fun requireAccount(accountId: String) {
        check(
            FirebaseAuth.getInstance().currentUser?.uid == accountId
        ) {
            "Your account changed. Reopen this screen."
        }
    }

    /**
     * Queue current inventory once, before any new stock mutation.
     * Historical sales are not replayed against this stock baseline.
     */
    suspend fun start(
        db: AppDatabase,
        accountId: String
    ) {
        requireAccount(accountId)

        db.withTransaction {
            requireAccount(accountId)

            BusinessCloudSync.bootstrap(db)

            if (
                db.syncDao().getMetadata(
                    "inventory_bootstrap"
                ) != "ready"
            ) {
                for (product in db.productDao().getProducts()) {
                    enqueueProduct(db, product)
                }

                db.syncDao().saveMetadata(
                    SyncMetadataEntity(
                        key = "inventory_bootstrap",
                        value = "ready"
                    )
                )
            }
        }

        if (startedAccounts.add(accountId)) {
            schedule(accountId)
        }
    }

    /**
     * Call inside the same Room transaction as the product write.
     */
    suspend fun enqueueProduct(
        db: AppDatabase,
        product: ProductEntity
    ) {
        val request = CloudProductWrite(
            name = product.name,
            category = product.category,
            costPriceCents = SaleRules.priceToCents(
                product.costPrice
            ),
            sellingPriceCents = SaleRules.priceToCents(
                product.sellingPrice
            ),
            quantity = product.quantity,
            minimumStock = product.minimumStock,
            barcode = product.barcode,
            expectedVersion = 0
        )

        db.syncDao().enqueue(
            SyncOperationEntity(
                operationId = UUID.randomUUID().toString(),
                type = "PRODUCT_PUT",
                entityId = product.id,
                payload = envelope(request)
            )
        )
    }

    /**
     * Call inside the same Room transaction as the local deletion.
     */
    suspend fun enqueueDeletion(
        db: AppDatabase,
        productId: String
    ) {
        val request = JsonObject().apply {
            addProperty("expectedVersion", 0)
        }

        db.syncDao().enqueue(
            SyncOperationEntity(
                operationId = UUID.randomUUID().toString(),
                type = "PRODUCT_DELETE",
                entityId = productId,
                payload = envelope(request)
            )
        )
    }

    /**
     * Call inside the checkout transaction after the sale is inserted.
     */
    suspend fun enqueueSale(
        db: AppDatabase,
        sale: CloudSaleWrite
    ) {
        db.syncDao().enqueue(
            SyncOperationEntity(
                operationId = sale.id,
                type = "SALE",
                entityId = sale.productId,
                payload = envelope(sale)
            )
        )
    }

    fun schedule(accountId: String) {
        val context = checkNotNull(applicationContext) {
            "Initialize SyncQueue before using it."
        }

        try {
            SyncScheduler.schedule(context, accountId)
        } catch (error: Exception) {
            // The database operation remains queued if scheduling fails.
            startedAccounts.remove(accountId)

            Log.w(
                "SyncQueue",
                "Scheduling deferred: ${error.javaClass.simpleName}"
            )
        }
    }

    private fun envelope(request: Any): String {
        return JsonObject().apply {
            addProperty("prepared", false)
            add("request", gson.toJsonTree(request))
        }.toString()
    }
}