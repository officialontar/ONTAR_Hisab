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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import android.util.Base64
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
    private var dynamicBaseUrl: String = "https://ontar-hisab-eb1ea-default-rtdb.firebaseio.com/"

    fun initialize(url: String) {
        val clean = url.trim()
        if (clean.isNotBlank()) {
            dynamicBaseUrl = if (clean.endsWith("/")) clean else "$clean/"
        }
    }

    private fun getBaseUrl(): String {
        return dynamicBaseUrl
    }

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val payloadAdapter = moshi.adapter(SyncPayload::class.java)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
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

    private fun compress(str: String): String {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { gzos ->
            gzos.write(str.toByteArray(Charsets.UTF_8))
        }
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    private fun decompress(compressedStr: String): String {
        val bytes = Base64.decode(compressedStr, Base64.NO_WRAP)
        val bis = ByteArrayInputStream(bytes)
        val gis = GZIPInputStream(bis)
        val bos = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        var len: Int
        while (gis.read(buffer).also { len = it } > 0) {
            bos.write(buffer, 0, len)
        }
        return bos.toString("UTF-8")
    }

    private fun fetchPayloadByUrl(url: String): SyncPayload? {
        return try {
            val request = Request.Builder().url(url).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val rawBody = response.body?.string() ?: return null
                    val cleanRaw = rawBody.trim()
                    if (cleanRaw.isEmpty() || cleanRaw == "null") return null
                    
                    val jsonToParse = if (cleanRaw.startsWith("{") || cleanRaw.startsWith("[")) {
                        cleanRaw
                    } else {
                        val unquoted = if (cleanRaw.startsWith("\"") && cleanRaw.endsWith("\"") && cleanRaw.length >= 2) {
                            cleanRaw.substring(1, cleanRaw.length - 1)
                                .replace("\\\\", "\\")
                                .replace("\\\"", "\"")
                                .replace("\\n", "\n")
                                .replace("\\r", "\r")
                        } else {
                            cleanRaw
                        }
                        try {
                            decompress(unquoted)
                        } catch (e: Exception) {
                            Log.e(TAG, "Gzip decompression failed, falling back to raw body", e)
                            unquoted
                        }
                    }
                    payloadAdapter.fromJson(jsonToParse)
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
                    val raw = response.body?.string()?.trim() ?: return null
                    if (raw == "null" || raw.isEmpty()) return null
                    if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length >= 2) {
                        raw.substring(1, raw.length - 1)
                            .replace("\\\\", "\\")
                            .replace("\\\"", "\"")
                    } else {
                        raw
                    }
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
     * Upload payload to Firebase cloud store
     */
    suspend fun uploadPayload(email: String, payload: SyncPayload): Boolean {
        if (email.isBlank()) return false
        val key = getSanitizedKey(email)
        val url = "${getBaseUrl()}users/$key.json"

        val uploadDirectSuccess = try {
            val json = payloadAdapter.toJson(payload)
            val compressedPayload = try {
                compress(json)
            } catch (e: Exception) {
                Log.e(TAG, "Gzip compression failed, falling back to raw json", e)
                json
            }

            // Wrap as a safe and valid JSON string format for Realtime Database
            val escaped = compressedPayload
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
            val jsonString = "\"$escaped\""
            
            val requestBody = jsonString.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .put(requestBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Uploaded sync payload successfully to Firebase for $email (Compressed: ${compressedPayload.length} chars, Original: ${json.length} chars)")
                    true
                } else {
                    Log.e(TAG, "Failed to upload sync payload to Firebase: Code ${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network exception uploading sync payload to Firebase", e)
            false
        }

        if (uploadDirectSuccess) {
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

            // Upload redirect keys to Firebase in parallel/asynchronously
            redirectIdentifiers.forEach { id ->
                val redirectKey = getSanitizedRedirectKey(id)
                val url = "${getBaseUrl()}users/$redirectKey.json"
                try {
                    val escapedEmail = primaryEmail.replace("\\", "\\\\").replace("\"", "\\\"")
                    val jsonString = "\"$escapedEmail\""
                    val requestBody = jsonString.toRequestBody("application/json".toMediaType())
                    val request = Request.Builder()
                        .url(url)
                        .put(requestBody)
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
     * Download payload from Firebase cloud store with redirection support
     */
    suspend fun downloadPayload(email: String): SyncPayload? = coroutineScope {
        if (email.isBlank()) return@coroutineScope null
        val trimmedRaw = email.trim().lowercase()

        // 1. Try reading directly first
        val directDeferred = async(Dispatchers.IO) {
            val directKey = getSanitizedKey(trimmedRaw)
            val directUrl = "${getBaseUrl()}users/$directKey.json"
            fetchPayloadByUrl(directUrl)
        }

        // 2. Try redirect key mapping
        val redirectDeferred = async(Dispatchers.IO) {
            val redirectKey = getSanitizedRedirectKey(trimmedRaw)
            val redirectUrl = "${getBaseUrl()}users/$redirectKey.json"
            val redirectedEmail = fetchStringByUrl(redirectUrl)
            if (!redirectedEmail.isNullOrBlank()) {
                val resolvedUrl = "${getBaseUrl()}users/${getSanitizedKey(redirectedEmail)}.json"
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
                val shortRedirectUrl = "${getBaseUrl()}users/$shortRedirectKey.json"
                val shortRedirectedEmail = fetchStringByUrl(shortRedirectUrl)
                if (!shortRedirectedEmail.isNullOrBlank()) {
                    val resolvedUrl = "${getBaseUrl()}users/${getSanitizedKey(shortRedirectedEmail)}.json"
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
            val url = "${getBaseUrl()}users.json?shallow=true"
            val request = Request.Builder().url(url).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val raw = response.body?.string() ?: return@coroutineScope emptyList()
                    if (raw == "null" || raw.trim().isEmpty()) return@coroutineScope emptyList()
                    
                    // Parse keys from JSON object like {"user_foo": true, "user_bar": true}
                    val regex = Regex("\"(user_[a-zA-Z0-9_]+)\"")
                    val keys = regex.findAll(raw).map { it.groupValues[1] }.toList().distinct()
                    
                    val deferreds = keys.map { key ->
                        async(Dispatchers.IO) {
                            try {
                                val userPayloadUrl = "${getBaseUrl()}users/$key.json"
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
            Log.e(TAG, "Failed to list keys from Firebase shallow option", e)
        }
        list.toList()
    }

    /**
     * Delete user payload from Firebase
     */
    suspend fun deletePayload(email: String): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val key = getSanitizedKey(email)
            val url = "${getBaseUrl()}users/$key.json"
            val request = Request.Builder()
                .url(url)
                .delete()
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network exception deleting sync payload from Firebase", e)
            false
        }
    }

    /**
     * Upload an individual image to its own key on Firebase to avoid bloating the main payload
     */
    suspend fun uploadIndividualImage(imageKey: String, base64Data: String): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (imageKey.isBlank() || base64Data.isBlank()) return@withContext false
        val url = "${getBaseUrl()}images/$imageKey.json"
        try {
            val escaped = base64Data
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
            val jsonString = "\"$escaped\""
            
            val requestBody = jsonString.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .put(requestBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Uploaded individual image successfully to Firebase for key: $imageKey")
                    true
                } else {
                    Log.e(TAG, "Failed to upload individual image to Firebase: Code ${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception uploading individual image to Firebase: $imageKey", e)
            false
        }
    }

    /**
     * Download an individual image base64 resource from its own key on Firebase
     */
    suspend fun downloadIndividualImage(imageKey: String): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (imageKey.isBlank()) return@withContext null
        val url = "${getBaseUrl()}images/$imageKey.json"
        try {
            val request = Request.Builder().url(url).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val rawStr = response.body?.string()?.trim() ?: return@withContext null
                    if (rawStr == "null" || rawStr.isEmpty()) return@withContext null
                    if (rawStr.startsWith("\"") && rawStr.endsWith("\"") && rawStr.length >= 2) {
                        rawStr.substring(1, rawStr.length - 1)
                            .replace("\\\\", "\\")
                            .replace("\\\"", "\"")
                            .replace("\\n", "\n")
                            .replace("\\r", "\r")
                    } else {
                        rawStr
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception downloading individual image from Firebase: $imageKey", e)
            null
        }
    }

    /**
     * Delete user mapping redirect for clean update of logins
     */
    suspend fun deleteRedirect(id: String): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (id.isBlank()) return@withContext false
        val redirectKey = getSanitizedRedirectKey(id)
        val url = "${getBaseUrl()}users/$redirectKey.json"
        try {
            val request = Request.Builder()
                .url(url)
                .delete()
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Deleted user mapping redirect for $id")
                    true
                } else {
                    Log.e(TAG, "Failed to delete redirect for $id, code ${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception deleting user mapping redirect for $id", e)
            false
        }
    }
}
