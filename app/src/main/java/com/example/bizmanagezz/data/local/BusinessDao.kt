package com.example.bizmanagezz.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BusinessDao {
    @Query("SELECT * FROM customers ORDER BY name")
    suspend fun getCustomers(): List<CustomerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCustomer(customer: CustomerEntity)

    @Query("SELECT * FROM sales ORDER BY date DESC, id")
    suspend fun getSales(): List<SaleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSale(sale: SaleEntity)

    @Query("SELECT * FROM expenses ORDER BY date DESC, id")
    suspend fun getExpenses(): List<ExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveExpense(expense: ExpenseEntity)
}