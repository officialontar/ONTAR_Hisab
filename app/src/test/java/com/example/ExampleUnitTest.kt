package com.example

import org.junit.Assert.*
import org.junit.Test
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testKvdbKeys() {
    try {
      val client = OkHttpClient()
      val bucketId = "DDCxA8wXRmxjnpLiqbu2Vp"
      
      // Attempt to read from the bucket
      val readRequest = Request.Builder()
        .url("https://kvdb.io/$bucketId/")
        .get()
        .build()
        
      client.newCall(readRequest).execute().use { response ->
        println("KVDB_GET_STATUS: ${response.code}")
        println("KVDB_GET_BODY: ${response.body?.string()}")
      }
      
      // Attempt a write
      val writeBody = "test_value_123".toRequestBody("text/plain".toMediaType())
      val writeRequest = Request.Builder()
        .url("https://kvdb.io/$bucketId/test_key")
        .post(writeBody)
        .build()
        
      client.newCall(writeRequest).execute().use { response ->
        println("KVDB_POST_STATUS: ${response.code}")
        println("KVDB_POST_BODY: ${response.body?.string()}")
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }
}
