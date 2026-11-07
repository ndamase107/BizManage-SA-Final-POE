package com.example.bizmanagezz.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.bizmanagezz.data.photos.PreparedProductPhoto
import com.example.bizmanagezz.data.photos.ProductPhotoMetadata
import com.example.bizmanagezz.data.photos.ProductPhotoStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.HttpException

@Composable
fun ProductPhotoPanel(productId: String) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    val store = remember(context, productId) {
        ProductPhotoStore(context, productId)
    }

    var metadata by remember(store) {
        mutableStateOf<ProductPhotoMetadata?>(null)
    }

    var bitmap by remember(store) {
        mutableStateOf<Bitmap?>(null)
    }

    var prepared by remember(store) {
        mutableStateOf<PreparedProductPhoto?>(null)
    }

    var busy by remember(store) { mutableStateOf(true) }
    var cloudChecked by remember(store) { mutableStateOf(false) }
    var message by remember(store) {
        mutableStateOf<String?>(null)
    }

    suspend fun refreshPhoto() {
        busy = true
        message = null
        prepared = null

        try {
            val result = store.refresh()
            metadata = result.first
            bitmap = result.second
            cloudChecked = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: HttpException) {
            cloudChecked = false

            message = if (error.code() == 404) {
                "Sync this product before uploading a photo."
            } else {
                "Could not check the cloud photo."
            }
        } catch (_: Exception) {
            cloudChecked = false

            message = if (bitmap != null) {
                "Showing the saved photo. Connect to the internet " +
                        "to check for updates."
            } else {
                "Connect to the internet to load or upload a photo."
            }
        } finally {
            busy = false
        }
    }

    LaunchedEffect(store) {
        try {
            val cached = store.cached()
            metadata = cached.first
            bitmap = cached.second

            refreshPhoto()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            message = "Could not load photo storage."
            busy = false
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            busy = true
            message = null

            scope.launch {
                try {
                    val selected = store.prepare(
                        uri,
                        metadata?.version ?: 0L
                    )

                    prepared = selected

                    bitmap = BitmapFactory.decodeByteArray(
                        selected.bytes,
                        0,
                        selected.bytes.size
                    )

                    message = "Photo selected. Tap Upload photo."
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    message = error.message
                        ?: "Could not prepare this photo."
                } finally {
                    busy = false
                }
            }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Product photo",
                style = MaterialTheme.typography.titleMedium
            )

            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Product photo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentScale = ContentScale.Fit
                )
            }

            if (bitmap == null && !busy) {
                Text("No photo available.")
            }

            if (busy) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                enabled = !busy && cloudChecked,
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts
                                .PickVisualMedia
                                .ImageOnly
                        )
                    )
                }
            ) {
                Text("Choose photo")
            }

            prepared?.let { selected ->
                Button(
                    enabled = !busy && cloudChecked,
                    onClick = {
                        busy = true
                        message = null

                        scope.launch {
                            try {
                                val result = store.upload(selected)

                                metadata = result.first
                                bitmap = result.second
                                prepared = null

                                message =
                                    "Photo uploaded to cloud storage."
                            } catch (
                                cancelled: CancellationException
                            ) {
                                throw cancelled
                            } catch (error: HttpException) {
                                if (error.code() == 409) {
                                    cloudChecked = false

                                    message =
                                        "The cloud photo changed. " +
                                                "Tap Reload photo, then choose " +
                                                "your image again."
                                } else {
                                    message =
                                        "Upload failed (${error.code()}). " +
                                                "Check the backend and retry."
                                }
                            } catch (_: Exception) {
                                message =
                                    "Upload could not be confirmed. " +
                                            "Reconnect and tap Upload photo again."
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) {
                    Text("Upload photo")
                }
            }

            OutlinedButton(
                enabled = !busy,
                onClick = {
                    scope.launch {
                        refreshPhoto()
                    }
                }
            ) {
                Text("Reload photo")
            }

            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                "Uploads require internet. Selected photos are " +
                        "not queued for background upload.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}