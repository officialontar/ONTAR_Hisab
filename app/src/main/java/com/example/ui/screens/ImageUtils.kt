package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import android.graphics.BitmapFactory
import android.util.Base64

@Composable
fun rememberImageModel(imageInput: String?): Any? {
    return remember(imageInput) {
        if (imageInput.isNullOrBlank()) {
            null
        } else if (imageInput.startsWith("data:") && imageInput.contains("base64,")) {
            try {
                val base64String = imageInput.substringAfter("base64,")
                val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            } catch (e: Exception) {
                // Fallback to original string if decoding fails
                imageInput
            }
        } else {
            imageInput
        }
    }
}
