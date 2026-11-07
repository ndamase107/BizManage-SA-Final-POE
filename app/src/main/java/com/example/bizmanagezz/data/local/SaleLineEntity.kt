package com.example.bizmanagezz.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sale_lines")
data class SaleLineEntity(
    @PrimaryKey
    val saleId: String,
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPriceCents: Long,
    val unitCostCents: Long,
    val totalCents: Long
)