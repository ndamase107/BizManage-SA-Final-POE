package com.example.bizmanagezz.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(onSignedIn: () -> Unit) {
    var loading by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("BizManage SA", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Simple business management for South African small businesses.")
            Spacer(Modifier.height(32.dp))

            Button(
                enabled = !loading,
                onClick = {
                    loading = true
                    onSignedIn()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (loading) "Signing in..." else "Continue with Google")
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Part 1 prototype: connect your Google Credential Manager configuration before production use.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
