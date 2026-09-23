package com.example.bizmanagezz.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bizmanagezz.data.model.Expense
import com.example.bizmanagezz.data.repository.BizRepository
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ExpensesScreen(navController: androidx.navigation.NavHostController) {
    val repo = remember { BizRepository() }
    var expenses by remember { mutableStateOf<List<Expense>>(emptyList()) }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { expenses = repo.getExpenses() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Expenses", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Description") })
        OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), label = { Text("Amount (R)") })

        Button(
            onClick = {
                val value = amount.toDoubleOrNull()
                if (description.isNotBlank() && value != null && value > 0) {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    kotlinx.coroutines.MainScope().launch {
                        repo.saveExpense(Expense(UUID.randomUUID().toString(), description, "General", value, date, false))
                        expenses = repo.getExpenses()
                        description = ""
                        amount = ""
                    }
                }
            },
            Modifier.fillMaxWidth()
        ) { Text("Add Expense") }

        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(expenses) {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(it.description, style = MaterialTheme.typography.titleMedium)
                            Text("${it.category} • ${it.date}")
                        }
                        Text("R${"%.2f".format(it.amount)}")
                    }
                }
            }
        }
    }
}
