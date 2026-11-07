package com.example.bizmanagezz.data.repository

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.example.bizmanagezz.data.api.BizManageApi
import com.example.bizmanagezz.data.local.AccountStorage
import com.example.bizmanagezz.data.local.AppDatabase
import com.example.bizmanagezz.data.local.CustomerEntity
import com.example.bizmanagezz.data.local.ExpenseEntity
import com.example.bizmanagezz.data.local.ProductEntity
import com.example.bizmanagezz.data.local.SaleEntity
import com.example.bizmanagezz.data.model.Customer
import com.example.bizmanagezz.data.model.Dashboard
import com.example.bizmanagezz.data.model.Expense
import com.example.bizmanagezz.data.model.Product
import com.example.bizmanagezz.data.model.Sale
import com.example.bizmanagezz.data.sync.SyncQueue
import com.example.bizmanagezz.data.sync.BusinessCloudSync
import com.example.bizmanagezz.domain.SaleRules
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BizRepository(
    @Suppress("UNUSED_PARAMETER")
    private val api: BizManageApi? = null
) {

    private val accountId = AccountStorage.currentUid()

    private val db: AppDatabase
        get() {
            val context = checkNotNull(applicationContext) {
                "Initialize BizRepository before using it."
            }

            return AppDatabase.getInstance(
                context = context,
                accountId = accountId
            )
        }

    private suspend fun readyDatabase(): AppDatabase {
        SyncQueue.requireAccount(accountId)

        val database = db

        SyncQueue.start(database, accountId)

        return database
    }

    suspend fun getProducts(): List<Product> {
        return readyDatabase()
            .productDao()
            .getProducts()
            .map { entity ->
                Product(
                    id = entity.id,
                    name = entity.name,
                    category = entity.category,
                    costPrice = entity.costPrice,
                    sellingPrice = entity.sellingPrice,
                    quantity = entity.quantity,
                    minimumStock = entity.minimumStock,
                    barcode = entity.barcode
                )
            }
    }

    suspend fun saveProduct(product: Product) {
        requireValidId(product.id)

        require(
            product.name.isNotBlank() &&
                    product.name.trim().length <= 200
        ) {
            "Enter a product name of 1â€“200 characters."
        }

        require(
            product.category.isNotBlank() &&
                    product.category.trim().length <= 200
        ) {
            "Enter a category of 1â€“200 characters."
        }

        require(product.barcode.trim().length <= 100) {
            "Barcode must be at most 100 characters."
        }

        val costCents = SaleRules.priceToCents(
            product.costPrice
        )

        val sellingCents = SaleRules.priceToCents(
            product.sellingPrice
        )

        require(costCents in 0L..1_000_000_000L) {
            "Cost price is outside the supported range."
        }

        require(sellingCents in 1L..1_000_000_000L) {
            "Selling price must be at least R0.01."
        }

        require(product.quantity in 0..1_000_000) {
            "Quantity must be between 0 and 1,000,000."
        }

        require(product.minimumStock in 0..1_000_000) {
            "Minimum stock must be between 0 and 1,000,000."
        }

        val database = readyDatabase()

        val entity = ProductEntity(
            id = product.id,
            name = product.name.trim(),
            category = product.category.trim(),
            costPrice = product.costPrice,
            sellingPrice = product.sellingPrice,
            quantity = product.quantity,
            minimumStock = product.minimumStock,
            barcode = product.barcode.trim()
        )

        database.withTransaction {
            SyncQueue.requireAccount(accountId)

            database.productDao().upsert(entity)

            // Save the outgoing operation in the same transaction.
            SyncQueue.enqueueProduct(database, entity)
        }

        SyncQueue.schedule(accountId)

        Log.d(TAG, "Product saved locally and queued")
    }

    suspend fun deleteProduct(id: String) {
        requireValidId(id)

        val database = readyDatabase()

        database.withTransaction {
            SyncQueue.requireAccount(accountId)

            if (database.productDao().getProduct(id) != null) {
                database.productDao().delete(id)

                SyncQueue.enqueueDeletion(
                    database,
                    id
                )
            }
        }

        SyncQueue.schedule(accountId)

        Log.d(
            TAG,
            "Product deletion saved locally and queued"
        )
    }

    suspend fun getCustomers(): List<Customer> {
        return readyDatabase()
            .businessDao()
            .getCustomers()
            .map { entity ->
                Customer(
                    id = entity.id,
                    name = entity.name,
                    phone = entity.phone,
                    email = entity.email
                )
            }
    }

    suspend fun saveCustomer(customer: Customer) {
        requireValidId(customer.id)

        require(
            customer.name.isNotBlank() &&
                customer.name.trim().length <= 200
        ) {
            "Enter a customer name of 1–200 characters."
        }

        require(customer.phone.trim().length <= 100) {
            "Phone must be at most 100 characters."
        }

        require(customer.email.trim().length <= 200) {
            "Email must be at most 200 characters."
        }

        val database = readyDatabase()

        val entity = CustomerEntity(
            id = customer.id,
            name = customer.name.trim(),
            phone = customer.phone.trim(),
            email = customer.email.trim()
        )

        database.withTransaction {
            SyncQueue.requireAccount(accountId)

            database.businessDao().saveCustomer(entity)

            BusinessCloudSync.enqueueCustomer(
                database,
                entity
            )
        }

        SyncQueue.schedule(accountId)
        Log.d(TAG, "Customer saved locally and queued")
    }

    suspend fun getExpenses(): List<Expense> {
        return readyDatabase()
            .businessDao()
            .getExpenses()
            .map { entity ->
                Expense(
                    id = entity.id,
                    description = entity.description,
                    category = entity.category,
                    amount = entity.amount,
                    date = entity.date,
                    synced = entity.synced
                )
            }
    }

    suspend fun saveExpense(expense: Expense) {
        requireValidId(expense.id)

        require(
            expense.description.isNotBlank() &&
                expense.description.trim().length <= 200
        ) {
            "Enter a description of 1–200 characters."
        }

        require(
            expense.category.isNotBlank() &&
                expense.category.trim().length <= 200
        ) {
            "Enter a category of 1–200 characters."
        }

        val amountCents = SaleRules.priceToCents(
            expense.amount
        )

        require(amountCents in 1L..1_000_000_000L) {
            "Enter an expense amount of at least R0.01."
        }

        val dateFormat = SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.US
        ).apply {
            isLenient = false
        }

        val parsedDate = runCatching {
            dateFormat.parse(expense.date)
        }.getOrNull()

        require(
            parsedDate != null &&
                dateFormat.format(parsedDate) == expense.date
        ) {
            "Expense date must be a valid yyyy-MM-dd date."
        }

        val database = readyDatabase()

        val entity = ExpenseEntity(
            id = expense.id,
            description = expense.description.trim(),
            category = expense.category.trim(),
            amount = expense.amount,
            date = expense.date,
            synced = false
        )

        database.withTransaction {
            SyncQueue.requireAccount(accountId)

            database.businessDao().saveExpense(entity)

            BusinessCloudSync.enqueueExpense(
                database,
                entity
            )
        }

        SyncQueue.schedule(accountId)
        Log.d(TAG, "Expense saved locally and queued")
    }

    suspend fun getSales(): List<Sale> {
        return readyDatabase()
            .businessDao()
            .getSales()
            .map { entity ->
                Sale(
                    id = entity.id,
                    customerName = entity.customerName,
                    total = entity.total,
                    date = entity.date,
                    synced = entity.synced
                )
            }
    }

    /**
     * Compatibility method for older amount-only callers.
     * Product-based sales must use SaleCheckout.
     */
    suspend fun saveSale(sale: Sale) {
        require(sale.id.isNotBlank()) {
            "Sale ID is required."
        }

        require(
            sale.total.isFinite() &&
                    sale.total > 0
        ) {
            "Sale total must be greater than zero."
        }

        require(sale.date.isNotBlank()) {
            "Sale date is required."
        }

        val database = readyDatabase()

        SyncQueue.requireAccount(accountId)

        database.businessDao().saveSale(
            SaleEntity(
                id = sale.id,
                customerName = sale.customerName
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() },
                total = sale.total,
                date = sale.date,
                synced = false
            )
        )

        Log.d(
            TAG,
            "Amount-only sale saved locally"
        )
    }

    suspend fun getDashboard(): Dashboard {
        val database = readyDatabase()

        return database.withTransaction {
            val today = SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
            ).format(Date())

            val sales = database
                .businessDao()
                .getSales()

            val expenses = database
                .businessDao()
                .getExpenses()

            val products = database
                .productDao()
                .getProducts()

            val todaySales = sales.filter {
                it.date.take(10) == today
            }

            val todayExpenses = expenses.filter {
                it.date.take(10) == today
            }

            val salesTotal = todaySales.fold(
                BigDecimal.ZERO
            ) { sum, sale ->
                sum.add(
                    BigDecimal.valueOf(sale.total)
                )
            }

            val expenseTotal = todayExpenses.fold(
                BigDecimal.ZERO
            ) { sum, expense ->
                sum.add(
                    BigDecimal.valueOf(expense.amount)
                )
            }

            Dashboard(
                salesToday = salesTotal.toDouble(),

                expensesToday = expenseTotal.toDouble(),

                // This is sales less expenses, excluding stock costs.
                estimatedProfit = salesTotal
                    .subtract(expenseTotal)
                    .toDouble(),

                transactionCount = todaySales.size,

                lowStockCount = products.count {
                    it.quantity <= it.minimumStock
                },

                recentActivity = sales.take(3).map {
                    String.format(
                        Locale.US,
                        "Sale: R%.2f",
                        it.total
                    )
                }
            )
        }
    }

    private fun requireValidId(id: String) {
        require(
            id.matches(
                Regex("[a-zA-Z0-9_-]{1,128}")
            )
        ) {
            "Record ID contains unsupported characters."
        }
    }

    companion object {

        private const val TAG = "BizRepository"

        @Volatile
        private var applicationContext: Context? = null

        fun initialize(context: Context) {
            applicationContext = context.applicationContext

            SyncQueue.initialize(context)
        }
    }
}