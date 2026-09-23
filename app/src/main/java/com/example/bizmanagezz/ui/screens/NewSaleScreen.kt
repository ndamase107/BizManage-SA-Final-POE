package com.example.bizmanagezz.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bizmanagezz.data.model.Sale
import com.example.bizmanagezz.data.repository.BizRepository
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NewSaleScreen(onDone: () -> Unit) {
    val repo = remember { BizRepository() }
    var customer by remember { mutableStateOf("") }
    var total by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    ScreenColumn {
        Text("New Sale", style = MaterialTheme.typography.headlineMedium)
        Text("Record a sale and associate it with a customer when applicable.")

        OutlinedTextField(customer, { customer = it }, Modifier.fillMaxWidth(), label = { Text("Customer") })
        OutlinedTextField(
            total,
            { total = it },
            Modifier.fillMaxWidth(),
            label = { Text("Total amount (R)") }
        )

        if (error) Text("Enter a valid amount greater than zero.", color = MaterialTheme.colorScheme.error)

        Button(
            onClick = {
                val amount = total.toDoubleOrNull()
                error = amount == null || amount <= 0
                if (!error) {
                    val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                    kotlinx.coroutines.MainScope().launch {
                        repo.saveSale(Sale(UUID.randomUUID().toString(), customer.ifBlank { null }, amount!!, date, false))
                        onDone()
                    }
                }
            },
            Modifier.fillMaxWidth()
        ) { Text("Save Sale") }
    }
}
