package com.example.bizmanagezz.ui

object AppRoutes {
    const val LOGIN = "login"
    const val BUSINESS_SETUP = "business_setup"
    const val DASHBOARD = "dashboard"
    const val SALES = "sales"
    const val NEW_SALE = "new_sale"
    const val INVENTORY = "inventory"
    const val PRODUCT = "product/{id}"
    const val CUSTOMERS = "customers"
    const val EXPENSES = "expenses"
    const val REPORTS = "reports"
    const val NOTIFICATIONS = "notifications"
    const val SETTINGS = "settings"

    fun product(id: String) = "product/$id"
}
