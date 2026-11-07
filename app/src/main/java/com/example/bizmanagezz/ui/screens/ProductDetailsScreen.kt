package com.example.bizmanagezz.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bizmanagezz.data.model.Product
import com.example.bizmanagezz.data.repository.BizRepository
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailsScreen(
    productId: String,
    onBack: () -> Unit
) {
    val repo = remember { BizRepository() }
    val scope = rememberCoroutineScope()

    var product by remember(productId) {
        mutableStateOf<Product?>(null)
    }

    var isLoading by remember(productId) {
        mutableStateOf(true)
    }

    var isSaving by remember(productId) {
        mutableStateOf(false)
    }

    var saved by remember(productId) {
        mutableStateOf(false)
    }

    var errorMessage by remember(productId) {
        mutableStateOf<String?>(null)
    }

    ObserveLocalChanges(
        "products",
        onError = {
            errorMessage = "Could not load this product."
            isLoading = false
        }
    ) {
        product = repo.getProducts().firstOrNull {
            it.id == productId
        }

        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Product Details") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error
                )
            }

            val currentProduct = product

            if (isLoading) {
                CircularProgressIndicator()
            } else if (currentProduct == null) {
                Text("Product not found.")
            } else {
                Text(
                    text = currentProduct.name,
                    style = MaterialTheme.typography.headlineSmall
                )

                ProductPhotoPanel(
                    productId = currentProduct.id
                )

                Text("Category: ${currentProduct.category}")

                Text(
                    String.format(
                        Locale.US,
                        "Cost price: R%.2f",
                        currentProduct.costPrice
                    )
                )

                Text(
                    String.format(
                        Locale.US,
                        "Selling price: R%.2f",
                        currentProduct.sellingPrice
                    )
                )

                Text("Quantity: ${currentProduct.quantity}")
                Text("Minimum stock: ${currentProduct.minimumStock}")
                Text("Barcode: ${currentProduct.barcode}")

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving,
                    onClick = {
                        saved = false
                        errorMessage = null

                        if (currentProduct.quantity >= 1_000_000) {
                            errorMessage =
                                "Stock has reached the maximum quantity."
                        } else {
                            isSaving = true

                            scope.launch {
                                try {
                                    repo.saveProduct(
                                        currentProduct.copy(
                                            quantity =
                                                currentProduct.quantity + 1
                                        )
                                    )

                                    saved = true
                                } catch (
                                    cancelled: CancellationException
                                ) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    errorMessage =
                                        "Could not confirm the update. " +
                                                "Check stock before retrying."
                                } finally {
                                    isSaving = false
                                }
                            }
                        }
                    }
                ) {
                    Text(
                        if (isSaving) {
                            "Updating..."
                        } else {
                            "Add Stock"
                        }
                    )
                }

                if (saved) {
                    Text("Stock updated.")
                }
            }
        }
    }
}