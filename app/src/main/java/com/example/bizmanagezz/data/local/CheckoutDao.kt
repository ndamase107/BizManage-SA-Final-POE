package com.example.bizmanagezz.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface CheckoutDao {
    @Query("SELECT * FROM sales WHERE id = :id LIMIT 1")
    suspend fun getSale(id: String): SaleEntity?

    @Query("SELECT * FROM sale_lines WHERE saleId = :id LIMIT 1")
    suspend fun getSaleLine(id: String): SaleLineEntity?

    // Default conflict behaviour rejects duplicate IDs.
    @Insert
    suspend fun insertSale(sale: SaleEntity)

    @Insert
    suspend fun insertSaleLine(line: SaleLineEntity)

    @Query(
        """
        UPDATE products
        SET quantity = quantity - :amount
        WHERE id = :productId AND quantity >= :amount
        """
    )
    suspend fun deductStock(productId: String, amount: Int): Int
}