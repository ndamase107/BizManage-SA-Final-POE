package com.example.bizmanagezz.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Business(
    val id: String = "business-1",
    val name: String = "",
    val type: String = "",
    val contact: String = "",
    val address: String = ""
)

@Serializable
data class Product(
    val id: String,
    val name: String,
    val category: String,
    val costPrice: Double,
    val sellingPrice: Double,
    val quantity: Int,
    val minimumStock: Int,
    val barcode: String = ""
)

@Serializable
data class Sale(
    val id: String,
    val customerName: String?,
    val total: Double,
    val date: String,
    val synced: Boolean = true
)

@Serializable
data class Expense(
    val id: String,
    val description: String,
    val category: String,
    val amount: Double,
    val date: String,
    val synced: Boolean = true
)

@Serializable
data class Customer(
    val id: String,
    val name: String,
    val phone: String,
    val email: String = ""
)

@Serializable
data class Dashboard(
    val salesToday: Double,
    val expensesToday: Double,
    val estimatedProfit: Double,
    val transactionCount: Int,
    val lowStockCount: Int,
    val recentActivity: List<String>
)

@Serializable
data class UserSettings(
    val language: String = "English",
    val biometricEnabled: Boolean = false,
    val notificationsEnabled: Boolean = true
)
