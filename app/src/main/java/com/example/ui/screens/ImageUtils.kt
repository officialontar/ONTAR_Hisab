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
        } else if (!imageInput.startsWith("http") && !imageInput.startsWith("content://") && !imageInput.startsWith("file://") && !imageInput.startsWith("/") && imageInput.length > 50) {
            // Decodes raw base64 string without direct jpeg prefix if applicable
            try {
                val decodedBytes = Base64.decode(imageInput, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size) ?: imageInput
            } catch (e: Exception) {
                imageInput
            }
        } else {
            imageInput
        }
    }
}

fun resolveBestPhoto(local: String?, remote: String?): String? {
    val l = local?.trim() ?: ""
    val r = remote?.trim() ?: ""
    
    val isLocalBase64 = l.startsWith("data:") || (l.length > 100 && !l.startsWith("content://") && !l.startsWith("file://") && !l.startsWith("/") && !l.startsWith("http"))
    val isRemoteBase64 = r.startsWith("data:") || (r.length > 100 && !r.startsWith("content://") && !r.startsWith("file://") && !r.startsWith("/") && !r.startsWith("http"))
    
    if (isLocalBase64 && !isRemoteBase64) return local
    if (isRemoteBase64 && !isLocalBase64) return remote
    if (isLocalBase64 && isRemoteBase64) {
        return if (l.length >= r.length) local else remote
    }
    
    if (l.isNotEmpty() && !l.startsWith("http")) return local
    if (r.isNotEmpty() && !r.startsWith("http")) return remote
    if (l.isNotEmpty() && l.contains("unsplash.com")) return local
    if (r.isNotEmpty() && r.contains("unsplash.com")) return remote
    
    return if (l.isNotEmpty()) local else if (r.isNotEmpty()) remote else null
}
