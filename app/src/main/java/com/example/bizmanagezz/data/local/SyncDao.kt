package com.example.bizmanagezz.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {

    @Query(
        """
        SELECT * FROM sync_outbox
        ORDER BY id ASC
        LIMIT 1
        """
    )
    suspend fun oldestOperation(): SyncOperationEntity?

    @Query("SELECT * FROM sync_outbox ORDER BY id ASC")
    suspend fun getOperations(): List<SyncOperationEntity>

    @Query("SELECT * FROM sync_outbox ORDER BY id ASC")
    fun observeOperations(): Flow<List<SyncOperationEntity>>

    @Query("SELECT COUNT(*) FROM sync_outbox")
    fun observeOutstandingCount(): Flow<Int>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM sync_outbox
            WHERE entityId = :entityId
        )
        """
    )
    suspend fun hasOutstandingOperation(entityId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun enqueue(operation: SyncOperationEntity): Long

    // Persist the exact request before sending it.
    // A retry must use the same expected version and request body.
    @Query(
        """
        UPDATE sync_outbox
        SET payload = :payload
        WHERE id = :id
        """
    )
    suspend fun updatePayload(id: Long, payload: String)

    @Query("DELETE FROM sync_outbox WHERE id = :id")
    suspend fun acknowledge(id: Long)

    @Query(
        """
        UPDATE sync_outbox
        SET status = 'BLOCKED', lastError = :message
        WHERE id = :id
        """
    )
    suspend fun markBlocked(id: Long, message: String)

    @Query(
        """
        UPDATE sync_outbox
        SET status = 'PENDING', lastError = :message
        WHERE id = :id
        """
    )
    suspend fun recordRetry(id: Long, message: String?)

    @Query(
        """
        SELECT * FROM cloud_product_versions
        WHERE productId = :productId
        LIMIT 1
        """
    )
    suspend fun getProductVersion(
        productId: String
    ): CloudProductVersionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProductVersion(
        version: CloudProductVersionEntity
    )

    @Query(
        """
        SELECT value FROM sync_metadata
        WHERE `key` = :key
        LIMIT 1
        """
    )
    suspend fun getMetadata(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMetadata(metadata: SyncMetadataEntity)

    @Query("UPDATE sales SET synced = 1 WHERE id = :saleId")
    suspend fun markSaleSynced(saleId: String)

    @Query("DELETE FROM sale_lines WHERE saleId = :saleId")
    suspend fun removeConflictSaleLine(saleId: String)

    @Query("DELETE FROM sales WHERE id = :saleId")
    suspend fun removeConflictSale(saleId: String)

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun removeConflictCustomer(id: String)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun removeConflictExpense(id: String)
}