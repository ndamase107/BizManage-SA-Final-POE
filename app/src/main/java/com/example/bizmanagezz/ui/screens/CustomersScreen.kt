package com.example.bizmanagezz.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.bizmanagezz.data.model.Customer
import com.example.bizmanagezz.data.repository.BizRepository
import java.util.UUID

@Composable
fun CustomersScreen(navController: NavHostController) {
    val repo = remember { BizRepository() }
    var customers by remember { mutableStateOf<List<Customer>>(emptyList()) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { customers = repo.getCustomers() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Customers", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Customer name") })
        OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("Phone") })
        Button(
            onClick = {
                if (name.isNotBlank() && phone.isNotBlank()) {
                    val customer = Customer(UUID.randomUUID().toString(), name, phone)
                    kotlinx.coroutines.MainScope().launch {
                        repo.saveCustomer(customer)
                        customers = repo.getCustomers()
                        name = ""
                        phone = ""
                    }
                }
            },
            Modifier.fillMaxWidth()
        ) { Text("Add Customer") }

        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(customers) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(it.name, style = MaterialTheme.typography.titleMedium)
                        Text(it.phone)
                        if (it.email.isNotBlank()) Text(it.email)
                    }
                }
            }
        }
    }
}
