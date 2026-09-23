package com.example.bizmanagezz.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.bizmanagezz.ui.screens.*

@Composable
fun BizManageApp() {
    val navController = rememberNavController()
    var signedIn by remember { mutableStateOf(false) }

    if (!signedIn) {
        LoginScreen(onSignedIn = {
            signedIn = true
            navController.navigate(AppRoutes.DASHBOARD) {
                popUpTo(AppRoutes.LOGIN) { inclusive = true }
            }
        })
        return
    }

    MainScaffold(navController = navController, onLogout = { signedIn = false })
}

@Composable
private fun MainScaffold(
    navController: NavHostController,
    onLogout: () -> Unit
) {
    val entry by navController.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                BottomItem("Dashboard", Icons.Default.Dashboard, AppRoutes.DASHBOARD, currentRoute, navController)
                BottomItem("Sales", Icons.Default.PointOfSale, AppRoutes.SALES, currentRoute, navController)
                BottomItem("Inventory", Icons.Default.Inventory, AppRoutes.INVENTORY, currentRoute, navController)
                BottomItem("Customers", Icons.Default.People, AppRoutes.CUSTOMERS, currentRoute, navController)
                BottomItem("More", Icons.Default.Analytics, "more", currentRoute, navController)
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AppRoutes.DASHBOARD,
            modifier = Modifier.padding(padding)
        ) {
            composable(AppRoutes.DASHBOARD) { DashboardScreen(navController) }
                composable("more") { MoreScreen(navController) }
            composable(AppRoutes.SALES) { SalesScreen(navController) }
            composable(AppRoutes.NEW_SALE) { NewSaleScreen(onDone = { navController.popBackStack() }) }
            composable(AppRoutes.INVENTORY) { InventoryScreen(navController) }
            composable(AppRoutes.PRODUCT) { backStack ->
                ProductDetailsScreen(
                    productId = backStack.arguments?.getString("id") ?: "",
                    onBack = { navController.popBackStack() }
                )
            }
            composable(AppRoutes.CUSTOMERS) { CustomersScreen(navController) }
            composable(AppRoutes.EXPENSES) { ExpensesScreen(navController) }
            composable(AppRoutes.REPORTS) { ReportsScreen() }
            composable(AppRoutes.NOTIFICATIONS) { NotificationsScreen() }
            composable(AppRoutes.SETTINGS) { SettingsScreen(onLogout) }
            composable(AppRoutes.BUSINESS_SETUP) { BusinessSetupScreen(onComplete = {
                navController.navigate(AppRoutes.DASHBOARD) {
                    popUpTo(AppRoutes.BUSINESS_SETUP) { inclusive = true }
                }
            }) }
        }
    }
}

@Composable
private fun BottomItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    route: String,
    currentRoute: String?,
    navController: NavHostController
) {
    NavigationBarItem(
        selected = currentRoute == route,
        onClick = {
            navController.navigate(route) {
                launchSingleTop = true
                restoreState = true
            }
        },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) }
    )
}
