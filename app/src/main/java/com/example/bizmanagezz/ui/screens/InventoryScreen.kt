package com.example.bizmanagezz.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.bizmanagezz.data.model.Product
import com.example.bizmanagezz.data.repository.BizRepository
import com.example.bizmanagezz.ui.AppRoutes
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun InventoryScreen(navController: NavHostController) {
    val repo = remember { BizRepository() }
    val scope = rememberCoroutineScope()

    var products by remember {
        mutableStateOf<List<Product>>(emptyList())
    }
    var search by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAddProduct by remember { mutableStateOf(false) }

    ObserveLocalChanges(
        "products",
        onError = {
            error = "Could not load inventory."
            loading = false
        }
    ) {
        products = repo.getProducts()
        loading = false
    }

    val query = search.trim()
    val filtered = products.filter {
        it.name.contains(query, ignoreCase = true) ||
                it.category.contains(query, ignoreCase = true) ||
                it.barcode.contains(query)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Inventory",
            style = MaterialTheme.typography.headlineMedium
        )

        Button(
            onClick = { showAddProduct = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add Product")
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search products or barcode") },
            singleLine = true
        )

        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error
            )

            TextButton(
                enabled = !loading,
                onClick = {
                    loading = true
                    error = null

                    scope.launch {
                        try {
                            products = repo.getProducts()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            error = "Could not load inventory."
                        } finally {
                            loading = false
                        }
                    }
                }
            ) {
                Text("Retry")
            }
        }

        if (loading) {
            CircularProgressIndicator()
        } else if (filtered.isEmpty() && error == null) {
            Text(
                if (query.isEmpty()) {
                    "No products yet. Add your first product."
                } else {
                    "No matching products."
                }
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = filtered,
                key = { it.id }
            ) { product ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        navController.navigate(
                            AppRoutes.product(product.id)
                        )
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = product.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(product.category)

                            if (product.barcode.isNotBlank()) {
                                Text(
                                    text = "Barcode: ${product.barcode}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(
                            horizontalAlignment = Alignment.End
                        ) {
                            Text("Qty ${product.quantity}")

                            if (product.quantity <= product.minimumStock) {
                                Text(
                                    text = "LOW STOCK",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddProduct) {
        AddProductDialog(
            repo = repo,
            onDismiss = { showAddProduct = false },
            onSaved = {
                // Room supplies the latest saved and synchronized values.
                error = null
                showAddProduct = false
            }
        )
    }
}

@Composable
private fun AddProductDialog(
    repo: BizRepository,
    onDismiss: () -> Unit,
    onSaved: (Product) -> Unit
) {
    val scope = rememberCoroutineScope()

    // Reuse the ID if a save must be retried.
    val productId = remember { UUID.randomUUID().toString() }

    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var costPrice by remember { mutableStateOf("") }
    var sellingPrice by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var minimumStock by remember { mutableStateOf("5") }
    var barcode by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = {
            if (!saving) onDismiss()
        },
        title = { Text("Add Product") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProductInput(
                    label = "Product name",
                    value = name,
                    enabled = !saving,
                    onChange = { name = it }
                )

                ProductInput(
                    label = "Category",
                    value = category,
                    enabled = !saving,
                    onChange = { category = it }
                )

                ProductInput(
                    label = "Cost price (R)",
                    value = costPrice,
                    enabled = !saving,
                    keyboardType = KeyboardType.Decimal,
                    onChange = { costPrice = it }
                )

                ProductInput(
                    label = "Selling price (R)",
                    value = sellingPrice,
                    enabled = !saving,
                    keyboardType = KeyboardType.Decimal,
                    onChange = { sellingPrice = it }
                )

                ProductInput(
                    label = "Opening quantity",
                    value = quantity,
                    enabled = !saving,
                    keyboardType = KeyboardType.Number,
                    onChange = { quantity = it }
                )

                ProductInput(
                    label = "Low-stock threshold",
                    value = minimumStock,
                    enabled = !saving,
                    keyboardType = KeyboardType.Number,
                    onChange = { minimumStock = it }
                )

                ProductInput(
                    label = "Barcode (optional)",
                    value = barcode,
                    enabled = !saving,
                    onChange = { barcode = it }
                )

                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    val cost = costPrice.trim().toDoubleOrNull()
                    val price = sellingPrice.trim().toDoubleOrNull()
                    val stock = quantity.trim().toIntOrNull()
                    val minimum = minimumStock.trim().toIntOrNull()

                    when {
                        name.isBlank() -> {
                            error = "Enter a product name."
                        }

                        category.isBlank() -> {
                            error = "Enter a category."
                        }

                        cost == null || !cost.isFinite() || cost < 0 -> {
                            error = "Enter a valid non-negative cost price."
                        }

                        price == null || !price.isFinite() || price <= 0 -> {
                            error = "Selling price must be greater than zero."
                        }

                        stock == null || stock < 0 -> {
                            error = "Quantity must be a non-negative whole number."
                        }

                        minimum == null || minimum < 0 -> {
                            error = "Threshold must be a non-negative whole number."
                        }

                        else -> {
                            val product = Product(
                                id = productId,
                                name = name.trim(),
                                category = category.trim(),
                                costPrice = cost,
                                sellingPrice = price,
                                quantity = stock,
                                minimumStock = minimum,
                                barcode = barcode.trim()
                            )

                            saving = true
                            error = null

                            scope.launch {
                                try {
                                    repo.saveProduct(product)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    error = "Could not save the product. Please retry."
                                    saving = false
                                    return@launch
                                }

                                saving = false
                                onSaved(product)
                            }
                        }
                    }
                }
            ) {
                Text(if (saving) "Saving..." else "Save")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !saving,
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ProductInput(
    label: String,
    value: String,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType
        )
    )
}