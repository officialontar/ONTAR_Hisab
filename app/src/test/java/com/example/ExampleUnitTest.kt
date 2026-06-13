package com.example

import org.junit.Assert.*
import org.junit.Test
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

class ExampleUnitTest {
    @Test
    fun runWipeAllNonAdminData() {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
        val bucketId = "6h9NTtLDbTocxLkyC7Jpv6"
        val baseUrl = "https://kvdb.io/$bucketId/"

        println("Fetching list of keys from: $baseUrl")
        val listRequest = Request.Builder()
            .url(baseUrl)
            .get()
            .build()
        
        var rawKeys = ""
        client.newCall(listRequest).execute().use { response ->
            if (response.isSuccessful) {
                rawKeys = response.body?.string() ?: ""
            } else {
                println("Failed to fetch keys. Status code: ${response.code}")
                return
            }
        }

        // Clean and extract keys
        val cleanRaw = rawKeys.replace("[", "").replace("]", "")
        val keys = cleanRaw.split(Regex("[\n,\r]"))
            .map { it.replace("\"", "").trim() }
            .filter { it.isNotBlank() }
            .distinct()

        println("Found total of ${keys.size} keys on the cloud:")
        for (k in keys) {
            println("  - $k")
        }

        val adminKey = "user_mdanisujjamanontar_at_gmail_dot_com"
        
        var deletedUsersCount = 0
        var deletedRedirectsCount = 0
        var preservedCount = 0

        for (key in keys) {
            val isUserKey = key.startsWith("user_")
            val isRedirectKey = key.startsWith("redirect_")
            
            // Check if it belongs to main admin
            val isMainAdmin = key == adminKey || key.contains("mdanisujjamanontar")
            
            if (isMainAdmin) {
                println("PRESERVING ADMIN KEY: $key")
                preservedCount++
                continue
            }

            if (isUserKey || isRedirectKey) {
                println("DELETING CLOUD KEY: $key")
                val deleteRequest = Request.Builder()
                    .url("$baseUrl$key")
                    .delete()
                    .build()
                
                client.newCall(deleteRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        if (isUserKey) deletedUsersCount++ else deletedRedirectsCount++
                        println("Successfully deleted: $key")
                    } else {
                        println("Failed to delete key: $key (status ${response.code})")
                    }
                }
            } else {
                println("Skipping unrelated key: $key")
                preservedCount++
            }
        }

        println("=== CLEANUP PROCESS COMPLETED ===")
        println("Users Deleted: $deletedUsersCount")
        println("Redirects Deleted: $deletedRedirectsCount")
        println("Preserved Keys: $preservedCount")
    }
}
