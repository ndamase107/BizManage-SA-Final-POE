package com.example.bizmanagezz.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.bizmanagezz.data.local.AppDatabase
import com.example.bizmanagezz.data.model.Product
import com.example.bizmanagezz.data.repository.BizRepository
import com.example.bizmanagezz.data.repository.SaleCheckout
import com.example.bizmanagezz.domain.SaleRules
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun NewSaleScreen(onDone: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val repo = remember { BizRepository() }
    val checkout = remember(context) {
        SaleCheckout(AppDatabase.getInstance(context))
    }
    val scope = rememberCoroutineScope()

    var products by remember {
        mutableStateOf<List<Product>>(emptyList())
    }

    var selectedId by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var customer by rememberSaveable { mutableStateOf("") }
    var quantityText by rememberSaveable { mutableStateOf("1") }

    // Survives rotation so the same save can be safely retried.
    val operationId = rememberSaveable {
        UUID.randomUUID().toString()
    }

    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        loading = true
        error = null

        try {
            products = repo.getProducts()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "Could not load products. Tap Reload Products."
        } finally {
            loading = false
        }
    }

    val selectedProduct = products.firstOrNull {
        it.id == selectedId
    }
    val quantity = quantityText.trim().toIntOrNull()

    val previewTotal = if (
        selectedProduct != null && quantity != null
    ) {
        try {
            SaleRules.totalCents(
                unitPriceCents = SaleRules.priceToCents(
                    selectedProduct.sellingPrice
                ),
                quantity = quantity,
                availableStock = selectedProduct.quantity
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    } else {
        null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "New Sale",
            style = MaterialTheme.typography.headlineMedium
        )

        Text("Select a product and enter the quantity sold.")

        TextButton(
            enabled = !loading && !saving,
            onClick = { reloadKey++ }
        ) {
            Text("Reload Products")
        }

        if (loading) {
            CircularProgressIndicator()
        } else if (products.isEmpty()) {
            Text("Add a product in Inventory before recording a sale.")
        }

        products.forEach { product ->
            FilterChip(
                selected = selectedId == product.id,
                enabled = !saving &&
                        !loading &&
                        product.quantity > 0,
                onClick = {
                    selectedId = product.id
                    error = null
                },
                label = {
                    Text(
                        "${product.name} · Stock: ${product.quantity}"
                    )
                }
            )
        }

        OutlinedTextField(
            value = quantityText,
            onValueChange = {
                quantityText = it
                error = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Quantity") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            ),
            enabled = !saving,
            singleLine = true
        )

        OutlinedTextField(
            value = customer,
            onValueChange = { customer = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Customer name (optional)") },
            enabled = !saving,
            singleLine = true
        )

        Text(
            text = previewTotal?.let {
                "Total: R${BigDecimal.valueOf(it, 2).toPlainString()}"
            } ?: "Total: select a product and valid quantity",
            style = MaterialTheme.typography.titleLarge
        )

        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error
            )
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading && !saving && products.isNotEmpty(),
            onClick = {
                val product = selectedProduct
                val count = quantityText.trim().toIntOrNull()

                if (product == null) {
                    error = "Select a product."
                } else if (count == null || count <= 0) {
                    error = "Enter a whole quantity greater than zero."
                } else {
                    saving = true
                    error = null

                    scope.launch {
                        try {
                            checkout.recordSale(
                                operationId = operationId,
                                productId = product.id,
                                quantity = count,
                                customerName = customer,
                                expectedPriceCents = SaleRules.priceToCents(
                                    product.sellingPrice
                                )
                            )
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (invalid: IllegalArgumentException) {
                            error = invalid.message
                                ?: "Check the sale details."
                            saving = false
                            return@launch
                        } catch (_: Exception) {
                            error =
                                "Could not confirm the save. Retry the same details before starting another sale."
                            saving = false
                            return@launch
                        }

                        saving = false
                        onDone()
                    }
                }
            }
        ) {
            Text(if (saving) "Saving..." else "Save Sale")
        }
    }
}