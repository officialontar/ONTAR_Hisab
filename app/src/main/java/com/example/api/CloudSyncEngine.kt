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
import kotlinx.coroutines.*

data class SyncPayload(
    val user: User,
    val stockItems: List<StockItem> = emptyList(),
    val customers: List<Customer> = emptyList(),
    val dealers: List<Dealer> = emptyList(),
    val transactions: List<TransactionRecord> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val registrationTimestamp: Long? = null,
    val additionalShops: List<User> = emptyList()
)

object CloudSyncEngine {
    private const val TAG = "CloudSyncEngine"
    private const val BUCKET_ID = "6h9NTtLDbTocxLkyC7Jpv6"
    private const val BASE_URL = "https://kvdb.io/$BUCKET_ID/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val payloadAdapter = moshi.adapter(SyncPayload::class.java)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
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
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val primaryEmail = user.email.trim().lowercase()
            val redirectIdentifiers = mutableSetOf<String>()

            // Add primary phones
            user.phone.split(",").forEach {
                val clean = it.trim().lowercase()
                if (clean.isNotEmpty() && clean != primaryEmail) {
                    redirectIdentifiers.add(clean)
                    val ultraClean = clean.replace("-", "").replace(" ", "").replace("+", "")
                    if (ultraClean.isNotEmpty() && ultraClean != primaryEmail) {
                        redirectIdentifiers.add(ultraClean)
                        if (ultraClean.length >= 11) {
                            redirectIdentifiers.add(ultraClean.takeLast(11))
                        }
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
                        val ultraClean = oPhone.replace("-", "").replace(" ", "").replace("+", "")
                        if (ultraClean.isNotEmpty() && ultraClean != primaryEmail) {
                            redirectIdentifiers.add(ultraClean)
                            if (ultraClean.length >= 11) {
                                redirectIdentifiers.add(ultraClean.takeLast(11))
                            }
                        }
                    }
                    if (oEmail.isNotEmpty() && oEmail != primaryEmail) {
                        redirectIdentifiers.add(oEmail)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse owner names for redirect upload", e)
            }

            // Upload redirect keys to kvdb.io in parallel/asynchronously
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
    }

    /**
     * Download payload from custom key-value cloud store with redirection support
     */
    suspend fun downloadPayload(email: String): SyncPayload? = coroutineScope {
        if (email.isBlank()) return@coroutineScope null
        val trimmedRaw = email.trim().lowercase()

        // 1. Try reading directly first
        val directDeferred = async(Dispatchers.IO) {
            val directKey = getSanitizedKey(trimmedRaw)
            val directUrl = "$BASE_URL$directKey"
            fetchPayloadByUrl(directUrl)
        }

        // 2. Try redirect key mapping
        val redirectDeferred = async(Dispatchers.IO) {
            val redirectKey = getSanitizedRedirectKey(trimmedRaw)
            val redirectUrl = "$BASE_URL$redirectKey"
            val redirectedEmail = fetchStringByUrl(redirectUrl)
            if (!redirectedEmail.isNullOrBlank()) {
                val resolvedUrl = "$BASE_URL${getSanitizedKey(redirectedEmail)}"
                fetchPayloadByUrl(resolvedUrl)
            } else {
                null
            }
        }

        // 3. Try normalizing raw identifier to 11-digit mobile number if applicable
        val phoneDeferred = async(Dispatchers.IO) {
            val ultraCleanRaw = trimmedRaw.replace("-", "").replace(" ", "").replace("+", "")
            if (ultraCleanRaw.length >= 11) {
                val shortPhone = ultraCleanRaw.takeLast(11)
                val shortRedirectKey = getSanitizedRedirectKey(shortPhone)
                val shortRedirectUrl = "$BASE_URL$shortRedirectKey"
                val shortRedirectedEmail = fetchStringByUrl(shortRedirectUrl)
                if (!shortRedirectedEmail.isNullOrBlank()) {
                    val resolvedUrl = "$BASE_URL${getSanitizedKey(shortRedirectedEmail)}"
                    fetchPayloadByUrl(resolvedUrl)
                } else null
            } else null
        }

        // Wait concurrently but prioritize the directResult if found because it's fastest
        val directResult = directDeferred.await()
        if (directResult != null) return@coroutineScope directResult

        val redirectResult = redirectDeferred.await()
        if (redirectResult != null) return@coroutineScope redirectResult

        val phoneResult = phoneDeferred.await()
        if (phoneResult != null) return@coroutineScope phoneResult

        null
    }

    /**
     * Fetch all registered users
     */
    suspend fun fetchAllRegisteredUsers(): List<User> = coroutineScope {
        val list = java.util.Collections.synchronizedList(mutableListOf<User>())
        try {
            val url = BASE_URL
            val request = Request.Builder().url(url).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val raw = response.body?.string() ?: return@coroutineScope emptyList()
                    
                    // Support standard plaintext newline formats as well as JSON listings
                    val cleanRaw = raw.replace("[", "").replace("]", "")
                    val keys = cleanRaw.split(Regex("[\n\r,]+"))
                        .map { it.replace("\"", "").trim() }
                        .filter { it.startsWith("user_") && it.isNotBlank() }
                        .distinct()
                    
                    val deferreds = keys.map { key ->
                        async(Dispatchers.IO) {
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
                    deferreds.awaitAll()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list keys from kvdb.io", e)
        }
        list.toList()
    }

    /**
     * Delete user payload from kvdb.io
     */
    suspend fun deletePayload(email: String): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val key = getSanitizedKey(email)
            val url = "$BASE_URL$key"
            val request = Request.Builder()
                .url(url)
                .delete()
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network exception deleting sync payload", e)
            false
        }
    }
}
