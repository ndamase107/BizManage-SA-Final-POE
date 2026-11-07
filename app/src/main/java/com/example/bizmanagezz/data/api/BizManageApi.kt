package com.example.bizmanagezz.data.api

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

data class CloudProduct(
    val id: String,
    val name: String,
    val category: String,
    val costPriceCents: Long,
    val sellingPriceCents: Long,
    val quantity: Int,
    val minimumStock: Int,
    val barcode: String,
    val version: Long,
    val deleted: Boolean
)

data class CloudProductsPage(
    val items: List<CloudProduct>,
    val nextCursor: String?
)

data class CloudProductWrite(
    val name: String,
    val category: String,
    val costPriceCents: Long,
    val sellingPriceCents: Long,
    val quantity: Int,
    val minimumStock: Int,
    val barcode: String,
    val expectedVersion: Long
)

data class CloudSaleWrite(
    val id: String,
    val productId: String,
    val quantity: Int,
    val expectedPriceCents: Long,
    val customerName: String?
)

data class CloudSaleResult(
    val id: String,
    val totalCents: Long,
    val replayed: Boolean
)

interface BizManageApi {

    @GET("products")
    suspend fun getProducts(
        @Header("Authorization") authorization: String,
        @Query("after") after: String? = null
    ): CloudProductsPage

    @PUT("products/{id}")
    suspend fun saveProduct(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Body product: CloudProductWrite,
        @Header("Idempotency-Key") operationId: String? = null
    ): CloudProduct

    @DELETE("products/{id}")
    suspend fun deleteProduct(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Query("expectedVersion") expectedVersion: Long,
        @Header("Idempotency-Key") operationId: String? = null
    )

    @POST("sales")
    suspend fun createSale(
        @Header("Authorization") authorization: String,
        @Body sale: CloudSaleWrite
    ): CloudSaleResult
}