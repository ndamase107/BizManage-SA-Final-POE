package com.example.bizmanagezz.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Durable outgoing operations.
 *
 * The operation ID stays the same across retries.
 * The numeric ID preserves local operation order.
 */
@Entity(
    tableName = "sync_outbox",
    indices = [
        Index(value = ["operationId"], unique = true),
        Index(value = ["entityId"])
    ]
)
data class SyncOperationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val operationId: String,
    val type: String,
    val entityId: String,
    val payload: String,
    val status: String = "PENDING",
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Last acknowledged server version for each product.
 * Kept separately from the locally edited product.
 */
@Entity(tableName = "cloud_product_versions")
data class CloudProductVersionEntity(
    @PrimaryKey
    val productId: String,
    val version: Long
)

/**
 * Persistent synchronization progress, including initial import state.
 */
@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey
    val key: String,
    val value: String
)