package com.example.bizmanagezz.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bizmanagezz.data.model.Product
import com.example.bizmanagezz.data.repository.BizRepository

@Composable
fun ProductDetailsScreen(productId: String, onBack: () -> Unit) {
    val repo = remember { BizRepository() }
    var product by remember { mutableStateOf<Product?>(null) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(productId) {
        product = repo.getProducts().firstOrNull { it.id == productId }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Product Details") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { padding ->
        ScreenColumn {
            Spacer(Modifier.height(padding.calculateTopPadding()))
            val p = product
            if (p == null) {
                EmptyState("Product not found")
            } else {
                Text(p.name, style = MaterialTheme.typography.headlineSmall)
                Text("Category: ${p.category}")
                Text("Cost price: R${"%.2f".format(p.costPrice)}")
                Text("Selling price: R${"%.2f".format(p.sellingPrice)}")
                Text("Quantity: ${p.quantity}")
                Text("Minimum stock: ${p.minimumStock}")
                Text("Barcode: ${p.barcode}")

                Button(onClick = {
                    kotlinx.coroutines.MainScope().launch {
                        repo.saveProduct(p.copy(quantity = p.quantity + 1))
                        saved = true
                    }
                }, Modifier.fillMaxWidth()) { Text("Add Stock") }

                if (saved) Text("Stock updated.")
            }
        }
    }
}
