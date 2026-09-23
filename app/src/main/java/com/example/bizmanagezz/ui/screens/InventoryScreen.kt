package com.example.bizmanagezz.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.bizmanagezz.data.model.Product
import com.example.bizmanagezz.data.repository.BizRepository

@Composable
fun InventoryScreen(navController: NavHostController) {
    val repo = remember { BizRepository() }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var search by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { products = repo.getProducts() }

    val filtered = products.filter {
        it.name.contains(search, true) || it.category.contains(search, true) || it.barcode.contains(search)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Inventory", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("Search products or barcode") })
        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered) { product ->
                Card(
                    Modifier.fillMaxWidth(),
                    onClick = { navController.navigate(AppRoutes.product(product.id)) }
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(product.name, style = MaterialTheme.typography.titleMedium)
                            Text(product.category)
                            Text("Barcode: ${product.barcode}", style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                            Text("Qty ${product.quantity}")
                            if (product.quantity <= product.minimumStock) {
                                Text("LOW STOCK", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
