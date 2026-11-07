package com.example.bizmanagezz.ui

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.bizmanagezz.R
import com.example.bizmanagezz.ui.screens.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun BizManageApp() {
    val auth = remember { FirebaseAuth.getInstance() }
    val context = LocalContext.current

    val credentialManager = remember(context) {
        CredentialManager.create(context)
    }

    val scope = rememberCoroutineScope()

    var user by remember {
        mutableStateOf(auth.currentUser)
    }

    var loggingOut by remember {
        mutableStateOf(false)
    }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener {
            user = it.currentUser
        }

        auth.addAuthStateListener(listener)

        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    val currentUser = user

    when {
        loggingOut -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()

                    Spacer(
                        modifier = Modifier.height(12.dp)
                    )

                    Text("Signing out...")
                }
            }
        }

        currentUser == null -> {
            LoginScreen(
                onSignedIn = {
                    user = auth.currentUser
                }
            )
        }

        else -> {
            // Reset navigation and biometric state when accounts change.
            key(currentUser.uid) {
                SignedInApp(
                    onLogout = {
                        if (!loggingOut) {
                            loggingOut = true
                            auth.signOut()

                            scope.launch {
                                try {
                                    credentialManager.clearCredentialState(
                                        ClearCredentialStateRequest()
                                    )
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    Log.w(
                                        "BizManageAuth",
                                        "Could not clear credential provider state"
                                    )
                                } finally {
                                    loggingOut = false
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SignedInApp(
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    val notificationContext = LocalContext.current.applicationContext
    val notificationAccountId = FirebaseAuth.getInstance().currentUser?.uid

    LaunchedEffect(notificationAccountId) {
        notificationAccountId?.let {
            com.example.bizmanagezz.data.notifications.NotificationRegistration
                .schedule(notificationContext, it)
        }
    }

    BiometricSession(
        onLogout = onLogout
    ) {
        MainScaffold(
            navController = navController,
            onLogout = onLogout
        )
    }
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
                BottomItem(
                    label = stringResource(R.string.bm_dashboard),
                    icon = Icons.Default.Dashboard,
                    route = AppRoutes.DASHBOARD,
                    currentRoute = currentRoute,
                    navController = navController
                )

                BottomItem(
                    label = stringResource(R.string.bm_sales),
                    icon = Icons.Default.PointOfSale,
                    route = AppRoutes.SALES,
                    currentRoute = currentRoute,
                    navController = navController
                )

                BottomItem(
                    label = stringResource(R.string.bm_inventory),
                    icon = Icons.Default.Inventory,
                    route = AppRoutes.INVENTORY,
                    currentRoute = currentRoute,
                    navController = navController
                )

                BottomItem(
                    label = stringResource(R.string.bm_customers),
                    icon = Icons.Default.People,
                    route = AppRoutes.CUSTOMERS,
                    currentRoute = currentRoute,
                    navController = navController
                )

                BottomItem(
                    label = stringResource(R.string.bm_more),
                    icon = Icons.Default.Analytics,
                    route = "more",
                    currentRoute = currentRoute,
                    navController = navController
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AppRoutes.DASHBOARD,
            modifier = Modifier.padding(padding)
        ) {
            composable(AppRoutes.DASHBOARD) {
                DashboardScreen(navController)
            }

            composable("more") {
                MoreScreen(navController)
            }

            composable(AppRoutes.SALES) {
                SalesScreen(navController)
            }

            composable(AppRoutes.NEW_SALE) {
                NewSaleScreen(
                    onDone = {
                        navController.popBackStack()
                    }
                )
            }

            composable(AppRoutes.INVENTORY) {
                InventoryScreen(navController)
            }

            composable(AppRoutes.PRODUCT) { backStack ->
                ProductDetailsScreen(
                    productId = backStack.arguments
                        ?.getString("id")
                        .orEmpty(),
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(AppRoutes.CUSTOMERS) {
                CustomersScreen(navController)
            }

            composable(AppRoutes.EXPENSES) {
                ExpensesScreen(navController)
            }

            composable(AppRoutes.REPORTS) {
                ReportsScreen()
            }

            composable(AppRoutes.NOTIFICATIONS) {
                NotificationsScreen()
            }

            composable(AppRoutes.SETTINGS) {
                SettingsScreen(
                    onLogout = onLogout
                )
            }

            composable(AppRoutes.BUSINESS_SETUP) {
                BusinessSetupScreen(
                    onComplete = {
                        navController.navigate(
                            AppRoutes.DASHBOARD
                        ) {
                            popUpTo(AppRoutes.BUSINESS_SETUP) {
                                inclusive = true
                            }

                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun RowScope.BottomItem(
    label: String,
    icon: ImageVector,
    route: String,
    currentRoute: String?,
    navController: NavHostController
) {
    NavigationBarItem(
        selected = currentRoute == route,
        onClick = {
            navController.navigate(route) {
                popUpTo(
                    navController.graph.startDestinationId
                ) {
                    saveState = true
                }

                launchSingleTop = true
                restoreState = true
            }
        },
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null
            )
        },
        label = {
            Text(label)
        }
    )
}