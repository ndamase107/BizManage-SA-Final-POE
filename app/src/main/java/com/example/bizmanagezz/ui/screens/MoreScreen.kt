package com.example.bizmanagezz.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.bizmanagezz.R
import com.example.bizmanagezz.ui.AppRoutes

@Composable
fun MoreScreen(navController: NavHostController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.bm_more),
            style = MaterialTheme.typography.headlineMedium
        )

        MoreMenuButton(
            title = "Expenses",
            onClick = {
                navController.navigate(AppRoutes.EXPENSES) {
                    launchSingleTop = true
                }
            }
        )

        MoreMenuButton(
            title = "Reports",
            onClick = {
                navController.navigate(AppRoutes.REPORTS) {
                    launchSingleTop = true
                }
            }
        )

        MoreMenuButton(
            title = "Notifications",
            onClick = {
                navController.navigate(AppRoutes.NOTIFICATIONS) {
                    launchSingleTop = true
                }
            }
        )

        MoreMenuButton(
            title = stringResource(R.string.bm_settings),
            onClick = {
                navController.navigate(AppRoutes.SETTINGS) {
                    launchSingleTop = true
                }
            }
        )
    }
}

@Composable
private fun MoreMenuButton(
    title: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(8.dp)
        )
    }
}