package com.example.bizmanagezz.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.bizmanagezz.data.model.Dashboard
import com.example.bizmanagezz.data.repository.BizRepository
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(navController: NavHostController) {
    val repo = remember { BizRepository() }
    var dashboard by remember { mutableStateOf<Dashboard?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        dashboard = repo.getDashboard()
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Good day 👋", style = MaterialTheme.typography.titleMedium)
            Text("BizManage SA", style = MaterialTheme.typography.headlineMedium)
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Sales", "R${"%.2f".format(dashboard?.salesToday ?: 0.0)}", Modifier.weight(1f))
                StatCard("Expenses", "R${"%.2f".format(dashboard?.expensesToday ?: 0.0)}", Modifier.weight(1f))
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Profit", "R${"%.2f".format(dashboard?.estimatedProfit ?: 0.0)}", Modifier.weight(1f))
                StatCard("Low stock", "${dashboard?.lowStockCount ?: 0}", Modifier.weight(1f))
            }
        }

        item {
            SectionTitle("Quick actions")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { navController.navigate("new_sale") }) { Text("New sale") }
                OutlinedButton(onClick = { navController.navigate("inventory") }) { Text("Inventory") }
            }
        }

        item { SectionTitle("Recent activity") }
        items(dashboard?.recentActivity ?: emptyList()) { Text("• $it") }

        item {
            Button(onClick = { navController.navigate("settings") }, Modifier.fillMaxWidth()) {
                Text("Open Settings")
            }
        }
    }
}
