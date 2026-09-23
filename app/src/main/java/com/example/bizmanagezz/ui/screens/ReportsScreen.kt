package com.example.bizmanagezz.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.bizmanagezz.data.model.Dashboard
import com.example.bizmanagezz.data.repository.BizRepository

@Composable
fun ReportsScreen() {
    val repo = remember { BizRepository() }
    var dashboard by remember { mutableStateOf<Dashboard?>(null) }

    LaunchedEffect(Unit) { dashboard = repo.getDashboard() }

    ScreenColumn {
        Text("Reports", style = MaterialTheme.typography.headlineMedium)
        Text("Business performance overview")

        dashboard?.let {
            StatCard("Total sales", "R${"%.2f".format(it.salesToday)}")
            StatCard("Total expenses", "R${"%.2f".format(it.expensesToday)}")
            StatCard("Estimated profit", "R${"%.2f".format(it.estimatedProfit)}")
            StatCard("Transactions", it.transactionCount.toString())
            StatCard("Low-stock products", it.lowStockCount.toString())
        }

        Text("Future report extensions can include best-selling products and sales trends.")
    }
}
