package com.example.bizmanagezz.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BusinessSetupScreen(onComplete: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    ScreenColumn {
        Text("Business Setup", style = MaterialTheme.typography.headlineMedium)
        Text("Enter the details for the business you manage.")

        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Business name") })
        OutlinedTextField(type, { type = it }, Modifier.fillMaxWidth(), label = { Text("Business type") })
        OutlinedTextField(contact, { contact = it }, Modifier.fillMaxWidth(), label = { Text("Contact number") })
        OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth(), label = { Text("Address") })

        if (error) Text("Please complete all fields.", color = MaterialTheme.colorScheme.error)

        Button(
            onClick = {
                error = name.isBlank() || type.isBlank() || contact.isBlank() || address.isBlank()
                if (!error) onComplete()
            },
            Modifier.fillMaxWidth()
        ) { Text("Save business") }
    }
}
