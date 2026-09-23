package com.example.bizmanagezz.data.repository

import com.example.bizmanagezz.data.api.BizManageApi
import com.example.bizmanagezz.data.model.*
import kotlinx.coroutines.delay

class BizRepository(private val api: BizManageApi? = null) {

    private val products = mutableListOf(
        Product("1", "Bread", "Food", 10.0, 15.0, 18, 5, "600100000001"),
        Product("2", "Milk 1L", "Food", 16.0, 22.0, 7, 5, "600100000002"),
        Product("3", "T-Shirt", "Clothing", 80.0, 130.0, 3, 5, "600100000003"),
        Product("4", "Shampoo", "Beauty", 35.0, 55.0, 12, 4, "600100000004")
    )

    private val customers = mutableListOf(
        Customer("1", "Thandi Mokoena", "071 000 0001", "thandi@example.com"),
        Customer("2", "Sipho Dlamini", "072 000 0002", "sipho@example.com")
    )

    private val expenses = mutableListOf(
        Expense("1", "Shop electricity", "Utilities", 850.0, "2026-09-20"),
        Expense("2", "Transport", "Transport", 300.0, "2026-09-21")
    )

    private val sales = mutableListOf(
        Sale("1", "Thandi Mokoena", 420.0, "2026-09-21 10:20"),
        Sale("2", "Walk-in customer", 185.0, "2026-09-21 13:10")
    )

    suspend fun getProducts(): List<Product> {
        delay(120)
        return products.toList()
    }

    suspend fun saveProduct(product: Product) {
        val index = products.indexOfFirst { it.id == product.id }
        if (index >= 0) products[index] = product else products.add(product)
        api?.let { runCatching { if (index >= 0) it.updateProduct(product.id, product) else it.createProduct(product) } }
    }

    suspend fun deleteProduct(id: String) {
        products.removeAll { it.id == id }
        api?.let { runCatching { it.deleteProduct(id) } }
    }

    suspend fun getCustomers(): List<Customer> = customers.toList()

    suspend fun saveCustomer(customer: Customer) {
        val index = customers.indexOfFirst { it.id == customer.id }
        if (index >= 0) customers[index] = customer else customers.add(customer)
    }

    suspend fun getExpenses(): List<Expense> = expenses.toList()

    suspend fun saveExpense(expense: Expense) {
        expenses.add(expense)
    }

    suspend fun getSales(): List<Sale> = sales.toList()

    suspend fun saveSale(sale: Sale) {
        sales.add(0, sale)
        val productIds = products.map { it.id }.toSet()
        if (productIds.isNotEmpty()) {
            // Inventory mutation is handled by the sale workflow in a later backend-integrated phase.
        }
    }

    suspend fun getDashboard(): Dashboard {
        val salesTotal = sales.sumOf { it.total }
        val expenseTotal = expenses.sumOf { it.amount }
        return Dashboard(
            salesToday = salesTotal,
            expensesToday = expenseTotal,
            estimatedProfit = salesTotal - expenseTotal,
            transactionCount = sales.size,
            lowStockCount = products.count { it.quantity <= it.minimumStock },
            recentActivity = sales.take(3).map { "Sale: R${"%.2f".format(it.total)}" }
        )
    }
}
