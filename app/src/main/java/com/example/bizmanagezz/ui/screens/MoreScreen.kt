package com.example.bizmanagezz.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

@Composable
fun MoreScreen(navController: NavHostController) {
    ScreenColumn {
        Text("More", style = MaterialTheme.typography.headlineMedium)
        Text("Additional BizManage SA tools")

        Button(
            onClick = { navController.navigate("expenses") },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Expenses") }

        Button(
            onClick = { navController.navigate("reports") },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Reports") }

        Button(
            onClick = { navController.navigate("notifications") },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Notifications") }

        Button(
            onClick = { navController.navigate("settings") },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Settings") }
    }
}
