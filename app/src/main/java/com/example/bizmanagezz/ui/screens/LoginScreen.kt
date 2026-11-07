package com.example.bizmanagezz.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.bizmanagezz.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun LoginScreen(onSignedIn: () -> Unit) {
    // Credential Manager needs the activity context for its account picker.
    val context = LocalContext.current
    val credentialManager = remember(context) {
        CredentialManager.create(context)
    }
    val auth = remember { FirebaseAuth.getInstance() }
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "BizManage SA",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Manage your business, inventory and sales."
        )

        Spacer(Modifier.height(32.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading,
            onClick = {
                loading = true
                error = null

                scope.launch {
                    try {
                        // Generated from the updated google-services.json.
                        // This is the WEB client ID, not the Android client ID.
                        val clientId = context.getString(
                            R.string.default_web_client_id
                        )

                        val googleOption = GetGoogleIdOption.Builder()
                            .setServerClientId(clientId)
                            .setFilterByAuthorizedAccounts(false)
                            .setAutoSelectEnabled(false)
                            .build()

                        val request = GetCredentialRequest.Builder()
                            .addCredentialOption(googleOption)
                            .build()

                        val response = credentialManager.getCredential(
                            context = context,
                            request = request
                        )

                        val credential = response.credential

                        if (
                            credential !is CustomCredential ||
                            credential.type !=
                            GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                        ) {
                            error = "Google did not return a supported sign-in."
                            return@launch
                        }

                        val googleCredential =
                            GoogleIdTokenCredential.createFrom(
                                credential.data
                            )

                        val firebaseCredential =
                            GoogleAuthProvider.getCredential(
                                googleCredential.idToken,
                                null
                            )

                        auth.signInWithCredential(
                            firebaseCredential
                        ).await()

                        Log.d(
                            "BizManageAuth",
                            "Firebase Google sign-in succeeded"
                        )

                        onSignedIn()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: GetCredentialCancellationException) {
                        error = "Sign-in cancelled. You can try again."
                    } catch (_: NoCredentialException) {
                        error =
                            "No Google account is available. Add a Google account to this device."
                    } catch (failure: GetCredentialException) {
                        Log.w(
                            "BizManageAuth",
                            "Credential request failed: ${failure.javaClass.simpleName}"
                        )
                        error =
                            "Could not open Google sign-in. Check your internet connection and Google Play services."
                    } catch (failure: Exception) {
                        Log.w(
                            "BizManageAuth",
                            "Authentication failed: ${failure.javaClass.simpleName}"
                        )
                        error =
                            "Sign-in failed. Check your connection and Firebase configuration."
                    } finally {
                        loading = false
                    }
                }
            }
        ) {
            Text(
                if (loading) "Signing in..." else "Continue with Google"
            )
        }

        if (loading) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator()
        }

        error?.let {
            Spacer(Modifier.height(16.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}