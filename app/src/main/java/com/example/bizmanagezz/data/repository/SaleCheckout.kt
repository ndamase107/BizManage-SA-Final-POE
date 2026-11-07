package com.example.bizmanagezz.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.example.bizmanagezz.data.api.CloudSaleWrite
import com.example.bizmanagezz.data.local.AccountStorage
import com.example.bizmanagezz.data.local.AppDatabase
import com.example.bizmanagezz.data.local.SaleEntity
import com.example.bizmanagezz.data.local.SaleLineEntity
import com.example.bizmanagezz.data.sync.SyncQueue
import com.example.bizmanagezz.domain.SaleRules
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SaleCheckout(
    private val db: AppDatabase
) {

    private val accountId = AccountStorage.currentUid()

    suspend fun recordSale(
        operationId: String,
        productId: String,
        quantity: Int,
        customerName: String?,
        expectedPriceCents: Long
    ) {
        require(
            operationId.matches(
                Regex("[a-zA-Z0-9_-]{1,128}")
            )
        ) {
            "Sale ID is invalid."
        }

        require(
            productId.matches(
                Regex("[a-zA-Z0-9_-]{1,128}")
            )
        ) {
            "Select a valid product."
        }

        require(quantity in 1..1_000_000) {
            "Quantity must be between 1 and 1,000,000."
        }

        val cleanCustomer = customerName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        require(
            cleanCustomer == null ||
                    cleanCustomer.length <= 200
        ) {
            "Customer name must be at most 200 characters."
        }

        // Establish the inventory baseline before deducting new stock.
        SyncQueue.start(db, accountId)

        db.withTransaction {
            SyncQueue.requireAccount(accountId)

            val checkoutDao = db.checkoutDao()
            val existingSale = checkoutDao.getSale(operationId)

            if (existingSale != null) {
                val existingLine = checkoutDao.getSaleLine(
                    operationId
                )

                require(
                    existingLine != null &&
                            existingLine.productId == productId &&
                            existingLine.quantity == quantity &&
                            existingLine.unitPriceCents ==
                            expectedPriceCents &&
                            existingSale.customerName == cleanCustomer
                ) {
                    "This sale ID was already used for different details."
                }

                return@withTransaction
            }

            val product = db.productDao()
                .getProduct(productId)
                ?: throw IllegalArgumentException(
                    "This product no longer exists."
                )

            val unitPriceCents = SaleRules.priceToCents(
                product.sellingPrice
            )

            require(unitPriceCents == expectedPriceCents) {
                "The product price changed. Reload products before saving."
            }

            val unitCostCents = SaleRules.priceToCents(
                product.costPrice
            )

            val totalCents = SaleRules.totalCents(
                unitPriceCents = unitPriceCents,
                quantity = quantity,
                availableStock = product.quantity
            )

            val changedRows = checkoutDao.deductStock(
                productId = productId,
                amount = quantity
            )

            check(changedRows == 1) {
                "Stock changed. Reload products and try again."
            }

            val date = SimpleDateFormat(
                "yyyy-MM-dd HH:mm",
                Locale.US
            ).format(Date())

            checkoutDao.insertSale(
                SaleEntity(
                    id = operationId,
                    customerName = cleanCustomer,
                    total = BigDecimal.valueOf(
                        totalCents,
                        2
                    ).toDouble(),
                    date = date,
                    synced = false
                )
            )

            checkoutDao.insertSaleLine(
                SaleLineEntity(
                    saleId = operationId,
                    productId = product.id,
                    productName = product.name,
                    quantity = quantity,
                    unitPriceCents = unitPriceCents,
                    unitCostCents = unitCostCents,
                    totalCents = totalCents
                )
            )

            // Queue only the sale, not a second product stock update.
            // The server deducts this sale's quantity once.
            SyncQueue.enqueueSale(
                db,
                CloudSaleWrite(
                    id = operationId,
                    productId = product.id,
                    quantity = quantity,
                    expectedPriceCents = unitPriceCents,
                    customerName = cleanCustomer
                )
            )
        }

        SyncQueue.schedule(accountId)

        Log.d(
            "SaleCheckout",
            "Sale saved locally and queued for synchronization"
        )
    }
}