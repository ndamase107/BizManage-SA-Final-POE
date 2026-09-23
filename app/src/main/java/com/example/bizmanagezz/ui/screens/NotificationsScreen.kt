package com.example.bizmanagezz.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NotificationsScreen() {
    ScreenColumn {
        Text("Notifications", style = MaterialTheme.typography.headlineMedium)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Low-stock alert", style = MaterialTheme.typography.titleMedium)
                Text("Some products are at or below their minimum stock level.")
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Synchronisation", style = MaterialTheme.typography.titleMedium)
                Text("Offline records will be synchronised when connectivity returns.")
            }
        }
    }
}
