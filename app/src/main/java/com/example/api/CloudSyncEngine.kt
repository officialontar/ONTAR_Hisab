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

    private fun getSanitizedRedirectKey(identifier: String): String {
        return "redirect_" + identifier.lowercase()
            .trim()
            .replace("@", "_at_")
            .replace(".", "_dot_")
            .replace("-", "")
            .replace(" ", "")
            .filter { it.isLetterOrDigit() || it == '_' }
    }

    private fun fetchPayloadByUrl(url: String): SyncPayload? {
        return try {
            val request = Request.Builder().url(url).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = response.body?.string() ?: return null
                    payloadAdapter.fromJson(json)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network exception fetching payload directly", e)
            null
        }
    }

    private fun fetchStringByUrl(url: String): String? {
        return try {
            val request = Request.Builder().url(url).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()?.trim()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network exception fetching string directly", e)
            null
        }
    }

    /**
     * Upload payload to custom key-value cloud store
     */
    suspend fun uploadPayload(email: String, payload: SyncPayload): Boolean {
        if (email.isBlank()) return false
        val key = getSanitizedKey(email)
        val url = "$BASE_URL$key"

        val uploadDirectSuccess = try {
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

        if (uploadDirectSuccess) {
            // Background thread-friendly mapping creation
            try {
                uploadRedirectsForUser(payload.user)
            } catch (e: Exception) {
                Log.e(TAG, "Error matching redirects", e)
            }
        }

        return uploadDirectSuccess
    }

    /**
     * Upload redirects to resolve phones and names back to primary email
     */
    private fun uploadRedirectsForUser(user: User) {
        val primaryEmail = user.email.trim().lowercase()
        val redirectIdentifiers = mutableSetOf<String>()

        // Add primary phones
        user.phone.split(",").forEach {
            val clean = it.trim().lowercase()
            if (clean.isNotEmpty() && clean != primaryEmail) {
                redirectIdentifiers.add(clean)
                val ultraClean = clean.replace("-", "").replace(" ", "")
                if (ultraClean.isNotEmpty() && ultraClean != primaryEmail) {
                    redirectIdentifiers.add(ultraClean)
                }
            }
        }

        // Add joint owners
        try {
            val owners = com.example.data.OwnerParser.deserialize(user.ownerName, user.phone, user.email)
            owners.forEach { owner ->
                val oPhone = owner.phone.trim().lowercase()
                val oEmail = owner.email.trim().lowercase()
                if (oPhone.isNotEmpty() && oPhone != primaryEmail) {
                    redirectIdentifiers.add(oPhone)
                    val ultraClean = oPhone.replace("-", "").replace(" ", "")
                    if (ultraClean.isNotEmpty() && ultraClean != primaryEmail) {
                        redirectIdentifiers.add(ultraClean)
                    }
                }
                if (oEmail.isNotEmpty() && oEmail != primaryEmail) {
                    redirectIdentifiers.add(oEmail)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse owner names for redirect upload", e)
        }

        // Upload redirect keys to kvdb.io
        redirectIdentifiers.forEach { id ->
            val redirectKey = getSanitizedRedirectKey(id)
            val url = "$BASE_URL$redirectKey"
            try {
                val requestBody = primaryEmail.toRequestBody("text/plain".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d(TAG, "Uploaded user mapping redirect for $id -> $primaryEmail")
                    } else {
                        Log.e(TAG, "Failed to upload redirect for $id, code ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception saving redirect for $id", e)
            }
        }
    }

    /**
     * Download payload from custom key-value cloud store with redirection support
     */
    suspend fun downloadPayload(email: String): SyncPayload? {
        if (email.isBlank()) return null
        val trimmedRaw = email.trim().lowercase()

        // 1. Try reading directly first (it might be the primary email)
        val directKey = getSanitizedKey(trimmedRaw)
        val directUrl = "$BASE_URL$directKey"
        val directPayload = fetchPayloadByUrl(directUrl)
        if (directPayload != null) {
            return directPayload
        }

        // 2. If direct check fails (e.g. they logged in using Phone or Joint info), check for a redirect key mapping
        val redirectKey = getSanitizedRedirectKey(trimmedRaw)
        val redirectUrl = "$BASE_URL$redirectKey"
        val redirectedEmail = fetchStringByUrl(redirectUrl)
        if (!redirectedEmail.isNullOrBlank()) {
            val resolvedUrl = "$BASE_URL${getSanitizedKey(redirectedEmail)}"
            return fetchPayloadByUrl(resolvedUrl)
        }

        return null
    }

    /**
     * Fetch all registered users
     */
    suspend fun fetchAllRegisteredUsers(): List<User> {
        val list = mutableListOf<User>()
        try {
            val url = BASE_URL
            val request = Request.Builder().url(url).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val raw = response.body?.string() ?: return emptyList()
                    val jsonKeysRegex = "\"[^\"]+\"".toRegex()
                    val keys = jsonKeysRegex.findAll(raw)
                        .map { it.value.replace("\"", "") }
                        .filter { it.startsWith("user_") }
                        .toList()
                    
                    keys.forEach { key ->
                        try {
                            val userPayloadUrl = "$BASE_URL$key"
                            val payload = fetchPayloadByUrl(userPayloadUrl)
                            if (payload != null) {
                                list.add(payload.user)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing user key data: $key", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list keys from kvdb.io", e)
        }
        return list
    }
}
