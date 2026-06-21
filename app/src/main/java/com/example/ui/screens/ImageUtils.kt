package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import android.graphics.BitmapFactory
import android.util.Base64

object ImageCache {
    private val memoryCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    
    fun get(context: android.content.Context, key: String): String? {
        val mem = memoryCache[key]
        if (mem != null) return mem
        val prefs = context.getSharedPreferences("remote_image_cache", android.content.Context.MODE_PRIVATE)
        val saved = prefs.getString(key, null)
        if (saved != null) {
            memoryCache[key] = saved
            return saved
        }
        return null
    }
    
    fun put(context: android.content.Context, key: String, base64: String) {
        memoryCache[key] = base64
        val prefs = context.getSharedPreferences("remote_image_cache", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString(key, base64).apply()
    }
}

@Composable
fun rememberImageModel(imageInput: String?): Any? {
    if (imageInput.isNullOrBlank()) return null
    val cleanInput = imageInput.trim()
    val context = androidx.compose.ui.platform.LocalContext.current

    if (cleanInput.startsWith("remote_ref:")) {
        val key = cleanInput.substringAfter("remote_ref:")
        
        var resolvedBase64 by androidx.compose.runtime.remember(key) {
            androidx.compose.runtime.mutableStateOf(ImageCache.get(context, key))
        }
        
        androidx.compose.runtime.LaunchedEffect(key) {
            if (resolvedBase64 == null) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val base64 = com.example.api.CloudSyncEngine.downloadIndividualImage(key)
                        if (!base64.isNullOrBlank()) {
                            ImageCache.put(context, key, base64)
                            resolvedBase64 = base64
                        }
                    } catch(e: Exception) {
                        android.util.Log.e("ImageUtils", "Failed to resolve image $key", e)
                    }
                }
            }
        }
        
        val displayInput = resolvedBase64 ?: ""
        return androidx.compose.runtime.remember(displayInput) {
            if (displayInput.isNotBlank()) {
                decodeBase64ToBitmap(displayInput)
            } else {
                null
            }
        }
    }

    return remember(cleanInput) {
        decodeBase64ToBitmap(cleanInput)
    }
}

private fun decodeBase64ToBitmap(input: String): Any? {
    val cleanInput = input.trim()
    if (cleanInput.startsWith("data:") && cleanInput.contains("base64,")) {
        try {
            val base64String = cleanInput.substringAfter("base64,")
                .replace("\n", "")
                .replace("\r", "")
                .replace(" ", "")
                .replace("\\", "")
                .trim()
            
            var decodedBytes: ByteArray? = null
            val flagsList = listOf(Base64.DEFAULT, Base64.NO_WRAP, Base64.NO_PADDING, Base64.URL_SAFE)
            for (flag in flagsList) {
                try {
                    decodedBytes = Base64.decode(base64String, flag)
                    if (decodedBytes != null && decodedBytes.isNotEmpty()) {
                        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                        if (bitmap != null) {
                            return bitmap
                        }
                    }
                } catch (e: Exception) {
                    // try next
                }
            }
            return cleanInput
        } catch (e: Exception) {
            return cleanInput
        }
    } else if (!cleanInput.startsWith("http") && !cleanInput.startsWith("content://") && !cleanInput.startsWith("file://") && !cleanInput.startsWith("/") && cleanInput.length > 50) {
        try {
            val base64String = cleanInput
                .replace("\n", "")
                .replace("\r", "")
                .replace(" ", "")
                .replace("\\", "")
                .trim()
            
            var decodedBytes: ByteArray? = null
            val flagsList = listOf(Base64.DEFAULT, Base64.NO_WRAP, Base64.NO_PADDING, Base64.URL_SAFE)
            for (flag in flagsList) {
                try {
                    decodedBytes = Base64.decode(base64String, flag)
                    if (decodedBytes != null && decodedBytes.isNotEmpty()) {
                        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                        if (bitmap != null) {
                            return bitmap
                        }
                    }
                } catch (e: Exception) {
                    // try next
                }
            }
            return cleanInput
        } catch (e: Exception) {
            return cleanInput
        }
    } else {
        return cleanInput
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
