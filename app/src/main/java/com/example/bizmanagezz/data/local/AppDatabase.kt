package com.example.bizmanagezz.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProductEntity::class,
        PendingSyncEntity::class,
        CustomerEntity::class,
        SaleEntity::class,
        ExpenseEntity::class,
        SaleLineEntity::class,
        SyncOperationEntity::class,
        CloudProductVersionEntity::class,
        SyncMetadataEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao

    abstract fun pendingSyncDao(): PendingSyncDao

    abstract fun businessDao(): BusinessDao

    abstract fun checkoutDao(): CheckoutDao

    abstract fun syncDao(): SyncDao

    companion object {

        private val instances = mutableMapOf<String, AppDatabase>()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS customers (
                        id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        phone TEXT NOT NULL,
                        email TEXT NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sales (
                        id TEXT NOT NULL,
                        customerName TEXT,
                        total REAL NOT NULL,
                        date TEXT NOT NULL,
                        synced INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS expenses (
                        id TEXT NOT NULL,
                        description TEXT NOT NULL,
                        category TEXT NOT NULL,
                        amount REAL NOT NULL,
                        date TEXT NOT NULL,
                        synced INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sale_lines (
                        saleId TEXT NOT NULL,
                        productId TEXT NOT NULL,
                        productName TEXT NOT NULL,
                        quantity INTEGER NOT NULL,
                        unitPriceCents INTEGER NOT NULL,
                        unitCostCents INTEGER NOT NULL,
                        totalCents INTEGER NOT NULL,
                        PRIMARY KEY(saleId)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add sync tables without changing existing business records.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_outbox (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        operationId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        entityId TEXT NOT NULL,
                        payload TEXT NOT NULL,
                        status TEXT NOT NULL,
                        lastError TEXT,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS
                    index_sync_outbox_operationId
                    ON sync_outbox (operationId)
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    index_sync_outbox_entityId
                    ON sync_outbox (entityId)
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cloud_product_versions (
                        productId TEXT NOT NULL,
                        version INTEGER NOT NULL,
                        PRIMARY KEY(productId)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_metadata (
                        `key` TEXT NOT NULL,
                        value TEXT NOT NULL,
                        PRIMARY KEY(`key`)
                    )
                    """.trimIndent()
                )
            }
        }

        @Synchronized
        fun getInstance(
            context: Context,
            accountId: String = AccountStorage.currentUid()
        ): AppDatabase {
            val suffix = AccountStorage.suffix(
                context,
                accountId
            )

            val databaseName = "bizmanage$suffix.db"

            return instances.getOrPut(databaseName) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    databaseName
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4
                    )
                    .build()
            }
        }
    }
}