package com.example.bizmanagezz.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.bizmanagezz.data.model.Sale
import com.example.bizmanagezz.data.repository.BizRepository

@Composable
fun SalesScreen(navController: NavHostController) {
    val repo = remember { BizRepository() }
    var loadError by remember { mutableStateOf<String?>(null) }
    var sales by remember { mutableStateOf<List<Sale>>(emptyList()) }

    ObserveLocalChanges(
        "sales",
        onError = {
            loadError = "Could not refresh sales."
        }
    ) {
        sales = repo.getSales()
        loadError = null
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Sales", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = { navController.navigate("new_sale") }) { Text("New Sale") }
        }
        loadError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sales) { sale ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(sale.customerName ?: "Walk-in customer", style = MaterialTheme.typography.titleMedium)
                        Text("R${"%.2f".format(sale.total)}")
                        Text(sale.date, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
