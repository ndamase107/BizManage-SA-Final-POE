package com.example.bizmanagezz.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val email: String
)

@Entity(tableName = "sales")
data class SaleEntity(
    @PrimaryKey val id: String,
    val customerName: String?,
    val total: Double,
    val date: String,
    val synced: Boolean
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val description: String,
    val category: String,
    val amount: Double,
    val date: String,
    val synced: Boolean
)