package com.example.bizmanagezz.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
fun SettingsScreen(onLogout: () -> Unit) {
    var language by remember { mutableStateOf("English") }
    var biometric by remember { mutableStateOf(false) }
    var notifications by remember { mutableStateOf(true) }

    ScreenColumn {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Text("Language", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("English", "isiZulu", "Sesotho").forEach { option ->
                FilterChip(
                    selected = language == option,
                    onClick = { language = option },
                    label = { Text(option) }
                )
            }
        }

        HorizontalDivider()

        SettingRow(
            title = "Biometric authentication",
            checked = biometric,
            onCheckedChange = { biometric = it }
        )
        SettingRow(
            title = "Business notifications",
            checked = notifications,
            onCheckedChange = { notifications = it }
        )

        HorizontalDivider()

        Button(onClick = { /* Business profile editing is wired here in the full backend phase. */ }) {
            Text("Manage Business Profile")
        }

        OutlinedButton(onClick = onLogout, Modifier.fillMaxWidth()) {
            Text("Log out")
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
