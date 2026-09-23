package com.example.bizmanagezz.data.api

import com.example.bizmanagezz.data.model.*
import retrofit2.http.*

interface BizManageApi {
    @GET("businesses/{id}")
    suspend fun getBusiness(@Path("id") id: String): Business

    @PUT("businesses/{id}")
    suspend fun updateBusiness(
        @Path("id") id: String,
        @Body business: Business
    ): Business

    @GET("products")
    suspend fun getProducts(): List<Product>

    @POST("products")
    suspend fun createProduct(@Body product: Product): Product

    @PUT("products/{id}")
    suspend fun updateProduct(
        @Path("id") id: String,
        @Body product: Product
    ): Product

    @DELETE("products/{id}")
    suspend fun deleteProduct(@Path("id") id: String)

    @GET("products/low-stock")
    suspend fun getLowStockProducts(): List<Product>

    @GET("sales")
    suspend fun getSales(): List<Sale>

    @POST("sales")
    suspend fun createSale(@Body sale: Sale): Sale

    @GET("expenses")
    suspend fun getExpenses(): List<Expense>

    @POST("expenses")
    suspend fun createExpense(@Body expense: Expense): Expense

    @GET("customers")
    suspend fun getCustomers(): List<Customer>

    @POST("customers")
    suspend fun createCustomer(@Body customer: Customer): Customer

    @GET("reports/dashboard")
    suspend fun getDashboard(): Dashboard

    @POST("sync")
    suspend fun synchronise(@Body records: List<Any>)

    @POST("notifications/register")
    suspend fun registerNotificationDevice(@Body token: Map<String, String>)
}
