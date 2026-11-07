package com.example.bizmanagezz.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name")
    fun observeProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY name")
    suspend fun getProducts(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProduct(id: String): ProductEntity?

    @Query("SELECT COUNT(*) FROM products")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(products: List<ProductEntity>)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM products")
    suspend fun clear()
}

@Dao
interface PendingSyncDao {
    @Query("SELECT * FROM pending_sync ORDER BY createdAt, id")
    suspend fun getPending(): List<PendingSyncEntity>

    @Insert
    suspend fun insert(item: PendingSyncEntity)

    @Query("DELETE FROM pending_sync WHERE id = :id")
    suspend fun delete(id: Long)
}