package com.example.bizmanagezz.data.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AtomicFile
import com.example.bizmanagezz.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

data class ProductPhotoMetadata(
    val productId: String,
    val url: String,
    val publicId: String,
    val width: Int,
    val height: Int,
    val version: Long
)

data class ProductPhotoEnvelope(
    val photo: ProductPhotoMetadata?
)

data class PreparedProductPhoto(
    val bytes: ByteArray,
    val operationId: String,
    val expectedVersion: Long
)

interface ProductPhotoApi {
    @GET("products/{id}/photo")
    suspend fun getPhoto(
        @Header("Authorization") authorization: String,
        @Path("id") id: String
    ): ProductPhotoEnvelope

    @PUT("products/{id}/photo")
    suspend fun uploadPhoto(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Header("Idempotency-Key") operationId: String,
        @Query("expectedVersion") expectedVersion: Long,
        @Body image: RequestBody
    ): ProductPhotoMetadata
}

class ProductPhotoStore(
    context: Context,
    private val productId: String
) {
    private val context = context.applicationContext

    private val accountId = checkNotNull(
        FirebaseAuth.getInstance().currentUser
    ).uid

    private val directory = File(
        this.context.filesDir,
        "product_photos/${hash(accountId)}/${hash(productId)}"
    )

    private val gson = Gson()

    private fun checkAccount() {
        check(
            FirebaseAuth.getInstance().currentUser?.uid == accountId
        ) {
            "Account changed. Reopen this product."
        }
    }

    suspend fun cached():
            Pair<ProductPhotoMetadata?, Bitmap?> =
        withContext(Dispatchers.IO) {
            checkAccount()

            val metadata = runCatching {
                val file = File(directory, "photo.json")

                if (!file.exists()) {
                    null
                } else {
                    gson.fromJson(
                        AtomicFile(file)
                            .openRead()
                            .bufferedReader()
                            .use { it.readText() },
                        ProductPhotoMetadata::class.java
                    )
                }
            }.getOrNull()

            val bitmap = metadata?.let {
                BitmapFactory.decodeFile(
                    imageFile(it).absolutePath
                )
            }

            metadata to bitmap
        }

    suspend fun refresh():
            Pair<ProductPhotoMetadata?, Bitmap?> {
        val authorization = authorization()

        val metadata = api.getPhoto(
            authorization,
            productId
        ).photo

        checkAccount()

        return withContext(Dispatchers.IO) {
            if (metadata == null) {
                return@withContext null to null
            }

            require(metadata.productId == productId)

            val uri = Uri.parse(metadata.url)

            require(
                uri.scheme == "https" &&
                        uri.host == "res.cloudinary.com"
            ) {
                "The server returned an invalid photo address."
            }

            val imageFile = imageFile(metadata)

            if (!imageFile.exists()) {
                val request = Request.Builder()
                    .url(metadata.url)
                    .build()

                val bytes = client.newCall(request)
                    .execute()
                    .use { response ->
                        check(response.isSuccessful) {
                            "Could not download the product photo."
                        }

                        checkNotNull(response.body)
                            .byteStream()
                            .use {
                                readLimited(
                                    it,
                                    5 * 1024 * 1024
                                )
                            }
                    }

                checkAccount()

                check(
                    BitmapFactory.decodeByteArray(
                        bytes,
                        0,
                        bytes.size
                    ) != null
                ) {
                    "The downloaded photo could not be displayed."
                }

                writeAtomic(imageFile, bytes)
            }

            writeMetadata(metadata)

            metadata to BitmapFactory.decodeFile(
                imageFile.absolutePath
            )
        }
    }

    suspend fun prepare(
        uri: Uri,
        expectedVersion: Long
    ): PreparedProductPhoto =
        withContext(Dispatchers.IO) {
            checkAccount()

            val original = checkNotNull(
                context.contentResolver.openInputStream(uri)
            ) {
                "Could not open this image."
            }.use {
                readLimited(it, 12 * 1024 * 1024)
            }

            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            BitmapFactory.decodeByteArray(
                original,
                0,
                original.size,
                bounds
            )

            require(
                bounds.outWidth > 0 &&
                        bounds.outHeight > 0
            ) {
                "Choose a supported image."
            }

            var sample = 1

            while (
                bounds.outWidth / sample > 1600 ||
                bounds.outHeight / sample > 1600
            ) {
                sample *= 2
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
            }

            val bitmap = checkNotNull(
                BitmapFactory.decodeByteArray(
                    original,
                    0,
                    original.size,
                    options
                )
            ) {
                "Could not read this image."
            }

            val output = ByteArrayOutputStream()

            try {
                check(
                    bitmap.compress(
                        Bitmap.CompressFormat.JPEG,
                        85,
                        output
                    )
                ) {
                    "Could not prepare this image."
                }
            } finally {
                bitmap.recycle()
            }

            val bytes = output.toByteArray()

            require(bytes.size <= 3 * 1024 * 1024) {
                "Choose a smaller photo."
            }

            PreparedProductPhoto(
                bytes = bytes,
                operationId = UUID.randomUUID().toString(),
                expectedVersion = expectedVersion
            )
        }

    suspend fun upload(
        prepared: PreparedProductPhoto
    ): Pair<ProductPhotoMetadata, Bitmap?> {
        val result = api.uploadPhoto(
            authorization = authorization(),
            id = productId,
            operationId = prepared.operationId,
            expectedVersion = prepared.expectedVersion,
            image = prepared.bytes.toRequestBody(
                "image/jpeg".toMediaType()
            )
        )

        checkAccount()
        require(result.productId == productId)

        return withContext(Dispatchers.IO) {
            writeAtomic(
                imageFile(result),
                prepared.bytes
            )

            writeMetadata(result)

            result to BitmapFactory.decodeByteArray(
                prepared.bytes,
                0,
                prepared.bytes.size
            )
        }
    }

    private suspend fun authorization(): String {
        checkAccount()

        val user = checkNotNull(
            FirebaseAuth.getInstance().currentUser
        )

        val token = user.getIdToken(false)
            .await()
            .token

        checkAccount()
        check(!token.isNullOrBlank())

        return "Bearer $token"
    }

    private fun imageFile(
        metadata: ProductPhotoMetadata
    ): File {
        return File(
            directory,
            "${hash(metadata.url)}.jpg"
        )
    }

    private fun writeMetadata(
        metadata: ProductPhotoMetadata
    ) {
        writeAtomic(
            File(directory, "photo.json"),
            gson.toJson(metadata).toByteArray(Charsets.UTF_8)
        )
    }

    private fun writeAtomic(
        file: File,
        bytes: ByteArray
    ) {
        check(directory.exists() || directory.mkdirs()) {
            "Could not create photo storage."
        }

        val atomicFile = AtomicFile(file)
        val stream = atomicFile.startWrite()

        try {
            stream.write(bytes)
            atomicFile.finishWrite(stream)
        } catch (error: Exception) {
            atomicFile.failWrite(stream)
            throw error
        }
    }

    companion object {
        private val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        private val api: ProductPhotoApi by lazy {
            Retrofit.Builder()
                .baseUrl(BuildConfig.API_BASE_URL)
                .client(client)
                .addConverterFactory(
                    GsonConverterFactory.create()
                )
                .build()
                .create(ProductPhotoApi::class.java)
        }

        private fun hash(value: String): String {
            return MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray())
                .joinToString("") {
                    "%02x".format(it.toInt() and 255)
                }
        }

        private fun readLimited(
            input: java.io.InputStream,
            maximum: Int
        ): ByteArray {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0

            while (true) {
                val count = input.read(buffer)

                if (count < 0) break

                total += count

                require(total <= maximum) {
                    "Image is too large. Choose a smaller photo."
                }

                output.write(buffer, 0, count)
            }

            return output.toByteArray()
        }
    }
}