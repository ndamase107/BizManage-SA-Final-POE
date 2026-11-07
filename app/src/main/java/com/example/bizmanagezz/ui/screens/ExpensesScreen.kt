package com.example.bizmanagezz.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.bizmanagezz.data.model.Expense
import com.example.bizmanagezz.data.repository.BizRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ExpensesScreen(
    @Suppress("UNUSED_PARAMETER")
    navController: NavHostController
) {
    val repo = remember { BizRepository() }
    val scope = rememberCoroutineScope()

    var expenses by remember {
        mutableStateOf<List<Expense>>(emptyList())
    }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    ObserveLocalChanges(
        "expenses",
        onError = {
            errorMessage = "Could not load expenses. Please try again."
            isLoading = false
        }
    ) {
        expenses = repo.getExpenses()
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Expenses",
            style = MaterialTheme.typography.headlineMedium
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Description") },
            enabled = !isSaving,
            singleLine = true
        )

        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Amount (R)") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            ),
            enabled = !isSaving,
            singleLine = true
        )

        errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error
            )
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving && !isLoading,
            onClick = {
                val cleanDescription = description.trim()
                val value = amount.trim().toDoubleOrNull()

                when {
                    cleanDescription.isBlank() -> {
                        errorMessage = "Enter an expense description."
                    }

                    value == null || !value.isFinite() || value <= 0 -> {
                        errorMessage = "Enter a valid amount greater than zero."
                    }

                    else -> {
                        isSaving = true
                        errorMessage = null

                        val date = SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.US
                        ).format(Date())

                        // Positional arguments follow your existing model usage.
                        val expense = Expense(
                            UUID.randomUUID().toString(),
                            cleanDescription,
                            "General",
                            value,
                            date,
                            false
                        )

                        scope.launch {
                            try {
                                repo.saveExpense(expense)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                errorMessage =
                                    "Could not confirm the save. Check your expenses before retrying."
                                isSaving = false
                                return@launch
                            }

                            // Clear inputs only after the save succeeds.
                            description = ""
                            amount = ""

                            try {
                                expenses = repo.getExpenses()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                errorMessage =
                                    "Expense saved, but the list could not refresh."
                            } finally {
                                isSaving = false
                            }
                        }
                    }
                }
            }
        ) {
            Text(if (isSaving) "Saving..." else "Add Expense")
        }

        if (isLoading) {
            CircularProgressIndicator()
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(expenses) { expense ->
                Card(
                    modifier = Modifier.fillMaxWidth()
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
                                text = expense.description,
                                style = MaterialTheme.typography.titleMedium
                            )

                            Text(
                                text = "${expense.category} â€¢ ${expense.date}"
                            )
                        }

                        Text(
                            text = String.format(
                                Locale.US,
                                "R%.2f",
                                expense.amount
                            )
                        )
                    }
                }
            }
        }
    }
}