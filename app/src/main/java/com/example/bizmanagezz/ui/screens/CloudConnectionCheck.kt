package com.example.bizmanagezz.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bizmanagezz.BuildConfig
import com.example.bizmanagezz.data.api.ApiClient
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import retrofit2.HttpException
import java.io.IOException

@Composable
fun CloudConnectionCheck() {
    if (!BuildConfig.DEBUG) {
        return
    }

    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !checking,
            onClick = {
                checking = true
                result = null

                scope.launch {
                    try {
                        val auth = FirebaseAuth.getInstance()
                        val user = checkNotNull(auth.currentUser) {
                            "Sign in first."
                        }

                        val token = checkNotNull(
                            user.getIdToken(false).await().token
                        ) {
                            "Could not obtain a login token."
                        }

                        check(auth.currentUser?.uid == user.uid) {
                            "Account changed. Try again."
                        }

                        val page = ApiClient.api.getProducts(
                            authorization = "Bearer $token"
                        )

                        result =
                            "Cloud connection successful. " +
                                    "${page.items.count { !it.deleted }} products on this page."
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: HttpException) {
                        result = when (failure.code()) {
                            401 -> "Session rejected. Sign out and sign in again."
                            403 -> "Cloud access was denied."
                            else -> "The API returned HTTP ${failure.code()}."
                        }
                    } catch (_: IOException) {
                        result =
                            "Cannot reach the API. Check that the server is running on the emulator host."
                    } catch (_: Exception) {
                        result =
                            "Could not check the cloud connection."
                    } finally {
                        checking = false
                    }
                }
            }
        ) {
            Text(
                if (checking) {
                    "Checking..."
                } else {
                    "Check Cloud Connection"
                }
            )
        }

        result?.let {
            Text(it)
        }
    }
}