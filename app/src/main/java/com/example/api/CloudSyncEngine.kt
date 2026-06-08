package com.example.api

import android.util.Log
import com.example.data.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class SyncPayload(
    val user: User,
    val stockItems: List<StockItem> = emptyList(),
    val customers: List<Customer> = emptyList(),
    val dealers: List<Dealer> = emptyList(),
    val transactions: List<TransactionRecord> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

object CloudSyncEngine {
    private const val TAG = "CloudSyncEngine"
    private const val BUCKET_ID = "hisab_khata_sync_v2_918237"
    private const val BASE_URL = "https://kvdb.io/$BUCKET_ID/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val payloadAdapter = moshi.adapter(SyncPayload::class.java)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun getSanitizedKey(email: String): String {
        return "user_" + email.lowercase()
            .trim()
            .replace("@", "_at_")
            .replace(".", "_dot_")
            .filter { it.isLetterOrDigit() || it == '_' }
    }

    /**
     * Upload payload to custom key-value cloud store
     */
    suspend fun uploadPayload(email: String, payload: SyncPayload): Boolean {
        if (email.isBlank()) return false
        val key = getSanitizedKey(email)
        val url = "$BASE_URL$key"

        return try {
            val json = payloadAdapter.toJson(payload)
            val requestBody = json.toRequestBody("text/plain".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Uploaded sync payload successfully for $email")
                    true
                } else {
                    Log.e(TAG, "Failed to upload sync payload: Code ${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network exception uploading sync payload", e)
            false
        }
    }

    /**
     * Download payload from custom key-value cloud store
     */
    suspend fun downloadPayload(email: String): SyncPayload? {
        if (email.isBlank()) return null
        val key = getSanitizedKey(email)
        val url = "$BASE_URL$key"

        return try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = response.body?.string() ?: return null
                    payloadAdapter.fromJson(json)
                } else if (response.code == 404) {
                    Log.d(TAG, "No sync payload found in cloud for $email")
                    null
                } else {
                    Log.e(TAG, "Failed to download sync payload: Code ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network exception downloading sync payload", e)
            null
        }
    }
}
