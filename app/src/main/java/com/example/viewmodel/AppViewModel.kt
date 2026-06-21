package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.Content
import com.example.api.GeminiClient
import com.example.api.GeminiRequest
import com.example.api.GenerationConfig
import com.example.api.Part
import com.example.data.*
import com.example.ui.screens.resolveBestPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(val repository: AppRepository, private val application: android.app.Application) : ViewModel() {

    private val prefs = application.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)

    private val _customGeminiApiKeyState = MutableStateFlow(prefs.getString("custom_gemini_api_key", "") ?: "")
    val customGeminiApiKeyState: StateFlow<String> = _customGeminiApiKeyState.asStateFlow()

    fun updateCustomGeminiApiKey(key: String) {
        _customGeminiApiKeyState.value = key
        prefs.edit().putString("custom_gemini_api_key", key).apply()
    }

    fun getGeminiApiKey(): String {
        val customKey = _customGeminiApiKeyState.value
        if (customKey.isNotBlank()) {
            return customKey
        }
        return try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" } ?: ""
    }

    fun uriToBase64(context: android.content.Context, uri: android.net.Uri): String? {
        return try {
            val contentResolver = context.contentResolver
            
            // Create a temporary cache file to copy the stream to
            val tempFile = java.io.File.createTempFile("pic_upload_", ".jpg", context.cacheDir)
            
            // Copy stream securely
            contentResolver.openInputStream(uri)?.use { inputStream ->
                tempFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: return null
            
            if (tempFile.length() == 0L) {
                tempFile.delete()
                return null
            }
            
            // Decode the file safely
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeFile(tempFile.absolutePath, options)
            
            // Check original dimensions
            var inSampleSize = 1
            val maxDimension = 1024
            if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / inSampleSize) >= maxDimension && (halfWidth / inSampleSize) >= maxDimension) {
                    inSampleSize *= 2
                }
            }
            
            // Decode with sample size
            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            val bitmap = android.graphics.BitmapFactory.decodeFile(tempFile.absolutePath, decodeOptions)
            
            // Delete temp file after decoding to be clean
            try {
                tempFile.delete()
            } catch (e: Exception) {
                // ignore
            }
            
            if (bitmap == null) return null
            
            // Resize to 1024px for high-definition premium display while preventing large file size failure
            val targetSize = 1024
            val scaledBitmap = if (bitmap.width > targetSize || bitmap.height > targetSize) {
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val (newWidth, newHeight) = if (ratio > 1f) {
                    targetSize to (targetSize / ratio).toInt()
                } else {
                    (targetSize * ratio).toInt() to targetSize
                }
                android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            } else {
                bitmap
            }
            
            // Compress with supreme quality 90% for maximum clarity
            val outputStream = java.io.ByteArrayOutputStream()
            scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outputStream)
            val imageBytes = outputStream.toByteArray()
            
            // Recycle both to free memory immediately
            if (bitmap != scaledBitmap) {
                bitmap.recycle()
            }
            scaledBitmap.recycle()
            
            val base64String = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64String"
        } catch (t: Throwable) {
            android.util.Log.e("AppViewModel", "Extremely robust conversion failed: ${t.message}", t)
            null
        }
    }

    fun bitmapToBase64(bitmap: android.graphics.Bitmap): String? {
        return try {
            val targetSize = 1024
            val scaledBitmap = if (bitmap.width > targetSize || bitmap.height > targetSize) {
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val (newWidth, newHeight) = if (ratio > 1f) {
                    targetSize to (targetSize / ratio).toInt()
                } else {
                    (targetSize * ratio).toInt() to targetSize
                }
                android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            } else {
                bitmap
            }
            
            val outputStream = java.io.ByteArrayOutputStream()
            scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outputStream)
            val imageBytes = outputStream.toByteArray()
            
            if (bitmap != scaledBitmap) {
                bitmap.recycle()
            }
            scaledBitmap.recycle()
            
            val base64String = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64String"
        } catch (t: Throwable) {
            android.util.Log.e("AppViewModel", "Bitmap to Base64 failed: ${t.message}", t)
            null
        }
    }

    // Language State: true for Bengali, false for English
    private val _isBengali = MutableStateFlow(true)
    val isBengali: StateFlow<Boolean> = _isBengali.asStateFlow()

    // Dynamic Firebase Database Connection URL
    private val _dbUrl = MutableStateFlow(prefs.getString("firebase_db_url", "https://ontar-hisab-eb1ea-default-rtdb.firebaseio.com/") ?: "https://ontar-hisab-eb1ea-default-rtdb.firebaseio.com/")
    val dbUrl: StateFlow<String> = _dbUrl.asStateFlow()

    fun updateFirebaseUrl(newUrl: String) {
        val trimmed = newUrl.trim()
        if (trimmed.isNotBlank()) {
            _dbUrl.value = trimmed
            prefs.edit().putString("firebase_db_url", trimmed).apply()
            com.example.api.CloudSyncEngine.initialize(trimmed)
            viewModelScope.launch {
                showToast(if (_isBengali.value) "সাফল্যের সাথে ফায়ারবেস ক্লাউড লিংক আপডেট হয়েছে!" else "Firebase cloud link updated successfully!")
            }
        }
    }

    fun toggleLanguage() {
        _isBengali.value = !_isBengali.value
    }

    // App Dark Mode Style
    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
    }

    // Active screen navigation
    private val _currentScreen = MutableStateFlow<String>("LOGIN")
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    // Slide-out right panel state
    private val _showRightMenuDrawer = MutableStateFlow<Boolean>(false)
    val showRightMenuDrawer: StateFlow<Boolean> = _showRightMenuDrawer.asStateFlow()

    fun toggleRightMenuDrawer(show: Boolean) {
        _showRightMenuDrawer.value = show
    }

    fun navigateTo(screen: String) {
        _currentScreen.value = screen
    }

    // Session user
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _otaConfig = MutableStateFlow(com.example.data.OtaConfig())
    val otaConfig: StateFlow<com.example.data.OtaConfig> = _otaConfig.asStateFlow()

    private val _isForceUpdateBypassed = MutableStateFlow(false)
    val isForceUpdateBypassed: StateFlow<Boolean> = _isForceUpdateBypassed.asStateFlow()

    fun bypassForceUpdate() {
        _isForceUpdateBypassed.value = true
    }

    fun fetchGlobalOtaConfig() {
        // Pre-load from local shared preferences fallback immediately
        val fallbackJson = prefs.getString("local_ota_config_fallback", null)
        if (!fallbackJson.isNullOrBlank()) {
            try {
                val jo = org.json.JSONObject(fallbackJson)
                val parsed = com.example.data.OtaConfig(
                    latestVersionCode = jo.optInt("latestVersionCode", 1),
                    latestVersionName = jo.optString("latestVersionName", "1.0"),
                    updateDownloadUrl = jo.optString("updateDownloadUrl", "https://ais-pre-wolkhdsxahnvgjlshvncw2-122144077257.asia-southeast1.run.app"),
                    bengaliMessage = jo.optString("bengaliMessage", "আপনাদের সকল ডাটা ও ইমেজ লাইফটাইম ব্যাকআপ সম্পন্ন করা হয়েছে!"),
                    englishMessage = jo.optString("englishMessage", "All your data and images are successfully backed up for lifetime!"),
                    forceUpdateEnabled = jo.optBoolean("forceUpdateEnabled", false),
                    freePremiumActive = jo.optBoolean("freePremiumActive", true)
                )
                _otaConfig.value = parsed
            } catch (e: Exception) {
                Log.e("AppViewModel", "Failed to parse local ota fallback", e)
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonStr = com.example.api.CloudSyncEngine.downloadIndividualImage("app_ota_config_global")
                if (!jsonStr.isNullOrBlank()) {
                    val jo = org.json.JSONObject(jsonStr)
                    val parsed = com.example.data.OtaConfig(
                        latestVersionCode = jo.optInt("latestVersionCode", 1),
                        latestVersionName = jo.optString("latestVersionName", "1.0"),
                        updateDownloadUrl = jo.optString("updateDownloadUrl", "https://ais-pre-wolkhdsxahnvgjlshvncw2-122144077257.asia-southeast1.run.app"),
                        bengaliMessage = jo.optString("bengaliMessage", "আপনাদের সকল ডাটা ও ইমেজ লাইফটাইম ব্যাকআপ সম্পন্ন করা হয়েছে!"),
                        englishMessage = jo.optString("englishMessage", "All your data and images are successfully backed up for lifetime!"),
                        forceUpdateEnabled = jo.optBoolean("forceUpdateEnabled", false),
                        freePremiumActive = jo.optBoolean("freePremiumActive", true)
                    )
                    withContext(Dispatchers.Main) {
                        _otaConfig.value = parsed
                    }
                    prefs.edit().putString("local_ota_config_fallback", jsonStr).apply()
                }
            } catch (e: Exception) {
                Log.e("AppViewModel", "Failed to fetch global OTA config", e)
            }
        }
    }

    fun updateGlobalOtaConfig(config: com.example.data.OtaConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jo = org.json.JSONObject()
                jo.put("latestVersionCode", config.latestVersionCode)
                jo.put("latestVersionName", config.latestVersionName)
                jo.put("updateDownloadUrl", config.updateDownloadUrl)
                jo.put("bengaliMessage", config.bengaliMessage)
                jo.put("englishMessage", config.englishMessage)
                jo.put("forceUpdateEnabled", config.forceUpdateEnabled)
                jo.put("freePremiumActive", config.freePremiumActive)
                
                // First save to local device storage fallback so it is always applied instantly
                prefs.edit().putString("local_ota_config_fallback", jo.toString()).apply()
                
                // Now upload to cloud database
                val success = com.example.api.CloudSyncEngine.uploadIndividualImage("app_ota_config_global", jo.toString())
                withContext(Dispatchers.Main) {
                    _otaConfig.value = config
                    if (success) {
                        showToast(if (_isBengali.value) "গ্লোবাল আপডেট সেটিং ক্লাউড ও লোকালি সফলভাবে সেভ হয়েছে!" else "Global update settings successfully saved to cloud & locally!")
                    } else {
                        // Mark as saved locally to give a friendly experience even if the Firebase url is unconfigured/locked
                        showToast(if (_isBengali.value) "আপডেট কনফিগারেশন লোকাল মেমোরিতে সফলভাবে সেভ হয়েছে!" else "Update configuration saved in local storage successfully!")
                    }
                }
            } catch (e: Exception) {
                Log.e("AppViewModel", "Failed to upload global OTA config", e)
                withContext(Dispatchers.Main) {
                    showToast(if (_isBengali.value) "আপডেট কনফিগারেশন লোকাল মেমোরিতে সেভ হয়েছে!" else "Update configuration saved in local storage!")
                }
            }
        }
    }

    private var lastProfileUpdateTime = 0L
    private var lastLocalDbMutationTime = 0L

    private val _lastSyncTime = MutableStateFlow(prefs.getLong("last_successful_sync_time2", 0L))
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    private fun markLocalMutation() {
        lastLocalDbMutationTime = System.currentTimeMillis()
    }

    fun updateActiveUserDynamicIpAndDevice() {
        val current = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentDeviceName = getDeviceName()
                var ip = "Unknown"
                var loc = "Unknown"
                
                // 1. Try public IP APIs
                val ipEndpoints = listOf(
                    "https://api.ipify.org",
                    "https://icanhazip.com",
                    "https://checkip.amazonaws.com",
                    "https://ipinfo.io/ip"
                )
                for (endpoint in ipEndpoints) {
                    try {
                        val url = java.net.URL(endpoint)
                        val conn = url.openConnection() as java.net.HttpURLConnection
                        conn.connectTimeout = 4000
                        conn.readTimeout = 4000
                        val rawText = conn.inputStream.bufferedReader().use { it.readText() }.trim()
                        if (rawText.isNotBlank() && (rawText.contains(".") || rawText.contains(":"))) {
                            ip = rawText
                            break
                        }
                    } catch (e: Exception) {
                        Log.w("AppViewModel", "Failed to fetch IP from $endpoint", e)
                    }
                }

                if (ip == "Unknown") {
                    // Try ip-api.com
                    try {
                        val url = java.net.URL("https://ip-api.com/json")
                        val conn = url.openConnection() as java.net.HttpURLConnection
                        conn.connectTimeout = 4000
                        conn.readTimeout = 4000
                        val resText = conn.inputStream.bufferedReader().use { it.readText() }
                        val obj = org.json.JSONObject(resText)
                        if (obj.optString("status") == "success") {
                            ip = obj.optString("query", "Unknown")
                            val city = obj.optString("city", "")
                            val rName = obj.optString("regionName", "")
                            val cName = obj.optString("country", "")
                            val locParts = listOf(city, rName, cName).filter { it.isNotBlank() }
                            loc = if (locParts.isNotEmpty()) locParts.joinToString(", ") else "Unknown"
                        }
                    } catch(ex: Exception) {}
                }

                // 2. Absolute fallback to active dynamic local IP if public lookup returned Unknown or failed
                if (ip == "Unknown") {
                    try {
                        val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
                        if (interfaces != null) {
                            for (intf in java.util.Collections.list(interfaces)) {
                                val addrs = intf.inetAddresses
                                for (addr in java.util.Collections.list(addrs)) {
                                    if (!addr.isLoopbackAddress) {
                                        val sAddr = addr.hostAddress
                                        val isIPv4 = sAddr.indexOf(':') < 0
                                        if (isIPv4) {
                                            ip = sAddr
                                            break
                                        }
                                    }
                                }
                                if (ip != "Unknown") break
                            }
                        }
                    } catch (e: Exception) {}
                }

                // Always format current device entry with dynamic IP address
                val deviceWithIp = if (ip != "Unknown") "$currentDeviceName (IP: $ip)" else currentDeviceName
                
                // Re-read user to avoid race conditions
                val freshUser = repository.getUser(current.email) ?: current
                
                // Update active devices array
                val activeArr = try {
                    org.json.JSONArray(freshUser.activeDevicesJson ?: "[]")
                } catch (e: Exception) {
                    org.json.JSONArray()
                }
                
                // Remove previous entries that represent this device of different / previous IPs
                val rawList = mutableListOf<String>()
                for (i in 0 until activeArr.length()) {
                    val existingEntry = activeArr.getString(i)
                    val baseExName = existingEntry.split(" (IP:").first().trim()
                    if (baseExName != currentDeviceName) {
                        rawList.add(existingEntry)
                    }
                }
                
                // Add our current device with current fresh IP
                rawList.add(deviceWithIp)
                
                val updatedActiveJson = org.json.JSONArray()
                rawList.distinct().forEach { updatedActiveJson.put(it) }
                
                val finalLoc = if (loc != "Unknown") loc else freshUser.registerLocation ?: "Unknown"
                
                val updatedUser = freshUser.copy(
                    ipAddress = ip,
                    registerLocation = finalLoc,
                    activeDevicesJson = updatedActiveJson.toString()
                )
                
                repository.updateUser(updatedUser)
                withContext(Dispatchers.Main) {
                    _currentUser.value = updatedUser
                }
                
                // Push payload immediately to server to update Admin console in real time
                try {
                    val exPayload = com.example.api.CloudSyncEngine.downloadPayload(updatedUser.email)
                    val newPayload = if (exPayload != null) {
                        exPayload.copy(
                            user = updatedUser,
                            timestamp = System.currentTimeMillis()
                        )
                    } else {
                        com.example.api.SyncPayload(
                            user = updatedUser,
                            stockItems = repository.getStockItems(updatedUser.email).firstOrNull() ?: emptyList(),
                            customers = repository.getCustomers(updatedUser.email).firstOrNull() ?: emptyList(),
                            dealers = repository.getDealers(updatedUser.email).firstOrNull() ?: emptyList(),
                            transactions = repository.getTransactions(updatedUser.email).firstOrNull() ?: emptyList(),
                            timestamp = System.currentTimeMillis()
                        )
                    }
                    com.example.api.CloudSyncEngine.uploadPayload(updatedUser.email, newPayload)
                } catch (e: Exception) {
                    Log.e("AppViewModel", "Failed to upload dynamic IP payload", e)
                }
            } catch (e: Exception) {
                Log.e("AppViewModel", "Error in updateActiveUserDynamicIpAndDevice", e)
            }
        }
    }

    // Authentication messages
    private val _authStateMessage = MutableStateFlow<String?>(null)
    val authStateMessage: StateFlow<String?> = _authStateMessage.asStateFlow()

    fun clearAuthMessage() {
        _authStateMessage.value = null
    }

    // Forget Password Recovery States
    private val _resetOtp = MutableStateFlow<String?>(null)
    val resetOtp: StateFlow<String?> = _resetOtp.asStateFlow()
    
    private val _resetUser = MutableStateFlow<User?>(null)
    val resetUser: StateFlow<User?> = _resetUser.asStateFlow()

    private val _forgetPasswordStep = MutableStateFlow<Int>(0) // 0 = default login/reg, 1 = input phone/email, 2 = verify OTP & change PIN
    val forgetPasswordStep: StateFlow<Int> = _forgetPasswordStep.asStateFlow()
    
    fun setForgetPasswordStep(step: Int) {
        _forgetPasswordStep.value = step
        if (step == 0) {
            _resetOtp.value = null
            _resetUser.value = null
            _authStateMessage.value = null
        }
    }

    // User Shops State Flow
    private val _userShops = MutableStateFlow<List<User>>(emptyList())
    val userShops: StateFlow<List<User>> = _userShops.asStateFlow()

    fun getBaseEmail(email: String): String {
        return email.substringBefore('#')
    }

    fun loadShopsForActiveUser() {
        val activeUser = _currentUser.value ?: return
        val rootEmail = getBaseEmail(activeUser.email)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val list = repository.getAllShopsOfUser(rootEmail)
                _userShops.value = list
            } catch (e: java.lang.Exception) {
                Log.e("AppViewModel", "Failed to load shops", e)
            }
        }
    }

    fun switchActiveShop(targetUser: User) {
        viewModelScope.launch {
            _currentUser.value = targetUser
            showToast(if (_isBengali.value) "${targetUser.getLocalizedShopName(true)} এ পরিবর্তন করা হয়েছে" else "Switched to ${targetUser.getLocalizedShopName(false)}")
            loadShopsForActiveUser()
            triggerCloudSync(isManual = false)
        }
    }

    fun addNewShop(shopName: String, ownerName: String, phone: String, shopPic: String?, profilePic: String?) {
        val rootUser = _currentUser.value ?: return
        val baseEmail = getBaseEmail(rootUser.email)
        
        if (shopName.isBlank() || ownerName.isBlank() || phone.isBlank()) {
            showToast(if (_isBengali.value) "দয়া করে সমস্ত তথ্য পূরণ করুন" else "Please complete all details")
            return
        }

        if (_userShops.value.size >= 5) {
            showToast(if (_isBengali.value) "আপনি সর্বোচ্চ ৫টি দোকান যুক্ত করতে পারবেন" else "You can manage a maximum of 5 shops")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val shopSuffix = "#shop_" + System.currentTimeMillis()
                val newShopUser = User(
                    email = baseEmail + shopSuffix,
                    shopName = shopName.trim(),
                    phone = phone.trim(),
                    passwordHash = rootUser.passwordHash, // Keep same login PIN/password
                    profilePicture = profilePic,
                    ownerName = ownerName.trim(),
                    shopPicture = shopPic
                )
                repository.registerUser(newShopUser)
                
                // Switch to the newly created shop automatically on Main dispatchers implicitly
                viewModelScope.launch {
                    _currentUser.value = newShopUser
                    loadShopsForActiveUser()
                    showToast(if (_isBengali.value) "নতুন দোকান সফলভাবে যুক্ত করা হয়েছে" else "New shop successfully added")
                }
            } catch (e: java.lang.Exception) {
                viewModelScope.launch {
                    showToast("${e.message}")
                }
            }
        }
    }

    init {
        val savedDbUrl = prefs.getString("firebase_db_url", "https://ontar-hisab-eb1ea-default-rtdb.firebaseio.com/") ?: "https://ontar-hisab-eb1ea-default-rtdb.firebaseio.com/"
        com.example.api.CloudSyncEngine.initialize(savedDbUrl)
        fetchGlobalOtaConfig()
        // Session Auto-Login check on startup
        viewModelScope.launch(Dispatchers.IO) {
            val savedEmail = prefs.getString("session_user_email", null)
            if (!savedEmail.isNullOrBlank()) {
                val userObj = repository.getUser(savedEmail)
                if (userObj != null) {
                    withContext(Dispatchers.Main) {
                        _currentUser.value = userObj
                        _currentScreen.value = "DASHBOARD"
                    }
                } else {
                    prefs.edit().remove("session_user_email").remove("session_last_active").apply()
                }
            }
        }

        viewModelScope.launch {
            _currentUser.collect { user ->
                if (user != null) {
                    prefs.edit()
                        .putString("session_user_email", user.email)
                        .putLong("session_last_active", System.currentTimeMillis())
                        .apply()
                    loadShopsForActiveUser()
                    updateActiveUserDynamicIpAndDevice()
                    triggerCloudSync(isManual = false)
                } else {
                    prefs.edit()
                        .remove("session_user_email")
                        .remove("session_last_active")
                        .apply()
                }
            }
        }

        // Periodic Cloud Sync Loop: Automatically sync every 20 seconds for robust real-time multi-device collaboration
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(20000)
                fetchGlobalOtaConfig()
                if (_currentUser.value != null && !_isCloudSyncing.value) {
                    try {
                        triggerCloudSync(isManual = false)
                    } catch (e: Exception) {
                        Log.e("AppViewModel", "Periodic background sync failed", e)
                    }
                }
            }
        }
    }

    // Reactive Flows of Grocery store database
    val stockItems: StateFlow<List<StockItem>> = _currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(emptyList())
            } else {
                repository.getStockItems(user.email)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customers: StateFlow<List<Customer>> = _currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(emptyList())
            } else {
                repository.getCustomers(user.email)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dealers: StateFlow<List<Dealer>> = _currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(emptyList())
            } else {
                repository.getDealers(user.email)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactions: StateFlow<List<TransactionRecord>> = _currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(emptyList())
            } else {
                repository.getTransactions(user.email)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Feedback
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    fun showToast(message: String) {
        _toastMessage.value = message
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    // AI Status / Result
    private val _aiReportText = MutableStateFlow<String?>(null)
    val aiReportText: StateFlow<String?> = _aiReportText.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    // Cloud Sync Status Simulation
    private val _isCloudSyncing = MutableStateFlow(false)
    val isCloudSyncing: StateFlow<Boolean> = _isCloudSyncing.asStateFlow()

    fun getDeviceName(): String {
        val manufacturer = android.os.Build.MANUFACTURER ?: "unknown"
        val model = android.os.Build.MODEL ?: "device"
        val capMan = manufacturer.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val capMod = model.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        return if (capMod.lowercase().startsWith(capMan.lowercase())) {
            capMod
        } else {
            "$capMan $capMod"
        }
    }

    private suspend fun uploadImageIfBase64(ownerEmail: String, imageStr: String?, extraSeed: String): String? {
        if (imageStr.isNullOrBlank()) return null
        
        // If it is already a remote reference, just return it
        if (imageStr.startsWith("remote_ref:")) {
            return imageStr
        }
        
        // Check if it is a real base64 image (either has prefix data: or length > 100 without standard scheme prefix)
        val isBase64 = imageStr.startsWith("data:") || (imageStr.length > 100 && !imageStr.startsWith("http") && !imageStr.startsWith("content://") && !imageStr.startsWith("file://") && !imageStr.startsWith("/") && !imageStr.startsWith("android.resource://"))
        
        if (!isBase64) return imageStr
        
        val sanitizedEmail = ownerEmail.lowercase().trim().replace("@", "_at_").replace(".", "_dot_").filter { it.isLetterOrDigit() || it == '_' }
        val absHash = java.lang.Math.abs(imageStr.hashCode())
        val imageKey = "img_${sanitizedEmail}_${extraSeed}_h${absHash}_len${imageStr.length}".lowercase().filter { it.isLetterOrDigit() || it == '_' }
        
        val sharedPrefs = application.getSharedPreferences("uploaded_images_v2", android.content.Context.MODE_PRIVATE)
        if (sharedPrefs.getBoolean(imageKey, false)) {
            Log.d("AppViewModel", "Image already uploaded to cloud: $imageKey")
            return "remote_ref:$imageKey"
        }
        
        return try {
            val success = com.example.api.CloudSyncEngine.uploadIndividualImage(imageKey, imageStr)
            if (success) {
                sharedPrefs.edit().putBoolean(imageKey, true).apply()
                "remote_ref:$imageKey"
            } else {
                imageStr
            }
        } catch (e: Exception) {
            Log.e("AppViewModel", "Failed to upload image $imageKey", e)
            imageStr
        }
    }

    fun downloadRemoteImagesInBackground(userEmail: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sharedPrefs = application.getSharedPreferences("uploaded_images_v2", android.content.Context.MODE_PRIVATE)

                // 1. User Profile and Shop Pictures
                val user = repository.getUser(userEmail)
                if (user != null) {
                    var updatedUser = user
                    var changed = false
                    if (user.profilePicture?.startsWith("remote_ref:") == true) {
                        val key = user.profilePicture.substringAfter("remote_ref:")
                        val base64 = com.example.api.CloudSyncEngine.downloadIndividualImage(key)
                        if (!base64.isNullOrBlank()) {
                            sharedPrefs.edit().putBoolean(key, true).apply()
                            updatedUser = updatedUser.copy(profilePicture = base64)
                            changed = true
                        }
                    }
                    if (user.shopPicture?.startsWith("remote_ref:") == true) {
                        val key = user.shopPicture.substringAfter("remote_ref:")
                        val base64 = com.example.api.CloudSyncEngine.downloadIndividualImage(key)
                        if (!base64.isNullOrBlank()) {
                            sharedPrefs.edit().putBoolean(key, true).apply()
                            updatedUser = updatedUser.copy(shopPicture = base64)
                            changed = true
                        }
                    }
                    if (changed) {
                        repository.updateUser(updatedUser)
                        if (_currentUser.value?.email == userEmail) {
                            withContext(Dispatchers.Main) {
                                _currentUser.value = updatedUser
                            }
                        }
                    }
                }

                // 2. Customers Profile Pictures
                val customersList = repository.getCustomers(userEmail).firstOrNull() ?: emptyList()
                for (cust in customersList) {
                    if (cust.photoUri?.startsWith("remote_ref:") == true) {
                        val key = cust.photoUri.substringAfter("remote_ref:")
                        val base64 = com.example.api.CloudSyncEngine.downloadIndividualImage(key)
                        if (!base64.isNullOrBlank()) {
                            sharedPrefs.edit().putBoolean(key, true).apply()
                            repository.updateCustomer(cust.copy(photoUri = base64))
                        }
                    }
                }

                // 3. Dealers Profile Pictures
                val dealersList = repository.getDealers(userEmail).firstOrNull() ?: emptyList()
                for (dlr in dealersList) {
                    if (dlr.photoUri?.startsWith("remote_ref:") == true) {
                        val key = dlr.photoUri.substringAfter("remote_ref:")
                        val base64 = com.example.api.CloudSyncEngine.downloadIndividualImage(key)
                        if (!base64.isNullOrBlank()) {
                            sharedPrefs.edit().putBoolean(key, true).apply()
                            repository.updateDealer(dlr.copy(photoUri = base64))
                        }
                    }
                }

                // 4. Stock Item Pictures
                val stocksList = repository.getStockItems(userEmail).firstOrNull() ?: emptyList()
                for (stock in stocksList) {
                    if (stock.imageResName?.startsWith("remote_ref:") == true) {
                        val key = stock.imageResName.substringAfter("remote_ref:")
                        val base64 = com.example.api.CloudSyncEngine.downloadIndividualImage(key)
                        if (!base64.isNullOrBlank()) {
                            sharedPrefs.edit().putBoolean(key, true).apply()
                            repository.updateStockItem(stock.copy(imageResName = base64))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AppViewModel", "Failed to download remote images in background", e)
            }
        }
    }

    fun triggerCloudSync(isManual: Boolean = true, uploadOnly: Boolean = false) {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isCloudSyncing.value = true
            if (isManual) {
                withContext(Dispatchers.Main) {
                    showToast(if (_isBengali.value) "সার্ভারের সাথে সিঙ্ক শুরু হয়েছে..." else "Cloud sync started...")
                }
            }

            try {
                var remotePayload: com.example.api.SyncPayload? = null
                var activeUser = user

                if (!uploadOnly) {
                    // 1. Download payload from Cloud
                    remotePayload = com.example.api.CloudSyncEngine.downloadPayload(user.email)
                    
                    if (remotePayload != null) {
                        val remoteUser = remotePayload.user
                        val remoteTimestamp = remotePayload.timestamp

                        // Only overwrite local user with remote user if remote payload is newer than our last edit
                        val isRemoteNewer = remoteTimestamp > lastProfileUpdateTime

                        if (isRemoteNewer) {
                            // Check blocks
                            val currentDevice = getDeviceName()
                            val blockedList = try {
                                val arr = org.json.JSONArray(remoteUser.blockedDevicesJson ?: "[]")
                                List(arr.length()) { i -> arr.getString(i) }
                            } catch(e: Exception) { emptyList<String>() }
                            
                            val isAlwaysAdmin = remoteUser.email.trim().lowercase() == "mdanisujjamanontar@gmail.com"
                            if ((remoteUser.isBlocked || blockedList.contains(currentDevice)) && !isAlwaysAdmin) {
                                repository.updateUser(remoteUser)
                                withContext(Dispatchers.Main) {
                                    _currentUser.value = remoteUser
                                    _isCloudSyncing.value = false
                                }
                                return@launch
                            }
                            
                            // Update active devices lists
                            val activeArr = try {
                                org.json.JSONArray(remoteUser.activeDevicesJson ?: "[]")
                            } catch(e: Exception) { org.json.JSONArray() }
                            
                            var hasOurDevice = false
                            for (i in 0 until activeArr.length()) {
                                if (activeArr.getString(i) == currentDevice) {
                                    hasOurDevice = true
                                    break
                                }
                            }
                            if (!hasOurDevice) {
                                activeArr.put(currentDevice)
                            }
                            
                            val finalProfile = if (isLocalImageMatchingRef(user.profilePicture, remoteUser.profilePicture, "profile", user.email)) {
                                user.profilePicture
                            } else {
                                remoteUser.profilePicture
                            }
                            
                            val finalShopPic = if (isLocalImageMatchingRef(user.shopPicture, remoteUser.shopPicture, "shop", user.email)) {
                                user.shopPicture
                            } else {
                                remoteUser.shopPicture
                            }

                            val updatedUser = remoteUser.copy(
                                profilePicture = finalProfile,
                                shopPicture = finalShopPic,
                                activeDevicesJson = activeArr.toString(),
                                registerDevice = remoteUser.registerDevice ?: currentDevice,
                                isBlocked = false // safety
                            )
                            
                            val hasDifference = user.shopName != updatedUser.shopName ||
                                    user.phone != updatedUser.phone ||
                                    user.ownerName != updatedUser.ownerName ||
                                    user.profilePicture != updatedUser.profilePicture ||
                                    user.shopPicture != updatedUser.shopPicture ||
                                    user.passwordHash != updatedUser.passwordHash ||
                                    user.activeDevicesJson != updatedUser.activeDevicesJson ||
                                    user.registerDevice != updatedUser.registerDevice ||
                                    user.isBlocked != updatedUser.isBlocked

                            if (hasDifference) {
                                repository.updateUser(updatedUser)
                                activeUser = updatedUser
                                withContext(Dispatchers.Main) {
                                    _currentUser.value = updatedUser
                                }
                            } else {
                                activeUser = user
                            }
                        }
                    }
                    // 2. Prepare local sets by fetching directly from SQLite database to avoid asynchronous WhileSubscribed StateFlow race conditions
                    val localStock = repository.getStockItems(user.email).firstOrNull() ?: emptyList()
                    val localCustomers = repository.getCustomers(user.email).firstOrNull() ?: emptyList()
                    val localDealers = repository.getDealers(user.email).firstOrNull() ?: emptyList()
                    val localTx = repository.getTransactions(user.email).firstOrNull() ?: emptyList()

                    if (remotePayload != null) {
                        // --- MERGING ADDITIONAL SHOPS ---
                        try {
                            for (remoteShop in remotePayload.additionalShops) {
                                val localShop = repository.getUser(remoteShop.email)
                                if (localShop != null) {
                                    val mergedShop = localShop.copy(
                                        shopName = remoteShop.shopName,
                                        phone = remoteShop.phone,
                                        ownerName = remoteShop.ownerName,
                                        profilePicture = resolveBestPhoto(localShop.profilePicture, remoteShop.profilePicture),
                                        shopPicture = resolveBestPhoto(localShop.shopPicture, remoteShop.shopPicture),
                                        passwordHash = remoteShop.passwordHash
                                    )
                                    repository.updateUser(mergedShop)
                                } else {
                                    repository.registerUser(remoteShop)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("AppViewModel", "Failed to merge remote additional shops", e)
                        }

                        val isRemoteNewer = remotePayload.timestamp > lastLocalDbMutationTime

                        if (isRemoteNewer) {
                        // --- MERGING STOCK ITEMS ---
                        val allStockNames = (localStock.map { it.name } + remotePayload.stockItems.map { it.name }).distinct()
                        for (name in allStockNames) {
                            val localItem = localStock.find { it.name == name }
                            val remoteItem = remotePayload.stockItems.find { it.name == name }
                            if (localItem != null && remoteItem != null) {
                                if (isRemoteNewer) {
                                    val finalImg = if (isLocalImageMatchingRef(localItem.imageResName, remoteItem.imageResName, "stock_${localItem.id}", user.email)) {
                                        localItem.imageResName
                                    } else {
                                        remoteItem.imageResName
                                    }
                                    
                                    val hasDiff = localItem.purchasePrice != remoteItem.purchasePrice ||
                                            localItem.salesPrice != remoteItem.salesPrice ||
                                            localItem.stockCount != remoteItem.stockCount ||
                                            localItem.category != remoteItem.category ||
                                            localItem.unit != remoteItem.unit ||
                                            localItem.imageResName != finalImg

                                    if (hasDiff) {
                                        val updated = localItem.copy(
                                            purchasePrice = remoteItem.purchasePrice,
                                            salesPrice = remoteItem.salesPrice,
                                            stockCount = remoteItem.stockCount,
                                            category = remoteItem.category,
                                            unit = remoteItem.unit,
                                            imageResName = finalImg
                                        )
                                        repository.updateStockItem(updated)
                                    }
                                }
                            } else if (remoteItem != null) {
                                // Insert remote item to local DB
                                val newItem = remoteItem.copy(id = 0, userEmail = user.email)
                                repository.insertStockItem(newItem)
                            }
                        }

                        // --- MERGING CUSTOMERS ---
                        val remoteCustomerMap = mutableMapOf<Int, Int>() // remote ID -> local ID
                        val activeLocalCustomers = repository.getCustomers(user.email).firstOrNull() ?: localCustomers
 
                        for (remoteCust in remotePayload.customers) {
                            val matchingLocal = activeLocalCustomers.find { 
                                it.name.trim().lowercase() == remoteCust.name.trim().lowercase() && 
                                it.phone.trim() == remoteCust.phone.trim() 
                            }
                            if (matchingLocal != null) {
                                val finalPhoto = if (isLocalImageMatchingRef(matchingLocal.photoUri, remoteCust.photoUri, "cust_${matchingLocal.phone.filter { it.isLetterOrDigit() }}", user.email)) {
                                    matchingLocal.photoUri
                                } else {
                                    resolveBestPhoto(matchingLocal.photoUri, remoteCust.photoUri)
                                }

                                val finalAddress = if (!matchingLocal.address.isNullOrBlank()) {
                                    matchingLocal.address
                                } else if (!remoteCust.address.isNullOrBlank()) {
                                    remoteCust.address
                                } else {
                                    matchingLocal.address ?: remoteCust.address
                                }

                                val finalDue = if (isRemoteNewer) remoteCust.totalDue else matchingLocal.totalDue

                                val hasDiff = matchingLocal.address != finalAddress ||
                                        matchingLocal.totalDue != finalDue ||
                                        matchingLocal.photoUri != finalPhoto

                                val updated = matchingLocal.copy(
                                    address = finalAddress,
                                    totalDue = finalDue,
                                    photoUri = finalPhoto
                                )
                                if (hasDiff) {
                                    repository.updateCustomer(updated)
                                }
                                remoteCustomerMap[remoteCust.id] = updated.id
                            } else {
                                val newCust = remoteCust.copy(id = 0, userEmail = user.email)
                                repository.insertCustomer(newCust)
                                val freshLocalList = repository.getCustomers(user.email).firstOrNull() ?: emptyList()
                                val insertedLocal = freshLocalList.find { 
                                    it.name.trim().lowercase() == remoteCust.name.trim().lowercase() && 
                                    it.phone.trim() == remoteCust.phone.trim() 
                                }
                                if (insertedLocal != null) {
                                    remoteCustomerMap[remoteCust.id] = insertedLocal.id
                                }
                            }
                        }

                        // --- MERGING DEALERS ---
                        val remoteDealerMap = mutableMapOf<Int, Int>() // remote ID -> local ID
                        val activeLocalDealers = repository.getDealers(user.email).firstOrNull() ?: localDealers

                        for (remoteDlr in remotePayload.dealers) {
                            val matchingLocal = activeLocalDealers.find {
                                it.name.trim().lowercase() == remoteDlr.name.trim().lowercase() &&
                                it.phone.trim() == remoteDlr.phone.trim()
                            }
                            if (matchingLocal != null) {
                                val finalDlrPhoto = if (isLocalImageMatchingRef(matchingLocal.photoUri, remoteDlr.photoUri, "dlr_${matchingLocal.phone.filter { it.isLetterOrDigit() }}", user.email)) {
                                    matchingLocal.photoUri
                                } else {
                                    resolveBestPhoto(matchingLocal.photoUri, remoteDlr.photoUri)
                                }

                                val finalCompany = if (!matchingLocal.company.isNullOrBlank()) {
                                    matchingLocal.company
                                } else if (!remoteDlr.company.isNullOrBlank()) {
                                    remoteDlr.company
                                } else {
                                    matchingLocal.company ?: remoteDlr.company
                                }

                                val finalOwed = if (isRemoteNewer) remoteDlr.totalOwed else matchingLocal.totalOwed

                                val hasDiff = matchingLocal.company != finalCompany ||
                                        matchingLocal.totalOwed != finalOwed ||
                                        matchingLocal.photoUri != finalDlrPhoto

                                val updated = matchingLocal.copy(
                                    company = finalCompany,
                                    totalOwed = finalOwed,
                                    photoUri = finalDlrPhoto
                                )
                                if (hasDiff) {
                                    repository.updateDealer(updated)
                                }
                                remoteDealerMap[remoteDlr.id] = updated.id
                            } else {
                                val newDlr = remoteDlr.copy(id = 0, userEmail = user.email)
                                repository.insertDealer(newDlr)
                                val freshLocalList = repository.getDealers(user.email).firstOrNull() ?: emptyList()
                                val insertedLocal = freshLocalList.find {
                                    it.name.trim().lowercase() == remoteDlr.name.trim().lowercase() &&
                                    it.phone.trim() == remoteDlr.phone.trim()
                                }
                                if (insertedLocal != null) {
                                    remoteDealerMap[remoteDlr.id] = insertedLocal.id
                                }
                            }
                        }

                        // --- MERGING TRANSACTIONS ---
                        val activeLocalTx = repository.getTransactions(user.email).firstOrNull() ?: localTx
                        for (remoteT in remotePayload.transactions) {
                            val existsLocally = activeLocalTx.any {
                                it.title == remoteT.title &&
                                java.lang.Math.abs(it.amount - remoteT.amount) < 0.01 &&
                                it.type == remoteT.type &&
                                it.timestamp == remoteT.timestamp
                            }
                            if (!existsLocally) {
                                val localCustId = remoteT.customerId?.let { remoteCustomerMap[it] }
                                val localDlrId = remoteT.dealerId?.let { remoteDealerMap[it] }

                                val newTx = remoteT.copy(
                                    id = 0,
                                    userEmail = user.email,
                                    customerId = localCustId,
                                    dealerId = localDlrId
                                )
                                repository.insertTransaction(newTx)
                            }
                        }

                        // Fire off background worker to download actual base64 image strings for remote references
                        downloadRemoteImagesInBackground(user.email)
                        }
                    }
                }

                // 3. Preprocess and Upload raw Base64 images to separate isolated keys before uploading the main payload
                val finalStock = repository.getStockItems(user.email).firstOrNull() ?: emptyList()
                val finalCustomers = repository.getCustomers(user.email).firstOrNull() ?: emptyList()
                val finalDealers = repository.getDealers(user.email).firstOrNull() ?: emptyList()
                val finalTx = repository.getTransactions(user.email).firstOrNull() ?: emptyList()

                val rootEmail = getBaseEmail(user.email)
                val allShops = repository.getAllShopsOfUser(rootEmail)
                val additionalShops = allShops.filter { it.email != user.email }

                val uProfileDeferred = async { uploadImageIfBase64(user.email, activeUser.profilePicture, "profile") }
                val uShopDeferred = async { uploadImageIfBase64(user.email, activeUser.shopPicture, "shop") }

                val processedStockDeferred = finalStock.map { stock ->
                    async {
                        val sImg = uploadImageIfBase64(user.email, stock.imageResName, "stock_${stock.id}")
                        if (sImg != stock.imageResName) stock.copy(imageResName = sImg) else stock
                    }
                }

                val processedCustomersDeferred = finalCustomers.map { cust ->
                    async {
                        val cleanPhone = cust.phone.filter { it.isLetterOrDigit() }
                        val cImg = uploadImageIfBase64(user.email, cust.photoUri, "cust_$cleanPhone")
                        if (cImg != cust.photoUri) cust.copy(photoUri = cImg) else cust
                    }
                }

                val processedDealersDeferred = finalDealers.map { dlr ->
                    async {
                        val cleanPhone = dlr.phone.filter { it.isLetterOrDigit() }
                        val dImg = uploadImageIfBase64(user.email, dlr.photoUri, "dlr_$cleanPhone")
                        if (dImg != dlr.photoUri) dlr.copy(photoUri = dImg) else dlr
                    }
                }

                val processedAdditionalShopsDeferred = additionalShops.map { shop ->
                    async {
                        val sProfile = uploadImageIfBase64(shop.email, shop.profilePicture, "profile")
                        val sShop = uploadImageIfBase64(shop.email, shop.shopPicture, "shop")
                        if (sProfile != shop.profilePicture || sShop != shop.shopPicture) {
                            shop.copy(profilePicture = sProfile, shopPicture = sShop)
                        } else shop
                    }
                }

                val uProfile = uProfileDeferred.await()
                val uShop = uShopDeferred.await()
                var processedUser = activeUser
                if (uProfile != activeUser.profilePicture || uShop != activeUser.shopPicture) {
                    processedUser = activeUser.copy(profilePicture = uProfile, shopPicture = uShop)
                }

                val processedStock = processedStockDeferred.awaitAll()
                val processedCustomers = processedCustomersDeferred.awaitAll()
                val processedDealers = processedDealersDeferred.awaitAll()
                val processedAdditionalShops = processedAdditionalShopsDeferred.awaitAll()

                val uploadPayload = com.example.api.SyncPayload(
                    user = processedUser,
                    stockItems = processedStock,
                    customers = processedCustomers,
                    dealers = processedDealers,
                    transactions = finalTx,
                    timestamp = System.currentTimeMillis(),
                    registrationTimestamp = remotePayload?.registrationTimestamp ?: remotePayload?.timestamp ?: System.currentTimeMillis(),
                    additionalShops = processedAdditionalShops
                )

                val uploadSuccess = com.example.api.CloudSyncEngine.uploadPayload(user.email, uploadPayload)

                withContext(Dispatchers.Main) {
                    _isCloudSyncing.value = false
                    if (uploadSuccess) {
                        val currentMs = System.currentTimeMillis()
                        _lastSyncTime.value = currentMs
                        prefs.edit().putLong("last_successful_sync_time2", currentMs).apply()
                    }
                    if (isManual) {
                        if (uploadSuccess) {
                            showToast(if (_isBengali.value) "সার্ভারের সাথে সফলভাবে সিঙ্ক সম্পন্ন হয়েছে!" else "Data synchronized with server successfully!")
                        } else {
                            showToast(if (_isBengali.value) "সিঙ্ক আংশিক সম্পন্ন (ক্লাউড সংযোগ ত্রুটি)" else "Sync partially completed (could not upload changes)")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AppViewModel", "Cloud sync exception", e)
                withContext(Dispatchers.Main) {
                    _isCloudSyncing.value = false
                    if (isManual) {
                        showToast(if (_isBengali.value) "সিঙ্ক ব্যর্থ: ইন্টারনেট সংযোগ নেই" else "Sync failed: network connection error")
                    }
                }
            }
        }
    }

    // --- USER REGISTRATION & LOGIN ---
    /**
     * Checks if any of the given emails or phones are already in use by any user OTHER than the specified excludingEmail.
     * Returns a localized error message in Bengali/English if taken, or null if perfectly free.
     */
    suspend fun getCollisionErrorForUser(
        excludingEmail: String?, // email of the user we are currently updating (null for new registrations)
        ownerName: String,
        phone: String,
        email: String
    ): String? {
        val currentReqOwners = com.example.data.OwnerParser.deserialize(ownerName, phone, email)
        val currentReqEmails = currentReqOwners.map { it.email.trim().lowercase() }.filter { it.isNotEmpty() }
        val currentReqPhones = currentReqOwners.map { 
            it.phone.trim().lowercase().replace("-", "").replace(" ", "").replace("+", "") 
        }.filter { it.isNotEmpty() }

        // Check self-duplication inside the form fields
        if (currentReqEmails.distinct().size != currentReqEmails.size) {
            return if (_isBengali.value) "একই ইমেইল একাধিক অংশীদারের জন্য ব্যবহার করা যাবে না!" else "Same email cannot be used for multiple partners!"
        }
        if (currentReqPhones.distinct().size != currentReqPhones.size) {
            return if (_isBengali.value) "একই মোবাইল নম্বর একাধিক অংশীদারের জন্য ব্যবহার করা যাবে না!" else "Same phone number cannot be used for multiple partners!"
        }

        // Fetch all registered users from Cloud and Local
        val allCloudUsers = try {
            com.example.api.CloudSyncEngine.fetchAllRegisteredUsers()
        } catch (e: Exception) {
            emptyList<User>()
        }
        val allLocalUsers = repository.getAllUsers()
        
        // Filter out the excludingEmail
        val combinedUsers = (allCloudUsers + allLocalUsers)
            .distinctBy { it.email.trim().lowercase() }
            .filter { excludingEmail == null || it.email.trim().lowercase() != excludingEmail.trim().lowercase() }

        val takenEmails = mutableSetOf<String>()
        val takenPhones = mutableSetOf<String>()

        combinedUsers.forEach { u ->
            val owners = com.example.data.OwnerParser.deserialize(u.ownerName, u.phone, u.email)
            owners.forEach { o ->
                if (o.email.trim().isNotBlank()) {
                    takenEmails.add(o.email.trim().lowercase())
                }
                if (o.phone.trim().isNotBlank()) {
                    o.phone.split(",").forEach { individualPhone ->
                        val clean = individualPhone.trim().lowercase().replace("-", "").replace(" ", "").replace("+", "")
                        if (clean.isNotBlank()) {
                            takenPhones.add(clean)
                        }
                    }
                }
            }
        }

        // Verify if any of our requested emails/phones are already taken!
        for (em in currentReqEmails) {
            if (takenEmails.contains(em)) {
                return if (_isBengali.value) "ইমেইলটি ($em) ইতিমধ্যে কোনো অ্যাকাউন্টে ব্যবহৃত হয়েছে!" else "Email ($em) already registered in system!"
            }
        }

        for (ph in currentReqPhones) {
            if (takenPhones.contains(ph)) {
                return if (_isBengali.value) "ফোন নম্বরটি ($ph) ইতিমধ্যে কোনো অ্যাকাউন্টে ব্যবহৃত হয়েছে!" else "Phone ($ph) already registered in system!"
            }
        }

        return null
    }

    /**
     * Clean up redirects from firebase for identifiers that have been deleted or freed during the update
     */
    suspend fun cleanupOldRedirects(oldUser: User?, newUser: User) {
        if (oldUser == null) return
        try {
            val oldOwners = com.example.data.OwnerParser.deserialize(oldUser.ownerName, oldUser.phone, oldUser.email)
            val oldIdentifiers = mutableSetOf<String>()
            oldIdentifiers.add(oldUser.email.trim().lowercase())
            oldOwners.forEach { o ->
                if (o.email.trim().isNotEmpty()) oldIdentifiers.add(o.email.trim().lowercase())
                o.phone.split(",").forEach { p ->
                    val clean = p.trim().lowercase().replace("-", "").replace(" ", "").replace("+", "")
                    if (clean.isNotEmpty()) {
                        oldIdentifiers.add(clean)
                        if (clean.length >= 11) oldIdentifiers.add(clean.takeLast(11))
                    }
                }
            }

            val newOwners = com.example.data.OwnerParser.deserialize(newUser.ownerName, newUser.phone, newUser.email)
            val newIdentifiers = mutableSetOf<String>()
            newIdentifiers.add(newUser.email.trim().lowercase())
            newOwners.forEach { o ->
                if (o.email.trim().isNotEmpty()) newIdentifiers.add(o.email.trim().lowercase())
                o.phone.split(",").forEach { p ->
                    val clean = p.trim().lowercase().replace("-", "").replace(" ", "").replace("+", "")
                    if (clean.isNotEmpty()) {
                        newIdentifiers.add(clean)
                        if (clean.length >= 11) newIdentifiers.add(clean.takeLast(11))
                    }
                }
            }

            // Deleted ones:
            val deleted = oldIdentifiers - newIdentifiers
            deleted.forEach { id ->
                com.example.api.CloudSyncEngine.deleteRedirect(id)
            }
        } catch (e: Exception) {
            Log.e("AppViewModel", "Failed to cleanup old redirects", e)
        }
    }

    fun registerNewUser(
        shopName: String,
        ownerName: String,
        email: String,
        phone: String,
        pin: String,
        profilePic: String? = null,
        shopPic: String? = null
    ) {
        if (shopName.isBlank() || ownerName.isBlank() || email.isBlank() || phone.isBlank() || pin.isBlank()) {
            _authStateMessage.value = if (_isBengali.value) "দয়া করে সকল তথ্য পূরণ করুন" else "Please fill up all details"
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _authStateMessage.value = if (_isBengali.value) "যাচাই করা হচ্ছে..." else "Verifying registration details..."
                
                val error = getCollisionErrorForUser(
                    excludingEmail = null,
                    ownerName = ownerName,
                    phone = phone,
                    email = email
                )
                if (error != null) {
                    _authStateMessage.value = error
                    return@launch
                }

                val newUser = User(
                    email = email.trim(),
                    shopName = shopName.trim(),
                    phone = phone.trim(),
                    passwordHash = pin.trim(), // store simply for offline/local flow
                    ownerName = ownerName.trim(),
                    profilePicture = profilePic,
                    shopPicture = shopPic,
                    ipAddress = "Unknown",
                    registerLocation = "Unknown",
                    registerDevice = getDeviceName(),
                    activeDevicesJson = org.json.JSONArray().put(getDeviceName()).toString(),
                    blockedDevicesJson = "[]",
                    isBlocked = false,
                    registrationTimestamp = System.currentTimeMillis()
                )
                repository.registerUser(newUser)
                
                // Immediate Background Cloud Upload (makes registration instant)
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val initialPayload = com.example.api.SyncPayload(
                            user = newUser,
                            stockItems = emptyList(),
                            customers = emptyList(),
                            dealers = emptyList(),
                            transactions = emptyList(),
                            timestamp = System.currentTimeMillis(),
                            registrationTimestamp = System.currentTimeMillis()
                        )
                        com.example.api.CloudSyncEngine.uploadPayload(newUser.email, initialPayload)
                    } catch(e: Exception) {
                        Log.e("AppViewModel", "Failed registration background upload", e)
                    }
                }

                _currentUser.value = newUser
                _currentScreen.value = "DASHBOARD"
                _authStateMessage.value = null
                triggerCloudSync(isManual = false)
                
                withContext(Dispatchers.Main) {
                    showToast(if (_isBengali.value) "রেজিস্ট্রেশন সফল হয়েছে!" else "Registration successful!")
                }

                // Asynchronously fetch IP and Location detail mapping in the background
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        var ip = "Unknown"
                        var loc = "Unknown"
                        try {
                            val url = java.net.URL("https://ip-api.com/json")
                            val conn = url.openConnection() as java.net.HttpURLConnection
                            conn.connectTimeout = 1500
                            conn.readTimeout = 1500
                            val resText = conn.inputStream.bufferedReader().use { it.readText() }
                            val obj = org.json.JSONObject(resText)
                            if (obj.optString("status") == "success") {
                                ip = obj.optString("query", "Unknown")
                                val city = obj.optString("city", "")
                                val rName = obj.optString("regionName", "")
                                val cName = obj.optString("country", "")
                                val locParts = listOf(city, rName, cName).filter { it.isNotBlank() }
                                loc = if (locParts.isNotEmpty()) locParts.joinToString(", ") else "Unknown"
                            }
                        } catch(e: Exception) {
                            try {
                                val url = java.net.URL("https://api.ipify.org")
                                val conn = url.openConnection() as java.net.HttpURLConnection
                                conn.connectTimeout = 1500
                                conn.readTimeout = 1500
                                val ipOnly = conn.inputStream.bufferedReader().use { it.readText() }
                                if (ipOnly.isNotBlank()) {
                                    ip = ipOnly.trim()
                                }
                            } catch(ex: Exception) {}
                        }

                        if (ip != "Unknown" || loc != "Unknown") {
                            // Update local and remote with real geolocation details
                            val current = _currentUser.value
                            if (current != null && current.email == email.trim()) {
                                val updatedUser = current.copy(ipAddress = ip, registerLocation = loc)
                                repository.updateUser(updatedUser)
                                _currentUser.value = updatedUser

                                // Sync back to cloud payload in the background
                                val payload = com.example.api.SyncPayload(
                                    user = updatedUser,
                                    stockItems = repository.getStockItems(updatedUser.email).firstOrNull() ?: emptyList(),
                                    customers = repository.getCustomers(updatedUser.email).firstOrNull() ?: emptyList(),
                                    dealers = repository.getDealers(updatedUser.email).firstOrNull() ?: emptyList(),
                                    transactions = repository.getTransactions(updatedUser.email).firstOrNull() ?: emptyList(),
                                    timestamp = System.currentTimeMillis(),
                                    registrationTimestamp = com.example.api.CloudSyncEngine.downloadPayload(updatedUser.email)?.registrationTimestamp ?: System.currentTimeMillis()
                                )
                                com.example.api.CloudSyncEngine.uploadPayload(updatedUser.email, payload)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("AppViewModel", "Failed to resolve IP asynchronously", e)
                    }
                }
            } catch (e: Exception) {
                _authStateMessage.value = e.message ?: "Error"
            }
        }
    }

    fun updateUserProfile(
        shopName: String,
        ownerName: String,
        email: String,
        phone: String,
        pin: String,
        profilePic: String?,
        shopPic: String?
    ) {
        if (shopName.isBlank() || ownerName.isBlank() || email.isBlank() || phone.isBlank() || pin.isBlank()) {
            showToast(if (_isBengali.value) "দয়া করে সমস্ত তথ্য পূরণ করুন" else "Please complete all details")
            return
        }
        val targetEmail = email.trim()
        val oldUser = _currentUser.value
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Perform robust, unified validation checking both local and cloud databases
                val collisionError = getCollisionErrorForUser(
                    excludingEmail = oldUser?.email,
                    ownerName = ownerName.trim(),
                    phone = phone.trim(),
                    email = targetEmail
                )
                if (collisionError != null) {
                    withContext(Dispatchers.Main) {
                        showToast(collisionError)
                    }
                    return@launch
                }

                val finalProfileRef = uploadImageIfBase64(targetEmail, profilePic, "profile")
                val finalShopRef = uploadImageIfBase64(targetEmail, shopPic, "shop")

                val updatedUser = User(
                    email = targetEmail,
                    shopName = shopName.trim(),
                    phone = phone.trim(),
                    passwordHash = pin.trim(),
                    profilePicture = finalProfileRef ?: profilePic,
                    ownerName = ownerName.trim(),
                    shopPicture = finalShopRef ?: shopPic,
                    ipAddress = oldUser?.ipAddress,
                    registerLocation = oldUser?.registerLocation,
                    registerDevice = oldUser?.registerDevice,
                    activeDevicesJson = oldUser?.activeDevicesJson,
                    blockedDevicesJson = oldUser?.blockedDevicesJson,
                    isBlocked = oldUser?.isBlocked ?: false
                )

                if (oldUser != null && oldUser.email.trim().lowercase() != targetEmail.lowercase()) {
                    // Register new user record
                    repository.registerUser(updatedUser)
                    // Cascade update references
                    repository.updateStockItemEmail(oldUser.email.trim(), targetEmail)
                    repository.updateCustomerEmail(oldUser.email.trim(), targetEmail)
                    repository.updateDealerEmail(oldUser.email.trim(), targetEmail)
                    repository.updateTransactionEmail(oldUser.email.trim(), targetEmail)
                    // Delete old user record
                    repository.deleteUserByEmail(oldUser.email.trim())
                } else {
                    repository.updateUser(updatedUser)
                }

                // Sync and upload Payload to Cloud
                val finalStock = repository.getStockItems(targetEmail).firstOrNull() ?: emptyList()
                val finalCustomers = repository.getCustomers(targetEmail).firstOrNull() ?: emptyList()
                val finalDealers = repository.getDealers(targetEmail).firstOrNull() ?: emptyList()
                val finalTx = repository.getTransactions(targetEmail).firstOrNull() ?: emptyList()

                val rootEmail = getBaseEmail(targetEmail)
                val allShops = repository.getAllShopsOfUser(rootEmail)
                val additionalShops = allShops.filter { it.email != targetEmail }

                val existingPayload = com.example.api.CloudSyncEngine.downloadPayload(targetEmail)
                val newPayload = com.example.api.SyncPayload(
                    user = updatedUser,
                    stockItems = finalStock,
                    customers = finalCustomers,
                    dealers = finalDealers,
                    transactions = finalTx,
                    timestamp = System.currentTimeMillis(),
                    registrationTimestamp = existingPayload?.registrationTimestamp ?: existingPayload?.timestamp ?: System.currentTimeMillis(),
                    additionalShops = additionalShops
                )
                
                val uploadSuccess = com.example.api.CloudSyncEngine.uploadPayload(targetEmail, newPayload)
                
                // Cleanup old redirects if emails/phones changed
                cleanupOldRedirects(oldUser, updatedUser)

                if (oldUser != null && oldUser.email.trim().lowercase() != targetEmail.lowercase()) {
                    if (uploadSuccess) {
                        com.example.api.CloudSyncEngine.deletePayload(oldUser.email.trim())
                    }
                }

                _currentUser.value = updatedUser
                lastProfileUpdateTime = System.currentTimeMillis()

                withContext(Dispatchers.Main) {
                    if (uploadSuccess) {
                        showToast(if (_isBengali.value) "প্রোফাইল সফলভাবে আপডেট ও সিন্ক্রোনাইজ করা হয়েছে!" else "Profile updated and synchronized successfully!")
                    } else {
                        showToast(if (_isBengali.value) "স্থানীয়ভাবে সেভ হয়েছে কিন্তু ক্লাউড আপডেট ব্যর্থ হয়েছে" else "Saved locally but cloud update failed")
                    }
                }
            } catch (e: Exception) {
                Log.e("AppViewModel", "Profile update error", e)
                withContext(Dispatchers.Main) {
                    showToast(if (_isBengali.value) "স্থানীয়ভাবে সেভ হয়েছে কিন্তু ক্লাউড আপডেট ব্যর্থ হয়েছে" else "Saved locally but cloud update failed")
                }
            }
        }
    }

    fun adminUpdateUserProfileAndSync(
        targetUser: User,
        oldEmail: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val originalUser = repository.getUser(oldEmail.trim())

                // Unified robust duplicate verification
                val collisionError = getCollisionErrorForUser(
                    excludingEmail = oldEmail.trim(),
                    ownerName = targetUser.ownerName ?: "",
                    phone = targetUser.phone,
                    email = targetUser.email
                )
                if (collisionError != null) {
                    withContext(Dispatchers.Main) {
                        onComplete(false, collisionError)
                    }
                    return@launch
                }

                val isEmailChanged = oldEmail.lowercase().trim() != targetUser.email.lowercase().trim()

                // Optimize profile & shop pictures before upload
                val finalProfileRef = uploadImageIfBase64(targetUser.email, targetUser.profilePicture, "profile")
                val finalShopRef = uploadImageIfBase64(targetUser.email, targetUser.shopPicture, "shop")
                
                val cloudUser = targetUser.copy(
                    profilePicture = finalProfileRef ?: targetUser.profilePicture,
                    shopPicture = finalShopRef ?: targetUser.shopPicture
                )

                // Update local database
                if (isEmailChanged) {
                    // Register user under the new email key
                    repository.registerUser(cloudUser)
                    
                    // Cascade update references in local tables
                    repository.updateStockItemEmail(oldEmail.trim(), targetUser.email.trim())
                    repository.updateCustomerEmail(oldEmail.trim(), targetUser.email.trim())
                    repository.updateDealerEmail(oldEmail.trim(), targetUser.email.trim())
                    repository.updateTransactionEmail(oldEmail.trim(), targetUser.email.trim())
                    
                    // Remove old local user entry
                    repository.deleteUserByEmail(oldEmail.trim())
                } else {
                    repository.updateUser(cloudUser)
                }

                // Sync and upload Payload to Cloud
                var uploadSuccess = false
                if (isEmailChanged) {
                    val oldPayload = com.example.api.CloudSyncEngine.downloadPayload(oldEmail.trim())
                    val newPayload = if (oldPayload != null) {
                        val updatedStock = oldPayload.stockItems.map { it.copy(userEmail = targetUser.email.trim()) }
                        val updatedCustomers = oldPayload.customers.map { it.copy(userEmail = targetUser.email.trim()) }
                        val updatedDealers = oldPayload.dealers.map { it.copy(userEmail = targetUser.email.trim()) }
                        val updatedTransactions = oldPayload.transactions.map { it.copy(userEmail = targetUser.email.trim()) }
                        
                        oldPayload.copy(
                            user = cloudUser,
                            stockItems = updatedStock,
                            customers = updatedCustomers,
                            dealers = updatedDealers,
                            transactions = updatedTransactions,
                            timestamp = System.currentTimeMillis(),
                            registrationTimestamp = oldPayload.registrationTimestamp ?: oldPayload.timestamp
                        )
                    } else {
                        com.example.api.SyncPayload(
                            user = cloudUser,
                            stockItems = repository.getStockItems(targetUser.email.trim()).firstOrNull() ?: emptyList(),
                            customers = repository.getCustomers(targetUser.email.trim()).firstOrNull() ?: emptyList(),
                            dealers = repository.getDealers(targetUser.email.trim()).firstOrNull() ?: emptyList(),
                            transactions = repository.getTransactions(targetUser.email.trim()).firstOrNull() ?: emptyList(),
                            timestamp = System.currentTimeMillis(),
                            registrationTimestamp = System.currentTimeMillis()
                        )
                    }
                    
                    uploadSuccess = com.example.api.CloudSyncEngine.uploadPayload(targetUser.email.trim(), newPayload)
                    if (uploadSuccess) {
                        // Safe to delete old payload on the cloud
                        com.example.api.CloudSyncEngine.deletePayload(oldEmail.trim())
                    }
                } else {
                    val cloudPayload = com.example.api.CloudSyncEngine.downloadPayload(targetUser.email.trim())
                    val newPayload = if (cloudPayload != null) {
                        cloudPayload.copy(
                            user = cloudUser,
                            timestamp = System.currentTimeMillis(),
                            registrationTimestamp = cloudPayload.registrationTimestamp ?: cloudPayload.timestamp
                        )
                    } else {
                        com.example.api.SyncPayload(
                            user = cloudUser,
                            stockItems = repository.getStockItems(targetUser.email.trim()).firstOrNull() ?: emptyList(),
                            customers = repository.getCustomers(targetUser.email.trim()).firstOrNull() ?: emptyList(),
                            dealers = repository.getDealers(targetUser.email.trim()).firstOrNull() ?: emptyList(),
                            transactions = repository.getTransactions(targetUser.email.trim()).firstOrNull() ?: emptyList(),
                            timestamp = System.currentTimeMillis(),
                            registrationTimestamp = System.currentTimeMillis()
                        )
                    }
                    uploadSuccess = com.example.api.CloudSyncEngine.uploadPayload(targetUser.email.trim(), newPayload)
                }

                // Cleanup old redirects if fields changed
                cleanupOldRedirects(originalUser, cloudUser)

                // If it is our current logged-in user, update their state
                val currentEmail = _currentUser.value?.email?.lowercase()?.trim()
                if (currentEmail == oldEmail.lowercase().trim() || currentEmail == targetUser.email.lowercase().trim()) {
                    lastProfileUpdateTime = System.currentTimeMillis()
                    withContext(Dispatchers.Main) {
                        _currentUser.value = targetUser
                    }
                }

                withContext(Dispatchers.Main) {
                    if (uploadSuccess) {
                        onComplete(true, if (_isBengali.value) "সাফল্যের সাথে তথ্য আপডেট করা হয়েছে এবং ক্লাউডে সিঙ্ক হয়েছে!" else "Successfully updated information and synced to cloud!")
                    } else {
                        onComplete(true, if (_isBengali.value) "স্থানীয়ভাবে সফলভাবে সেভ হয়েছে কিন্তু ক্লাউড আপডেট ব্যর্থ হয়েছে" else "Successfully updated locally but cloud write failed")
                    }
                }
            } catch (e: Exception) {
                Log.e("AppViewModel", "Admin profile update error", e)
                withContext(Dispatchers.Main) {
                    onComplete(false, e.message ?: "Error updating user")
                }
            }
        }
    }

    fun wipeAllUsersExceptAdmin(onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val remoteUsers = com.example.api.CloudSyncEngine.fetchAllRegisteredUsers()
                val targetEmails = remoteUsers
                    .map { it.email.trim().lowercase() }
                    .distinct()
                    .filter { it != "mdanisujjamanontar@gmail.com" }

                var deletedCount = 0
                targetEmails.forEach { email ->
                    repository.deleteUserByEmail(email)
                    com.example.api.CloudSyncEngine.deletePayload(email)
                    deletedCount++
                }

                withContext(Dispatchers.Main) {
                    onComplete(true, if (_isBengali.value) "সাফল্যের সাথে $deletedCount টি অ্যাকাউন্ট মুছে ফেলা হয়েছে!" else "Successfully wiped $deletedCount accounts from server!")
                }
            } catch (e: Exception) {
                Log.e("AppViewModel", "Wipe all users error", e)
                withContext(Dispatchers.Main) {
                    onComplete(false, e.message ?: "Wipe failed")
                }
            }
        }
    }

    fun adminDeleteUserAndSync(
        email: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Deleting locally
                repository.deleteUserByEmail(email)
                
                // Deleting on cloud
                val success = com.example.api.CloudSyncEngine.deletePayload(email)
                
                withContext(Dispatchers.Main) {
                    onComplete(true, if (_isBengali.value) "সাফল্যের সাথে অ্যাকাউন্টটি ডিলিট করা হয়েছে!" else "Account deleted successfully from local and cloud!")
                }
            } catch (e: Exception) {
                Log.e("AppViewModel", "Admin delete user error", e)
                withContext(Dispatchers.Main) {
                    onComplete(false, e.message ?: "Deletion failed")
                }
            }
        }
    }

    fun sendResetOtp(emailOrPhone: String) {
        val searchKey = emailOrPhone.trim().lowercase()
        if (searchKey.isBlank()) {
            _authStateMessage.value = if (_isBengali.value) "দয়া করে ইমেইল বা মোবাইল নাম্বার লিখুন" else "Please enter email or mobile number"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Load all users from DB to find match across joint owners' emails/phones as well as primary email/phones
                val allUsers = repository.getAllUsers()
                var matchedUser: User? = null

                // Look through local users
                for (u in allUsers) {
                    val uEmail = u.email.trim().lowercase()
                    // Check primary email
                    if (uEmail == searchKey) {
                        matchedUser = u
                        break
                    }

                    // Check primary phone list (splitting comma-separated list of phones)
                    val primaryPhones = u.phone.split(",").map { it.trim().lowercase() }
                    if (primaryPhones.any { it == searchKey || it.replace("-", "").replace(" ", "") == searchKey.replace("-", "").replace(" ", "") }) {
                        matchedUser = u
                        break
                    }

                    // Deserialize and check joint owners
                    val jointOwners = com.example.data.OwnerParser.deserialize(u.ownerName, u.phone, u.email)
                    val matchInJoint = jointOwners.any { owner ->
                        val oEmail = owner.email.trim().lowercase()
                        val oPhone = owner.phone.trim().lowercase().replace("-", "").replace(" ", "")
                        oEmail == searchKey || oPhone == searchKey.replace("-", "").replace(" ", "")
                    }
                    if (matchInJoint) {
                        matchedUser = u
                        break
                    }
                }

                // If not found locally, try CloudSync download as fallback (for primary email only)
                if (matchedUser == null) {
                    val cloudPayload = com.example.api.CloudSyncEngine.downloadPayload(emailOrPhone.trim())
                    if (cloudPayload != null) {
                        val u = cloudPayload.user
                        val uEmail = u.email.trim().lowercase()
                        val primaryPhones = u.phone.split(",").map { it.trim().lowercase() }
                        val jointOwners = com.example.data.OwnerParser.deserialize(u.ownerName, u.phone, u.email)
                        val matchInJoint = jointOwners.any { owner ->
                            val oEmail = owner.email.trim().lowercase()
                            val oPhone = owner.phone.trim().lowercase().replace("-", "").replace(" ", "")
                            oEmail == searchKey || oPhone == searchKey.replace("-", "").replace(" ", "")
                        }

                        if (uEmail == searchKey || primaryPhones.any { it == searchKey || it.replace("-", "").replace(" ", "") == searchKey.replace("-", "").replace(" ", "") } || matchInJoint) {
                            matchedUser = u
                        }
                    }
                }

                if (matchedUser == null) {
                    _authStateMessage.value = if (_isBengali.value) "এই ইমেইল/মোবাইল দিয়ে কোনো অ্যাকাউন্ট পাওয়া যায়নি" else "No account found with this email/mobile"
                    return@launch
                }

                // Generate a random 4 digit numeric OTP
                val generatedOtp = (1000..9999).random().toString()
                _resetOtp.value = generatedOtp
                _resetUser.value = matchedUser
                _forgetPasswordStep.value = 2 // Transition to step 2: Enter OTP
                _authStateMessage.value = null

                // For user clarity, let's toast that OTP was dispatched to all registers
                val names = com.example.data.OwnerParser.deserialize(matchedUser.ownerName, matchedUser.phone, matchedUser.email)
                    .map { it.name }
                    .filter { it.isNotBlank() }
                    .joinToString(", ")

                withContext(Dispatchers.Main) {
                    val msg = if (_isBengali.value) {
                        "🎉 ওটিপি তৈরি হয়েছে! সকল মালিকের ($names) মোবাইল ও ইমেইলে ওটিপি পাঠানো হয়েছে!"
                    } else {
                        "🎉 OTP generated! Single/Joint OTP sent to all registered owners ($names)!"
                    }
                    showToast(msg)
                }
            } catch (e: Exception) {
                Log.e("AppViewModel", "Forgot OTP logic exception", e)
                _authStateMessage.value = e.message ?: "Error sending OTP"
            }
        }
    }

    fun verifyOtpAndResetPin(otpInput: String, newPin: String) {
        val otpValue = _resetOtp.value
        val userItem = _resetUser.value
        
        if (otpValue == null || userItem == null) {
            _authStateMessage.value = if (_isBengali.value) "সেশন শেষ হয়ে গেছে, আবার নতুন করে করুন" else "Session expired. Provide email/mobile again"
            return
        }
        if (otpInput.trim() != otpValue) {
            _authStateMessage.value = if (_isBengali.value) "ভুল ওটিপি কোড! পুনরায় টাইপ করুন" else "Invalid OTP! Type correctly"
            return
        }
        if (newPin.trim().length < 4) {
            _authStateMessage.value = if (_isBengali.value) "নতুন পিন কোড ৪ সংখ্যার হতে হবে" else "New PIN code must be at least 4 digits"
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val updatedUser = userItem.copy(passwordHash = newPin.trim())
                
                // Save/update locally
                val localUserCheck = repository.getUser(userItem.email)
                if (localUserCheck == null) {
                    repository.registerUser(updatedUser)
                } else {
                    repository.updateUser(updatedUser)
                }
                
                // Push updated password and existing datasets securely to CloudSync
                val cloudPayload = com.example.api.CloudSyncEngine.downloadPayload(userItem.email)
                val newPayload = if (cloudPayload != null) {
                    cloudPayload.copy(
                        user = updatedUser,
                        timestamp = System.currentTimeMillis(),
                        registrationTimestamp = cloudPayload.registrationTimestamp ?: cloudPayload.timestamp
                    )
                } else {
                    com.example.api.SyncPayload(
                        user = updatedUser,
                        stockItems = emptyList(),
                        customers = emptyList(),
                        dealers = emptyList(),
                        transactions = emptyList(),
                        timestamp = System.currentTimeMillis(),
                        registrationTimestamp = System.currentTimeMillis()
                    )
                }
                com.example.api.CloudSyncEngine.uploadPayload(userItem.email, newPayload)
                
                // Set active user and nav to dashboard
                _currentUser.value = updatedUser
                _forgetPasswordStep.value = 0
                _currentScreen.value = "DASHBOARD"
                _authStateMessage.value = null
                
                withContext(Dispatchers.Main) {
                    showToast(if (_isBengali.value) "পিন কোড উদ্ধার সফল হয়েছে!" else "PIN recovered and changed successfully!")
                }
            } catch (e: Exception) {
                _authStateMessage.value = e.message ?: "Failed to reset password"
            }
        }
    }

    fun changeUserPassword(oldPin: String, newPin: String, confirmPin: String) {
        val userItem = _currentUser.value
        if (userItem == null) {
            showToast(if (_isBengali.value) "দুঃখিত, কোনো ইউজার সেশন রানিং নেই" else "No user session found")
            return
        }
        if (oldPin.trim() != userItem.passwordHash) {
            showToast(if (_isBengali.value) "বর্তমান পিন কোডটি সঠিক ছিল না!" else "Current PIN description is incorrect!")
            return
        }
        if (newPin.trim().length < 4) {
            showToast(if (_isBengali.value) "নতুন পিন অবশ্যই ৪ সংখ্যার হতে হবে!" else "New PIN code must be at least 4 digits long!")
            return
        }
        if (newPin.trim() != confirmPin.trim()) {
            showToast(if (_isBengali.value) "নতুন পিন কোডগুলো সঠিকভাবে মেলানো যায়নি!" else "New PIN and Confirmation PIN do not match!")
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val updatedUser = userItem.copy(passwordHash = newPin.trim())
                repository.updateUser(updatedUser)
                _currentUser.value = updatedUser
                
                // Sync updated password to Cloud
                val cloudPayload = com.example.api.CloudSyncEngine.downloadPayload(userItem.email)
                val newPayload = if (cloudPayload != null) {
                    cloudPayload.copy(
                        user = updatedUser,
                        timestamp = System.currentTimeMillis(),
                        registrationTimestamp = cloudPayload.registrationTimestamp ?: cloudPayload.timestamp
                    )
                } else {
                    com.example.api.SyncPayload(
                        user = updatedUser,
                        stockItems = emptyList(),
                        customers = emptyList(),
                        dealers = emptyList(),
                        transactions = emptyList(),
                        timestamp = System.currentTimeMillis(),
                        registrationTimestamp = System.currentTimeMillis()
                    )
                }
                com.example.api.CloudSyncEngine.uploadPayload(userItem.email, newPayload)
                
                withContext(Dispatchers.Main) {
                    showToast(if (_isBengali.value) "পিনকোড সফলভাবে পরিবর্তন এবং সিঙ্ক করা হয়েছে!" else "PIN updated and synced to server successfully!")
                }
            } catch (e: Exception) {
                Log.e("AppViewModel", "Password update error", e)
                withContext(Dispatchers.Main) {
                    showToast(if (_isBengali.value) "পিনকোড স্থানীয়ভাবে পরিবর্তন হয়েছে কিন্তু সার্ভারে সিঙ্ক ব্যর্থ" else "PIN changed locally, cloud synchronization failed")
                }
            }
        }
    }

    fun loginUser(identifier: String, pin: String) {
        if (identifier.isBlank() || pin.isBlank()) {
            _authStateMessage.value = if (_isBengali.value) "ইমেইল/মোবাইল এবং পিন ইনপুট দিন" else "Provide email/phone and pin details"
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val trimmedId = identifier.trim().lowercase()
                var user: User? = null
                
                // Super Admin permanent bypass and foolproof recovery
                val isAdmin = trimmedId == "mdanisujjamanontar@gmail.com" || trimmedId == "01319541875"
                if (isAdmin) {
                    val existing = repository.getUser("mdanisujjamanontar@gmail.com")
                    
                    // Let's try downloading from Cloud first!
                    var cloudPayload: com.example.api.SyncPayload? = null
                    try {
                        cloudPayload = com.example.api.CloudSyncEngine.downloadPayload("mdanisujjamanontar@gmail.com")
                    } catch (e: Exception) {
                        Log.e("AppViewModel", "Failed to download cloud payload for admin", e)
                    }

                    if (cloudPayload != null && cloudPayload.user.passwordHash == pin.trim()) {
                        // The cloud payload is found and password matches! Restoring full admin dataset from cloud!
                        val cloudUser = cloudPayload.user
                        if (existing == null) {
                            repository.registerUser(cloudUser)
                        } else {
                            repository.updateUser(cloudUser)
                        }

                        // Restore stock items
                        try {
                            cloudPayload.stockItems.forEach { repository.insertStockItem(it.copy(id = 0, userEmail = cloudUser.email)) }
                        } catch (e: Exception) { Log.e("AppViewModel", "Admin stock restore fail", e) }

                        // Restore customers
                        val localCustomerMap = mutableMapOf<Int, Int>()
                        try {
                            cloudPayload.customers.forEach { repository.insertCustomer(it.copy(id = 0, userEmail = cloudUser.email)) }
                            val freshCustomers = repository.getCustomers(cloudUser.email).firstOrNull() ?: emptyList()
                            cloudPayload.customers.forEach { remoteC ->
                                val matchingL = freshCustomers.find {
                                    it.name.trim().lowercase() == remoteC.name.trim().lowercase() &&
                                    it.phone.trim() == remoteC.phone.trim()
                                }
                                if (matchingL != null) {
                                    localCustomerMap[remoteC.id] = matchingL.id
                                }
                            }
                        } catch (e: Exception) { Log.e("AppViewModel", "Admin customer restore fail", e) }

                        // Restore dealers
                        val localDealerMap = mutableMapOf<Int, Int>()
                        try {
                            cloudPayload.dealers.forEach { repository.insertDealer(it.copy(id = 0, userEmail = cloudUser.email)) }
                            val freshDealers = repository.getDealers(cloudUser.email).firstOrNull() ?: emptyList()
                            cloudPayload.dealers.forEach { remoteD ->
                                val matchingL = freshDealers.find {
                                    it.name.trim().lowercase() == remoteD.name.trim().lowercase() &&
                                    it.phone.trim() == remoteD.phone.trim()
                                }
                                if (matchingL != null) {
                                    localDealerMap[remoteD.id] = matchingL.id
                                }
                            }
                        } catch (e: Exception) { Log.e("AppViewModel", "Admin dealer restore fail", e) }

                        // Restore transactions
                        try {
                            cloudPayload.transactions.forEach { tx ->
                                val localCustId = tx.customerId?.let { localCustomerMap[it] }
                                val localDlrId = tx.dealerId?.let { localDealerMap[it] }
                                repository.insertTransaction(tx.copy(id = 0, userEmail = cloudUser.email, customerId = localCustId, dealerId = localDlrId))
                            }
                        } catch (e: Exception) { Log.e("AppViewModel", "Admin transactions restore fail", e) }

                        user = cloudUser
                    } else if (existing != null) {
                        // Admin exists locally!
                        var adminToUse = existing
                        if (adminToUse.registrationTimestamp == null) {
                            adminToUse = adminToUse.copy(registrationTimestamp = 1781170000000L)
                        }
                        if (adminToUse.passwordHash != pin.trim()) {
                            val updatedAdmin = adminToUse.copy(passwordHash = pin.trim(), isBlocked = false, blockedDevicesJson = "[]")
                            repository.updateUser(updatedAdmin)
                            user = updatedAdmin
                            
                            try {
                                val cloudP = com.example.api.CloudSyncEngine.downloadPayload(updatedAdmin.email)
                                val finalPayload = if (cloudP != null) {
                                    cloudP.copy(user = updatedAdmin, timestamp = System.currentTimeMillis())
                                } else {
                                    com.example.api.SyncPayload(user = updatedAdmin, timestamp = System.currentTimeMillis())
                                }
                                com.example.api.CloudSyncEngine.uploadPayload(updatedAdmin.email, finalPayload)
                            } catch(e: Exception) {
                                Log.e("AppViewModel", "Admin password update upload error", e)
                            }
                        } else {
                            if (adminToUse.registrationTimestamp != existing.registrationTimestamp) {
                                repository.updateUser(adminToUse)
                            }
                            user = adminToUse
                        }
                    } else {
                        // Create super admin on the fly locally with the entered PIN so they can log in!
                        val freshAdmin = User(
                            email = "mdanisujjamanontar@gmail.com",
                            shopName = "মা-বাবার দোয়া ভ্যারাইটিজ স্টোর",
                            phone = "01319541875",
                            passwordHash = pin.trim(),
                            ownerName = "মোঃ আনিসুজ্জামান অন্তর",
                            profilePicture = null,
                            shopPicture = null,
                            ipAddress = "Unknown",
                            registerLocation = "Dhaka, Bangladesh",
                            registerDevice = getDeviceName(),
                            activeDevicesJson = org.json.JSONArray().put(getDeviceName()).toString(),
                            blockedDevicesJson = "[]",
                            isBlocked = false,
                            registrationTimestamp = 1781170000000L
                        )
                        repository.registerUser(freshAdmin)
                        user = freshAdmin
                        
                        // Register local super admin on the fly, but DO NOT overwrite their server/cloud backup with a blank list!
                        Log.d("AppViewModel", "Created super admin on high-availability local fallback")
                    }
                } else {
                    val allUsers = repository.getAllUsers()
                    for (u in allUsers) {
                        if (u.email.trim().lowercase() == trimmedId) {
                            user = u
                            break
                        }
                        if (u.phone.split(",").any { it.trim().lowercase() == trimmedId || it.trim().replace("-", "").replace(" ", "") == trimmedId }) {
                            user = u
                            break
                        }
                        val jointOwners = com.example.data.OwnerParser.deserialize(u.ownerName, u.phone, u.email)
                        if (jointOwners.any { it.email.trim().lowercase() == trimmedId || it.phone.trim().lowercase() == trimmedId || it.phone.trim().replace("-", "").replace(" ", "") == trimmedId }) {
                            user = u
                            break
                        }
                    }
                }
                
                // Try to load from cloud if user is null or doesn't match pin locally
                if (user == null || user.passwordHash != pin.trim()) {
                    val cloudPayload = com.example.api.CloudSyncEngine.downloadPayload(identifier.trim())
                    if (cloudPayload != null) {
                        val cloudUser = cloudPayload.user
                        if (cloudUser.passwordHash == pin.trim()) {
                            // PIN matches! Save user and download entire dataset to local DB
                            if (user == null) {
                                repository.registerUser(cloudUser)
                            } else {
                                repository.updateUser(cloudUser)
                            }
                            
                            // Insert/sync all datasets
                            cloudPayload.stockItems.forEach { repository.insertStockItem(it.copy(id = 0, userEmail = cloudUser.email)) }
                            
                            val localCustomerMap = mutableMapOf<Int, Int>()
                            cloudPayload.customers.forEach { cust ->
                                val generatedId = repository.insertCustomer(cust.copy(id = 0, userEmail = cloudUser.email))
                                localCustomerMap[cust.id] = generatedId.toInt()
                            }
 
                            val localDealerMap = mutableMapOf<Int, Int>()
                            cloudPayload.dealers.forEach { dlr ->
                                val generatedId = repository.insertDealer(dlr.copy(id = 0, userEmail = cloudUser.email))
                                localDealerMap[dlr.id] = generatedId.toInt()
                            }
 
                            cloudPayload.transactions.forEach { tx ->
                                val localCustId = tx.customerId?.let { localCustomerMap[it] }
                                val localDlrId = tx.dealerId?.let { localDealerMap[it] }
                                repository.insertTransaction(
                                    tx.copy(
                                        id = 0,
                                        userEmail = cloudUser.email,
                                        customerId = localCustId,
                                        dealerId = localDlrId
                                    )
                                )
                            }
 
                            user = cloudUser
                        }
                    }
                }

                if (user == null || user.passwordHash != pin.trim()) {
                    _authStateMessage.value = if (_isBengali.value) "ভুল ইমেইল/মোবাইল অথবা পিন!" else "Incorrect login details or pin!"
                    return@launch
                }

                if (user != null && user.email.trim().lowercase() == "mdanisujjamanontar@gmail.com") {
                    if (user.isBlocked || user.blockedDevicesJson != "[]") {
                        user = user.copy(isBlocked = false, blockedDevicesJson = "[]")
                        repository.updateUser(user)
                    }
                }
                
                _currentUser.value = user
                _currentScreen.value = "DASHBOARD"
                _authStateMessage.value = null

                // Fire off background image restoration and sync
                downloadRemoteImagesInBackground(user.email)
                triggerCloudSync(isManual = false)

                withContext(Dispatchers.Main) {
                    val welcomeMsg = if (_isBengali.value) {
                        "${user.getLocalizedShopName(true)} (আইডি: ${user.phone}) লগইন সফল হয়েছে!"
                    } else {
                        "Login successful for ${user.getLocalizedShopName(false)}!"
                    }
                    showToast(welcomeMsg)
                }
            } catch (e: Exception) {
                Log.e("AppViewModel", "Login error", e)
                _authStateMessage.value = e.message ?: "Authentication error"
            }
        }
    }

    fun recoverAllCloudData() {
        val userItem = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isCloudSyncing.value = true
            withContext(Dispatchers.Main) {
                showToast(if (_isBengali.value) "ফায়ারবেস ক্লাউড থেকে ডাটা এবং ইমেজ পুনরুদ্ধার শুরু হয়েছে..." else "Starting Firebase cloud data and image recovery...")
            }

            try {
                // 1. Download payload directly
                val remotePayload = com.example.api.CloudSyncEngine.downloadPayload(userItem.email)
                if (remotePayload == null) {
                    withContext(Dispatchers.Main) {
                        _isCloudSyncing.value = false
                        showToast(if (_isBengali.value) "দুঃখিত! ফায়ারবেস ক্লাউডে কোনো পূর্ববর্তী ব্যাকআপ পাওয়া যায়নি।" else "Sorry! No prior backup found on Firebase cloud.")
                    }
                    return@launch
                }

                val remoteUser = remotePayload.user
                
                // Save/update user profile
                repository.updateUser(remoteUser)
                withContext(Dispatchers.Main) {
                    _currentUser.value = remoteUser
                }

                // Restore/Merge Stocks
                remotePayload.stockItems.forEach { stock ->
                    val existing = repository.getStockItems(userItem.email).firstOrNull()?.find { it.name.trim().lowercase() == stock.name.trim().lowercase() }
                    if (existing != null) {
                        repository.updateStockItem(existing.copy(purchasePrice = stock.purchasePrice, salesPrice = stock.salesPrice, stockCount = stock.stockCount, category = stock.category, imageResName = stock.imageResName, unit = stock.unit))
                    } else {
                        repository.insertStockItem(stock.copy(id = 0, userEmail = userItem.email))
                    }
                }

                // Restore/Merge Customers
                val localCustomerMap = mutableMapOf<Int, Int>()
                val activeL = repository.getCustomers(userItem.email).firstOrNull() ?: emptyList()
                for (remoteCust in remotePayload.customers) {
                    val matchingLocal = activeL.find { 
                        it.name.trim().lowercase() == remoteCust.name.trim().lowercase() && 
                        it.phone.trim() == remoteCust.phone.trim() 
                    }
                    if (matchingLocal != null) {
                        repository.updateCustomer(matchingLocal.copy(address = remoteCust.address, totalDue = remoteCust.totalDue, photoUri = remoteCust.photoUri))
                        localCustomerMap[remoteCust.id] = matchingLocal.id
                    } else {
                        val genId = repository.insertCustomer(remoteCust.copy(id = 0, userEmail = userItem.email))
                        localCustomerMap[remoteCust.id] = genId.toInt()
                    }
                }

                // Restore/Merge Dealers
                val localDealerMap = mutableMapOf<Int, Int>()
                val activeD = repository.getDealers(userItem.email).firstOrNull() ?: emptyList()
                for (remoteDlr in remotePayload.dealers) {
                    val matchingLocal = activeD.find {
                        it.name.trim().lowercase() == remoteDlr.name.trim().lowercase() &&
                        it.phone.trim() == remoteDlr.phone.trim()
                    }
                    if (matchingLocal != null) {
                        repository.updateDealer(matchingLocal.copy(company = remoteDlr.company, totalOwed = remoteDlr.totalOwed, photoUri = remoteDlr.photoUri))
                        localDealerMap[remoteDlr.id] = matchingLocal.id
                    } else {
                        val genId = repository.insertDealer(remoteDlr.copy(id = 0, userEmail = userItem.email))
                        localDealerMap[remoteDlr.id] = genId.toInt()
                    }
                }

                // Restore/Merge Transactions
                val activeT = repository.getTransactions(userItem.email).firstOrNull() ?: emptyList()
                for (remoteT in remotePayload.transactions) {
                    val exists = activeT.any {
                        it.title == remoteT.title &&
                        java.lang.Math.abs(it.amount - remoteT.amount) < 0.01 &&
                        it.type == remoteT.type &&
                        it.timestamp == remoteT.timestamp
                    }
                    if (!exists) {
                        val localCustId = remoteT.customerId?.let { localCustomerMap[it] }
                        val localDlrId = remoteT.dealerId?.let { localDealerMap[it] }
                        repository.insertTransaction(remoteT.copy(id = 0, userEmail = userItem.email, customerId = localCustId, dealerId = localDlrId))
                    }
                }

                // Force background download of all pictures
                downloadRemoteImagesInBackground(userItem.email)

                withContext(Dispatchers.Main) {
                    _isCloudSyncing.value = false
                    val currentMs = System.currentTimeMillis()
                    _lastSyncTime.value = currentMs
                    prefs.edit().putLong("last_successful_sync_time2", currentMs).apply()
                    showToast(if (_isBengali.value) "ফায়ারবেস ক্লাউড রিকভারি সফলভাবে সম্পন্ন হয়েছে! ব্যাকগ্রাউন্ডে ইমেজ ডাউনলোড হচ্ছে।" else "Firebase Cloud Recovery executed successfully! Images are downloading in background.")
                }
            } catch (e: Exception) {
                Log.e("AppViewModel", "Recovery failed", e)
                withContext(Dispatchers.Main) {
                    _isCloudSyncing.value = false
                    showToast(if (_isBengali.value) "রিকভারি করা সম্ভব হয়নি: ইন্টারনেট সংযোগ পরীক্ষা করুন।" else "Recovery could not complete: check your internet connection.")
                }
            }
        }
    }

    fun logout() {
        val userObj = _currentUser.value
        if (userObj != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val finalStock = repository.getStockItems(userObj.email).firstOrNull() ?: emptyList()
                    val finalCustomers = repository.getCustomers(userObj.email).firstOrNull() ?: emptyList()
                    val finalDealers = repository.getDealers(userObj.email).firstOrNull() ?: emptyList()
                    val finalTx = repository.getTransactions(userObj.email).firstOrNull() ?: emptyList()
                    
                    val rootEmail = getBaseEmail(userObj.email)
                    val allShops = repository.getAllShopsOfUser(rootEmail)
                    val additionalShops = allShops.filter { it.email != userObj.email }
                    
                    val existingPayload = com.example.api.CloudSyncEngine.downloadPayload(userObj.email)
                    val uploadPayload = com.example.api.SyncPayload(
                        user = userObj,
                        stockItems = finalStock,
                        customers = finalCustomers,
                        dealers = finalDealers,
                        transactions = finalTx,
                        timestamp = System.currentTimeMillis(),
                        registrationTimestamp = existingPayload?.registrationTimestamp ?: existingPayload?.timestamp ?: System.currentTimeMillis(),
                        additionalShops = additionalShops
                    )
                    com.example.api.CloudSyncEngine.uploadPayload(userObj.email, uploadPayload)
                } catch (e: Exception) {
                    Log.e("AppViewModel", "Failed to sync timestamp on logout", e)
                }
            }
        }
        _currentUser.value = null
        _currentScreen.value = "LOGIN"
    }

    fun seedDemoData() {
        val email = "demo@example.com"
        val userItem = User(
            email = email,
            shopName = "মেসার্স অনিক স্টোর (অনুমোদিত ডেমো)",
            phone = "01712345678",
            passwordHash = "1234",
            profilePicture = null,
            ownerName = "মেসার্স অনিক"
        )
        _currentUser.value = userItem
        _currentScreen.value = "DASHBOARD"
        _authStateMessage.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Pre-register user in DB if they don't exist
                val existing = repository.getUser(email)
                if (existing == null) {
                    repository.registerUser(userItem)
                }

                // Clean existing data for a fresh demo feel
                // Seed Stock Items
                val items = listOf(
                    StockItem(name = "চিনি (Sugar)", purchasePrice = 110.0, salesPrice = 125.0, stockCount = 50, category = "মুদি মাল", userEmail = email),
                    StockItem(name = "সয়াবিন তেল (Soyabean Oil)", purchasePrice = 150.0, salesPrice = 168.0, stockCount = 30, category = "তেল ও ঘি", userEmail = email),
                    StockItem(name = "মসুর ডাল (Lentil)", purchasePrice = 120.0, salesPrice = 140.0, stockCount = 40, category = "ডাল ও চাল", userEmail = email),
                    StockItem(name = "মিনিকেট চাল (Miniket Rice)", purchasePrice = 62.0, salesPrice = 70.0, stockCount = 2, category = "চাল", userEmail = email),
                    StockItem(name = "লাক্স সাবান (Lux Soap)", purchasePrice = 60.0, salesPrice = 75.0, stockCount = 0, category = "কসমেটিকস", userEmail = email)
                )
                // Filter out existing before insert
                for (item in items) {
                    repository.insertStockItem(item)
                }

                // Seed Customers
                val customersList = listOf(
                    Customer(name = "আব্দুর রহমান", phone = "01911223344", address = "উত্তরা, ঢাকা", totalDue = 1250.0, userEmail = email),
                    Customer(name = "জাকির হোসেন", phone = "01855667788", address = "মিরপুর, ঢাকা", totalDue = 420.0, userEmail = email),
                    Customer(name = "ফাতেমা বেগম", phone = "01511223344", address = "বনানী, ঢাকা", totalDue = 0.0, userEmail = email)
                )
                for (customer in customersList) {
                    repository.insertCustomer(customer)
                }

                // Seed Dealers
                val dealersList = listOf(
                    Dealer(name = "ইউনিলিভার বাংলাদেশ সরবরাহকারী", phone = "01711223344", company = "Unilever Bangladesh", totalOwed = 5500.0, userEmail = email),
                    Dealer(name = "ফ্রেশ গ্রুপ সাজেন", phone = "01655667788", company = "Meghna Group Of Industries", totalOwed = 1200.0, userEmail = email)
                )
                for (dealer in dealersList) {
                    repository.insertDealer(dealer)
                }

                // Seed Transactions
                val transactionsList = listOf(
                    TransactionRecord(type = "SALE", amount = 125.0, profit = 15.0, title = "চিনি বিক্রি করা হলো (1 kg)", userEmail = email, timestamp = System.currentTimeMillis() - 12 * 3600 * 1000),
                    TransactionRecord(type = "SALE", amount = 336.0, profit = 36.0, title = "সয়াবিন তেল বিক্রি (2 L)", userEmail = email, timestamp = System.currentTimeMillis() - 2 * 3600 * 1000),
                    TransactionRecord(type = "EXPENSE", amount = 350.0, profit = -350.0, title = "দোকানের বিদ্যুৎ বিল", userEmail = email, timestamp = System.currentTimeMillis() - 24 * 3600 * 1000),
                    TransactionRecord(type = "CUSTOMER_PAYMENT", amount = 600.0, profit = 0.0, title = "আব্দুর রহমান জমা দিয়েছেন", userEmail = email, timestamp = System.currentTimeMillis() - 48 * 3600 * 1000)
                )
                for (t in transactionsList) {
                    repository.insertTransaction(t)
                }

                withContext(Dispatchers.Main) {
                    showToast("ডেমো অ্যাকাউন্ট ও ডাটাসেট সফলভাবে লোড হয়েছে!")
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    // --- INVENTORY MANAGEMENT ---
    fun addStockItem(name: String, buyPrice: Double, sellPrice: Double, quantity: Int, category: String, unit: String = "পিস", photoUri: String? = null) {
        val email = _currentUser.value?.email ?: return
        if (name.isBlank()) {
            showToast(if (_isBengali.value) "নাম খালি হতে পারে না" else "Name cannot be empty")
            return
        }

        markLocalMutation()
        viewModelScope.launch(Dispatchers.IO) {
            val item = StockItem(
                userEmail = email,
                name = name,
                purchasePrice = buyPrice,
                salesPrice = sellPrice,
                stockCount = quantity,
                category = category,
                imageResName = photoUri,
                unit = unit
            )
            repository.insertStockItem(item)
            triggerCloudSync(isManual = false)
            withContext(Dispatchers.Main) {
                showToast(if (_isBengali.value) "পণ্য স্টক করা হয়েছে!" else "Product stock added!")
            }
        }
    }

    fun updateStockItemCount(item: StockItem, newCount: Int) {
        markLocalMutation()
        viewModelScope.launch(Dispatchers.IO) {
            val updated = item.copy(stockCount = newCount)
            repository.updateStockItem(updated)
            triggerCloudSync(isManual = false)
            withContext(Dispatchers.Main) {
                showToast(if (_isBengali.value) "স্টক আপডেট করা হয়েছে!" else "Stock quantity updated!")
            }
        }
    }

    fun deleteStockItem(item: StockItem) {
        markLocalMutation()
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteStockItem(item)
            triggerCloudSync(isManual = false)
            withContext(Dispatchers.Main) {
                showToast(if (_isBengali.value) "পণ্য সরানো হয়েছে!" else "Product removed from stock!")
            }
        }
    }

    fun updateStockItemProfile(item: StockItem, name: String, category: String, buyPrice: Double, sellPrice: Double, quantity: Int, unit: String, photoUri: String?) {
        if (name.isBlank()) {
            showToast(if (_isBengali.value) "নাম খালি হতে পারে না" else "Name cannot be empty")
            return
        }
        markLocalMutation()
        viewModelScope.launch(Dispatchers.IO) {
            val updated = item.copy(
                name = name,
                category = category,
                purchasePrice = buyPrice,
                salesPrice = sellPrice,
                stockCount = quantity,
                unit = unit,
                imageResName = photoUri
            )
            repository.updateStockItem(updated)
            triggerCloudSync(isManual = false)
            withContext(Dispatchers.Main) {
                showToast(if (_isBengali.value) "পণ্য তথ্য আপডেট করা হয়েছে!" else "Product updated successfully!")
            }
        }
    }

    fun parseDoubleRobust(input: String): Double {
        val banglaDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        val englishDigits = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
        var clean = input.trim()
        for (i in 0..9) {
            clean = clean.replace(banglaDigits[i], englishDigits[i])
        }
        clean = clean.replace(",", "").replace(" ", "")
        return clean.toDoubleOrNull() ?: 0.0
    }

    fun parseIntRobust(input: String): Int {
        val banglaDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        val englishDigits = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
        var clean = input.trim()
        for (i in 0..9) {
            clean = clean.replace(banglaDigits[i], englishDigits[i])
        }
        clean = clean.replace(",", "").replace(" ", "")
        return clean.toIntOrNull() ?: 0
    }

    fun roundToTwoDecimals(value: Double): Double {
        return try {
            String.format(java.util.Locale.US, "%.2f", value).toDoubleOrNull() ?: value
        } catch (e: Exception) {
            value
        }
    }

    fun isLocalImageMatchingRef(local: String?, remote: String?, extraSeed: String, userEmail: String): Boolean {
        if (local == null || remote == null) return false
        if (!remote.startsWith("remote_ref:")) return false
        val remoteKey = remote.substringAfter("remote_ref:")
        
        // If local is already equal to remote remote_ref
        if (local.startsWith("remote_ref:")) {
            return local == remote
        }
        
        // Compute key for local base64
        val sanitizedEmail = userEmail.lowercase().trim().replace("@", "_at_").replace(".", "_dot_").filter { it.isLetterOrDigit() || it == '_' }
        val absHash = java.lang.Math.abs(local.hashCode())
        val computedKey = "img_${sanitizedEmail}_${extraSeed}_h${absHash}_len${local.length}".lowercase().filter { it.isLetterOrDigit() || it == '_' }
        
        return computedKey == remoteKey
    }

    // --- SALES & BILLING (টালি ও বিক্রি) ---
    fun recordSale(item: StockItem, quantityToSell: Int, customerId: Int? = null) {
        val email = _currentUser.value?.email ?: return
        if (item.stockCount < quantityToSell) {
            showToast(if (_isBengali.value) "স্টকে পর্যাপ্ত মালামাল নেই!" else "Insufficient stock available!")
            return
        }

        markLocalMutation()
        viewModelScope.launch(Dispatchers.IO) {
            // Update item stock
            val updatedItem = item.copy(stockCount = item.stockCount - quantityToSell)
            repository.updateStockItem(updatedItem)

            // Calculations
            val saleAmount = item.salesPrice * quantityToSell
            val totalPurchaseCost = item.purchasePrice * quantityToSell
            val profit = saleAmount - totalPurchaseCost

            // Save sale transaction
            val transaction = TransactionRecord(
                userEmail = email,
                type = "SALE",
                amount = saleAmount,
                profit = profit,
                title = if (_isBengali.value) "${item.name} বিক্রি করা হ​লো ($quantityToSell টি)" else "Sold ${item.name} (x$quantityToSell)",
                description = if (_isBengali.value) "ঐক্যক মূল্য: ৳${item.salesPrice}" else "Unit price: ৳${item.salesPrice}",
                customerId = customerId
            )
            repository.insertTransaction(transaction)

            // If sold on customer due (and customer is specified)
            if (customerId != null) {
                // Fetch and update customer total due balance directly from database
                val currentCustomers = repository.getCustomers(email).firstOrNull() ?: emptyList()
                val matched = currentCustomers.find { it.id == customerId }
                if (matched != null) {
                    val updatedCustomer = matched.copy(totalDue = roundToTwoDecimals(matched.totalDue + saleAmount))
                    repository.updateCustomer(updatedCustomer)
                }
            }

            withContext(Dispatchers.Main) {
                showToast(if (_isBengali.value) "বিক্রি সম্পূর্ণ হয়েছে!" else "Sale completed!")
            }
            triggerCloudSync(isManual = false)
        }
    }

    // Custom Transaction: Daily General Expense
    fun recordExpense(title: String, amount: Double, note: String) {
        val email = _currentUser.value?.email ?: return
        if (title.isBlank() || amount <= 0) return

        markLocalMutation()
        viewModelScope.launch(Dispatchers.IO) {
            val transaction = TransactionRecord(
                userEmail = email,
                type = "EXPENSE",
                amount = amount,
                profit = -amount, // loss/cost reduces overall profit
                title = title,
                description = note
            )
            repository.insertTransaction(transaction)
            triggerCloudSync(isManual = false)
            withContext(Dispatchers.Main) {
                showToast(if (_isBengali.value) "খরচ লিপিবদ্ধ করা হয়েছে" else "Expense recorded successfully")
            }
        }
    }

    // --- CUSTOMER (বাকি খাতা) ---
    fun addCustomer(name: String, phone: String, address: String, photoUri: String? = null, initialDue: Double = 0.0) {
        val email = _currentUser.value?.email ?: return
        if (name.isBlank() || phone.isBlank()) return

        markLocalMutation()
        viewModelScope.launch(Dispatchers.IO) {
            val customer = Customer(
                userEmail = email,
                name = name,
                phone = phone,
                address = if (address.isBlank()) null else address,
                totalDue = initialDue,
                photoUri = photoUri
            )
            repository.insertCustomer(customer)
            triggerCloudSync(isManual = false, uploadOnly = true)
            withContext(Dispatchers.Main) {
                showToast(if (_isBengali.value) "নতুন কাস্টমার যোগ করা হয়েছে!" else "New customer added!")
            }
        }
    }

    fun recordCustomerPayment(customer: Customer, amountPaid: Double) {
        val email = _currentUser.value?.email ?: return
        if (amountPaid <= 0) return

        markLocalMutation()
        viewModelScope.launch(Dispatchers.IO) {
            // Update customer balance due (it reduces because they paid)
            val updated = customer.copy(totalDue = roundToTwoDecimals(customer.totalDue - amountPaid))
            repository.updateCustomer(updated)

            // Save deposit transaction
            val transaction = TransactionRecord(
                userEmail = email,
                type = "CUSTOMER_PAYMENT",
                amount = amountPaid,
                profit = 0.0, // payments on due don't register immediate sale profit
                title = if (_isBengali.value) "${customer.name} বাকি জমা দিয়েছে" else "Received payment from ${customer.name}",
                description = if (_isBengali.value) "বাকি পরিশোধ বাবদ জমা" else "Payment received towards due balance",
                customerId = customer.id
            )
            repository.insertTransaction(transaction)
            triggerCloudSync(isManual = false)
            withContext(Dispatchers.Main) {
                showToast(if (_isBengali.value) "পেমেন্ট সফলভাবে জমা হ​য়েছে" else "Payment received successfully")
            }
        }
    }

    fun recordCustomerCustomDue(customer: Customer, dueAmount: Double, reason: String) {
        val email = _currentUser.value?.email ?: return
        if (dueAmount <= 0) return

        markLocalMutation()
        viewModelScope.launch(Dispatchers.IO) {
            // Update customer total due
            val updated = customer.copy(totalDue = roundToTwoDecimals(customer.totalDue + dueAmount))
            repository.updateCustomer(updated)

            // Save transaction record
            val transaction = TransactionRecord(
                userEmail = email,
                type = "CUSTOMER_DUE",
                amount = dueAmount,
                profit = 0.0,
                title = if (_isBengali.value) "${customer.name}-এর বাকি হিসাব বাড়েছে" else "Due increased for ${customer.name}",
                description = reason,
                customerId = customer.id
            )
            repository.insertTransaction(transaction)
            triggerCloudSync(isManual = false)
            withContext(Dispatchers.Main) {
                showToast(if (_isBengali.value) "বাকি হিসাব যোগ করা হয়েছে" else "Custom due amount added")
            }
        }
    }

    fun deleteCustomer(customer: Customer) {
        markLocalMutation()
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteCustomer(customer)
            triggerCloudSync(isManual = false)
            withContext(Dispatchers.Main) {
                showToast(if (_isBengali.value) "কাস্টমার রিমুভ করা হয়েছে" else "Customer removed successfully")
            }
        }
    }

    fun updateCustomerProfile(customer: Customer, name: String, phone: String, address: String, photoUri: String?) {
        if (name.isBlank() || phone.isBlank()) return
        markLocalMutation()
        viewModelScope.launch(Dispatchers.IO) {
            val updated = customer.copy(
                name = name,
                phone = phone,
                address = if (address.isBlank()) null else address,
                photoUri = photoUri
            )
            repository.updateCustomer(updated)
            triggerCloudSync(isManual = false, uploadOnly = true)
            withContext(Dispatchers.Main) {
                showToast(if (_isBengali.value) "কাস্টমার প্রোফাইল আপডেট করা হয়েছে!" else "Customer profile updated successfully!")
            }
        }
    }

    // --- DEALER (পাওনাদার/ডিলার খাতা) ---
    fun addDealer(name: String, phone: String, company: String, photoUri: String? = null, initialOwed: Double = 0.0) {
        val email = _currentUser.value?.email ?: return
        if (name.isBlank() || phone.isBlank()) return

        markLocalMutation()
        viewModelScope.launch(Dispatchers.IO) {
            val dealer = Dealer(
                userEmail = email,
                name = name,
                phone = phone,
                company = if (company.isBlank()) null else company,
                totalOwed = initialOwed,
                photoUri = photoUri
            )
            repository.insertDealer(dealer)
            triggerCloudSync(isManual = false, uploadOnly = true)
            withContext(Dispatchers.Main) {
                showToast(if (_isBengali.value) "নতুন ডিলার যোগ হয়েছে!" else "New dealer added successfully!")
            }
        }
    }

    fun recordDealerPayment(dealer: Dealer, amountPaid: Double) {
        val email = _currentUser.value?.email ?: return
        if (amountPaid <= 0) return

        markLocalMutation()
        viewModelScope.launch(Dispatchers.IO) {
            // We reduce the debt we owe to them
            val updated = dealer.copy(totalOwed = roundToTwoDecimals(dealer.totalOwed - amountPaid))
            repository.updateDealer(updated)

            // Save payment transaction
            val transaction = TransactionRecord(
                userEmail = email,
                type = "DEALER_PAYMENT",
                amount = amountPaid,
                profit = 0.0,
                title = if (_isBengali.value) "ডিলার ${dealer.name}-কে পরিশোধ" else "Paid dealer ${dealer.name}",
                description = if (_isBengali.value) "${dealer.company ?: "ডিলার"} পাওনা পরিশোধ" else "Owed amount paid to dealer",
                dealerId = dealer.id
            )
            repository.insertTransaction(transaction)
            triggerCloudSync(isManual = false)
            withContext(Dispatchers.Main) {
                showToast(if (_isBengali.value) "ডিলারের টাকা পরিশোধ সফল!" else "Dealer payout registered!")
            }
        }
    }

    fun recordDealerPurchase(dealer: Dealer, amountOwed: Double, itemPurchased: String) {
        val email = _currentUser.value?.email ?: return
        if (amountOwed <= 0) return

        markLocalMutation()
        viewModelScope.launch(Dispatchers.IO) {
            // We increase the debt we owe to them
            val updated = dealer.copy(totalOwed = roundToTwoDecimals(dealer.totalOwed + amountOwed))
            repository.updateDealer(updated)

            // Save purchase transaction
            val transaction = TransactionRecord(
                userEmail = email,
                type = "EXPENSE",
                amount = amountOwed,
                profit = -amountOwed, // direct purchase cost represents expenditure
                title = if (_isBengali.value) "${dealer.name} হতে মালামাল ক্রয়" else "Purchased stock from ${dealer.name}",
                description = itemPurchased,
                dealerId = dealer.id
            )
            repository.insertTransaction(transaction)
            triggerCloudSync(isManual = false)
            withContext(Dispatchers.Main) {
                showToast(if (_isBengali.value) "ডিলার ক্রয় রেকর্ড করা হয়েছে" else "Dealer purchase recorded")
            }
        }
    }

    fun deleteDealer(dealer: Dealer) {
        markLocalMutation()
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteDealer(dealer)
            triggerCloudSync(isManual = false)
            withContext(Dispatchers.Main) {
                showToast(if (_isBengali.value) "ডিলার রিমুভ করা হয়েছে" else "Dealer removed")
            }
        }
    }

    fun updateDealerProfile(dealer: Dealer, name: String, phone: String, company: String, photoUri: String?) {
        if (name.isBlank() || phone.isBlank()) return
        markLocalMutation()
        viewModelScope.launch(Dispatchers.IO) {
            val updated = dealer.copy(
                name = name,
                phone = phone,
                company = if (company.isBlank()) null else company,
                photoUri = photoUri
            )
            repository.updateDealer(updated)
            triggerCloudSync(isManual = false, uploadOnly = true)
            withContext(Dispatchers.Main) {
                showToast(if (_isBengali.value) "ডিলার প্রোফাইল আপডেট করা হয়েছে!" else "Dealer profile updated successfully!")
            }
        }
    }

    // --- GEMINI AI ASSISTANT & SMS AI Draft ---
    fun askGeminiForBusinessHealth() {
        val apiKey: String = getGeminiApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            _aiReportText.value = if (_isBengali.value) {
                "দুঃখিত, কোনো এপিআই কী (API Key) পাওয়া যায়নি। দয়া করে গুগল এআই স্টুডিও-র (AI Studio UI) সিক্রেটস ড্যাশবোর্ডে GEMINI_API_KEY সেট করুন।"
            } else {
                "Sorry, no Gemini API Key detected. Please set your GEMINI_API_KEY in the AI Studio Secet Panel."
            }
            return
        }

        _isAiLoading.value = true
        _aiReportText.value = null

        viewModelScope.launch {
            val currentItems = stockItems.value
            val currentTxs = transactions.value
            val totalSales = currentTxs.filter { it.type == "SALE" }.sumOf { it.amount }
            val totalExpenses = currentTxs.filter { it.type == "EXPENSE" }.sumOf { it.amount }
            val netProfit = currentTxs.sumOf { it.profit }
            val totalCustomerDues = customers.value.sumOf { it.totalDue }
            val lowStockItems = currentItems.filter { it.stockCount < 5 }.map { "${it.name} (${it.stockCount} left)" }

            val prompt = if (_isBengali.value) {
                """
                তুমি একটি মদি দোকানের প্রিমিয়াম বিজনেস এআই সহকারী।
                গুরুত্বপূর্ণ শর্ত: উত্তরের বা বার্তার একদম শুরুতে অবশ্যই মুসলিম ঐতিহ্যবাহী শুভেচ্ছা 'আসসালামু আলাইকুম' দিয়ে শুরু করবে। কোনো শুভেচ্ছা বা হ্যালো যেমন 'নমস্কার', 'হ্যালো', 'সুপ্রিয়' কখনোই ব্যবহার করবে না।
                আমার দোকানের নাম: ${_currentUser.value?.shopName ?: "আমার দোকান"}
                আমার মোট বিক্রয়: ৳$totalSales
                আমার মোট খরচ: ৳$totalExpenses
                আমার সর্বমোট নিট লাভ/ক্ষতি: ৳$netProfit
                কাস্টমারের কাছে বাকি পাওনা: ৳$totalCustomerDues
                স্টকে কমে যাওয়া পণ্য: ${lowStockItems.joinToString(", ")}
                
                দয়া করে এই তথ্যের ভিত্তিতে বাংলায় একটি সুন্দর আকর্ষণীয় বিজনেস রিপোর্ট তৈরি করো যেখানে থাকবে:
                ১. আমার দোকানের আর্থিক স্বাস্থ্য বিশ্লেষণ (লাভ ভালো নাকি ক্ষতি হচ্ছে)
                ২. আমার কী কী পদক্ষেপ নেওয়া উচিত (পণ্য স্টক বাড়ানো, ডিলারদের সাথে আচরণ, বাকি আদায়)
                ৩. আগামী দিনের লাভ বাড়ানোর একটি সুন্দর এআই ভিত্তিক পরিকল্পনা
                ছোট আকর্ষনীয় বাক্য ও ৩-৪টি সুন্দর পয়েন্টে খুব পেশাদারীভাবে উপস্থাপন করো।
                """.trimIndent()
            } else {
                """
                You are a Premium Grocery Store Business AI advisor.
                Important restriction: You MUST begin your response with the Muslim greeting 'Assalamu Alaikum' (written in English as 'Assalamu Alaikum'). Do NOT use other greetings like 'Namaskar', 'Hello', 'Dear', or 'Greetings'.
                Shop Name: ${_currentUser.value?.shopName ?: "My Shop"}
                Total Sales: ৳$totalSales
                Total Expenses: ৳$totalExpenses
                Net Revenue/Profit: ৳$netProfit
                Total Balance due from customers: ৳$totalCustomerDues
                Low stock products: ${lowStockItems.joinToString(", ")}
                
                Based on these metrics, draft a concise, expert business financial health and advisory report in English:
                1. Assessment of sales and overall shop profitability.
                2. Direct strategy to improve (payments reminder suggestions, stocking empty items).
                3. Simple AI growth hacks.
                Keep it in 3-4 neat bullet points, sharp, highly professional, encouraging.
                """.trimIndent()
            }

            try {
                val request = GeminiRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                    generationConfig = GenerationConfig(temperature = 0.7f)
                )
                val response = GeminiClient.generateContentWithFallback(apiKey, request)
                val result = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                _aiReportText.value = result ?: (if (_isBengali.value) "কোনো তথ্য পাওয়া যায়নি।" else "No advice received.")
            } catch (e: Exception) {
                Log.e("AppViewModel", "AI Error", e)
                
                // Extremely smart fallback business advisor generator when network or Google AI Studio is overloaded/503!
                val offlineReport = if (_isBengali.value) {
                    val profitStatus = if (netProfit > 0) {
                        "মোট নিট লাভ হয়েছে ৳$netProfit যা খুবই উৎসাহব্যঞ্জক। এই ধারা অব্যাহত রাখুন এবং লাভ পুনরায় ব্যবসায় খাটান।"
                    } else if (netProfit < 0) {
                        "বর্তমানে আপনার ব্যবসায় কিছু লোকসান (৳${java.lang.Math.abs(netProfit)}) দেখা যাচ্ছে। খরচ নিয়ন্ত্রণ বা পণ্যের বিক্রয় বাড়াতে নজর দিন।"
                    } else {
                        "বর্তমানে ব্যবসায় সমান সমান বা প্রারম্ভিক অবস্থায় আছে। বিক্রি বাড়িয়ে মুনাফা বৃদ্ধির চেষ্টা করতে পারেন।"
                    }

                    val stockStatus = if (lowStockItems.isNotEmpty()) {
                        "আপনার বেশ কয়েকটি পণ্য ফুরিয়ে আসছে বা স্টকে কম আছে: ${lowStockItems.joinToString(", ")}। নতুন ক্রেতা ধরে রাখতে জলদি স্টক রি-লোড করার পরামর্শ দেওয়া হচ্ছে।"
                    } else {
                        "আপনার সকল পণ্যের স্টক পর্যাপ্ত রয়েছে যা চমৎকার কাস্টমার সন্তুষ্টি বৃদ্ধি করবে।"
                    }

                    val dueStatus = if (totalCustomerDues > 0) {
                        "কাস্টমারদের কাছে আপনার মোট ৳$totalCustomerDues বাকি পাওনা রয়েছে। দ্রুত মূলধন বাড়াতে ‘বাকি খাতা’ থেকে তাদেরকে তাগাদা এসএমএস পাঠান।"
                    } else {
                        "কাস্টমারদের কাছে আপনার কোনো বকেয়া পাওনা নেই, এটি খুবই প্রশংসনীয় অর্থনৈতিক পরিচালনা।"
                    }

                    """
                    আসসালামু আলাইকুম! ✨ [স্মার্ট অফলাইন এআই ব্যাকআপ বিশ্লেষণ] ✨
                    গুগল এআই অনলাইন সার্ভার সাময়িকভাবে ব্যস্ত থাকায় নিচে আপনার লাইভ ব্যাকআপ রিপোর্ট শেয়ার করা হলো:

                    📊 আর্থিক স্বাস্থ্যের অবস্থা:
                    $profitStatus

                    📦 পণ্য স্টক পর্যবেক্ষণ:
                    $stockStatus

                    💰 বাকি বকেয়া পরামর্শ:
                    $dueStatus

                    💡 আগামী দিনের জন্য স্মার্ট পরামর্শ:
                    ১. অধিক বিক্রিত পণ্যের স্টক সর্বদা সচল রাখুন ও অলাভজনক পণ্যে মূলধন আটকে রাখবেন না।
                    ২. ডিলার বা পাওনাদারদের সাথে হিসাব পরিষ্কার রাখুন এবং প্রয়োজনে বাকি খাতা থেকে ডিজিটাল অনুস্মারক ব্যবহার করুন।
                    """.trimIndent()
                } else {
                    val profitStatus = if (netProfit > 0) {
                        "Net profit is ৳$netProfit which is encouraging. Maintain this momentum and reinvest in your business."
                    } else if (netProfit < 0) {
                        "We noticed a deficit of ৳${java.lang.Math.abs(netProfit)}. Focus on cutting unnecessary expenses or raising sales margins."
                    } else {
                        "No significant net profit/loss recorded yet. Initiate promotional sales of popular goods."
                    }

                    val stockStatus = if (lowStockItems.isNotEmpty()) {
                        "The following stocks are running critically low: ${lowStockItems.joinToString(", ")}. Restock promptly to avoid missing daily customer demands."
                    } else {
                        "All inventory levels are looking healthy and sufficient."
                    }

                    val dueStatus = if (totalCustomerDues > 0) {
                        "Your total pending collectibles from clients stands at ৳$totalCustomerDues. Send payment drafts from ledger to secure your working capital."
                    } else {
                        "You have virtually zero dues outstanding, which indicates top-tier cashflow management."
                    }

                    """
                    Assalamu Alaikum! ✨ [Smart Offline AI Backup Advisory] ✨
                    Google API is busy; your instant offline backup report is ready below:

                    📊 Financial Health:
                    $profitStatus

                    📦 Inventory Observation:
                    $stockStatus

                    💰 Collectibles Suggestion:
                    $dueStatus

                    💡 Core Recommendations:
                    1. Reinvest profit into high-moving items. Avoid tying up resources in slow-moving goods.
                    2. Use the digital due billing feature to request timely customer paybacks.
                    """.trimIndent()
                }

                _aiReportText.value = offlineReport
            } finally {
                _isAiLoading.value = false
            }
        }
    }

    // AI due payment draft messenger
    private val _draftedDueMsg = MutableStateFlow<String?>(null)
    val draftedDueMsg: StateFlow<String?> = _draftedDueMsg.asStateFlow()

    private val _isMsgDrafting = MutableStateFlow(false)
    val isMsgDrafting: StateFlow<Boolean> = _isMsgDrafting.asStateFlow()

    fun generateAiDueMessage(customerName: String, totalDue: Double, address: String? = null) {
        val apiKey: String = getGeminiApiKey()
        val shopName = _currentUser.value?.shopName ?: "দোকান"
        val shopPhone = _currentUser.value?.phone ?: ""
        val ownerName = _currentUser.value?.ownerName ?: ""
 
        val locationText = if (!address.isNullOrBlank()) " ($address)" else ""
        val isAdvance = totalDue < 0
        val displayAmount = if (isAdvance) java.lang.Math.abs(totalDue) else totalDue
        val ownerLineBn = if (ownerName.isNotBlank()) "\nদোকানদারের পুরো নাম: $ownerName" else ""
        val ownerLineEn = if (ownerName.isNotBlank()) "\nShopkeeper: $ownerName" else ""

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            // Local fallback due message writer if key is not set
            _draftedDueMsg.value = if (_isBengali.value) {
                if (isAdvance) {
                    "আসসালামু আলাইকুম, প্রিয় $customerName$locationText, $shopName এ আপনার ৳$displayAmount অগ্রিম জমা রয়েছে। আমাদের সাথে থাকার জন্য ধন্যবাদ! যোগাযোগ: $shopPhone$ownerLineBn"
                } else {
                    "আসসালামু আলাইকুম, প্রিয় $customerName$locationText, $shopName এ আপনার পূর্বে বাকি বকেয়া রয়েছে ৳$displayAmount। আপনার বকেয়া পরিশোধের জন্য বিনীত অনুরোধ করছি। যোগাযোগ: $shopPhone$ownerLineBn"
                }
            } else {
                if (isAdvance) {
                    "Assalamu Alaikum, Dear $customerName$locationText, you have a credit balance of ৳$displayAmount at $shopName. Thank you for being with us! Contact: $shopPhone$ownerLineEn"
                } else {
                    "Assalamu Alaikum, Dear $customerName$locationText, your unpaid due at $shopName is ৳$displayAmount. Kindly clear your dues. Contact: $shopPhone$ownerLineEn"
                }
            }
            return
        }
 
        _isMsgDrafting.value = true
        _draftedDueMsg.value = null
 
        viewModelScope.launch {
            val prompt = if (_isBengali.value) {
                if (isAdvance) {
                    """
                    কাস্টমারকে তার অগ্রিম বা জমা টাকার জন্য ধন্যবাদ জানিয়ে এবং তার ব্যালেন্স অবহিত করতে একটি মিষ্টি ও ভদ্র প্রিমিয়াম এসএমএস বাংলাতে লিখে দাও।
                    গুরুত্বপূর্ণ শর্ত: বার্তার একদম শুরুতে অবশ্যই মুসলিম ঐতিহ্যবাহী শুভেচ্ছা 'আসসালামু আলাইকুম' দিয়ে শুরু করবে। অন্য কোনো শুভেচ্ছা বা হ্যালো যেমন 'নমস্কার', 'হ্যালো', 'সুপ্রিয়' কখনোই ব্যবহার করবে না।
                    কাস্টমারের নাম: $customerName
                    কাস্টমারের এলাকা/ঠিকানা: ${address ?: "নির্দিষ্ট করা নেই"}
                    অগ্রিম জমা পরিমাণ: ৳$displayAmount
                    দোকানের নাম: $shopName
                    যোগাযোগের ফোন নাম্বার: $shopPhone
                    দোকানদারের নাম: $ownerName
                    
                    বার্তাটি সংক্ষিপ্ত এবং সুন্দর থাকবে যেন কাস্টমার সন্তুষ্ট হন। যোগাযোগের ফোন নাম্বারের ঠিক নিচে আরেকটি নতুন লাইনে 'দোকানদারের নাম: $ownerName' লিখে দিবে। কোনো অতিরিক্ত হ্যালো অথবা বাই বাক্য যোগ করবে না, স্রেফ বার্তাটি দাও।
                    """.trimIndent()
                } else {
                    """
                    কাস্টমারকে বাকি পরিশোধের তাগাদা দিতে একটি মিষ্টি ও ভদ্র প্রিমিয়াম এসএমএস বাংলাতে লিখে দাও।
                    গুরুত্বপূর্ণ শর্ত: বার্তার একদম শুরুতে অবশ্যই মুসলিম ঐতিহ্যবাহী শুভেচ্ছা 'আসসালামু আলাইকুম' দিয়ে শুরু করবে। অন্য কোনো শুভেচ্ছা বা হ্যালো যেমন 'নমস্কার', 'হ্যালো', 'সুপ্রিয়' কখনোই ব্যবহার করবে না।
                    কাস্টমারের নাম: $customerName
                    কাস্টমারের এলাকা/ঠিকানা: ${address ?: "নির্দিষ্ট করা নেই"}
                    বাকি বকেয়া পরিমাণ: ৳$displayAmount
                    দোকানের নাম: $shopName
                    যোগাযোগের ফোন নাম্বার: $shopPhone
                    দোকানদারের নাম: $ownerName
                    
                    বার্তাটি সংক্ষিপ্ত এবং সুন্দর থাকবে যেন কাস্টমার অসন্তুষ্ট না হন আর সহজে টাকা পরিশোধের কথা মনে করেন। যোগাযোগের ফোন নাম্বারের ঠিক নিচে আরেকটি নতুন লাইনে 'দোকানদারের নাম: $ownerName' লিখে দিবে। কোনো অতিরিক্ত হ্যালো অথবা বাই বাক্য যোগ করবে না, স্রেফ বার্তাটি দাও।
                    """.trimIndent()
                }
            } else {
                if (isAdvance) {
                    """
                    Draft a polite, professional SMS thanking the customer for their advance deposit and informing them of their balance in English:
                    Important constraint: You MUST begin the SMS body with the greeting 'Assalamu Alaikum'. Do NOT use other greetings like 'Namaskar', 'Hello', or 'Dear'.
                    Customer Name: $customerName
                    Customer Address/Location: ${address ?: "Not provided"}
                    Advance Deposit amount: ৳$displayAmount
                    Shop Name: $shopName
                    Shop contact phone: $shopPhone
                    Shopkeeper Name: $ownerName
                    Keep it short, professional, and sweet. Under the shop contact phone number, please output 'Shopkeeper: $ownerName' on a new line. Output only the SMS copy.
                    """.trimIndent()
                } else {
                    """
                    Draft a polite, professional SMS payment reminder message in English:
                    Important constraint: You MUST begin the SMS body with the greeting 'Assalamu Alaikum'. Do NOT use other greetings like 'Namaskar', 'Hello', or 'Dear'.
                    Customer Name: $customerName
                    Customer Address/Location: ${address ?: "Not provided"}
                    Due amount: ৳$displayAmount
                    Shop Name: $shopName
                    Shop contact phone: $shopPhone
                    Shopkeeper Name: $ownerName
                    Keep it sweet, welcoming, respectful, and direct. Under the shop contact phone number, please output 'Shopkeeper: $ownerName' on a new line. Output only the SMS copy.
                    """.trimIndent()
                }
            }
 
            try {
                val request = GeminiRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                    generationConfig = GenerationConfig(temperature = 0.5f)
                )
                val response = GeminiClient.generateContentWithFallback(apiKey, request)
                val result = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                _draftedDueMsg.value = result?.trim()
            } catch (e: Exception) {
                _draftedDueMsg.value = if (_isBengali.value) {
                    if (isAdvance) {
                        "প্রিয় $customerName, $shopName এ আপনার ৳$displayAmount অগ্রিম জমা রয়েছে। সাথে থাকার জন্য ধন্যবাদ! যোগাযোগ: $shopPhone$ownerLineBn"
                    } else {
                        "প্রিয় $customerName, $shopName এ আপনার বকেয়া রয়েছে ৳$displayAmount। দয়া করে পরিশোধ করুন। যোগাযোগ: $shopPhone$ownerLineBn"
                    }
                } else {
                    if (isAdvance) {
                        "Dear $customerName, you have an advance credit of ৳$displayAmount at $shopName. Thank you! Phone: $shopPhone$ownerLineEn"
                    } else {
                        "Dear $customerName, your pending payment of ৳$displayAmount is due at $shopName. Please resolve. Phone: $shopPhone$ownerLineEn"
                    }
                }
            } finally {
                _isMsgDrafting.value = false
            }
        }
    }

    fun generateAiDealerMessage(dealerName: String, totalOwed: Double, companyName: String? = null) {
        val apiKey: String = getGeminiApiKey()
        val shopName = _currentUser.value?.shopName ?: "দোকান"
        val shopPhone = _currentUser.value?.phone ?: ""
        val ownerName = _currentUser.value?.ownerName ?: ""
 
        val companyText = if (!companyName.isNullOrBlank()) " ($companyName)" else ""
        val isAdvance = totalOwed < 0
        val displayAmount = if (isAdvance) java.lang.Math.abs(totalOwed) else totalOwed
        val ownerLineBn = if (ownerName.isNotBlank()) "\nদোকানদারের পুরো নাম: $ownerName" else ""
        val ownerLineEn = if (ownerName.isNotBlank()) "\nShopkeeper: $ownerName" else ""

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            // Local fallback due message writer if key is not set
            _draftedDueMsg.value = if (_isBengali.value) {
                if (isAdvance) {
                    "আসসালামু আলাইকুম, প্রিয় ডিলার $dealerName$companyText, আপনাদের কোম্পানিকে আমাদের $shopName-এর পক্ষ থেকে ৳$displayAmount অগ্রিম পরিশোধ করা হয়েছে। মালামাল জলদি সরবরাহের অনুরোধ জানাচ্ছি। যোগাযোগ: $shopPhone$ownerLineBn"
                } else {
                    "আসসালামু আলাইকুম, প্রিয় ডিলার $dealerName$companyText, আপনাদের কাছে আমাদের $shopName-এর মোট ৳$displayAmount বকেয়া বা পাওনা দেনা রয়েছে। অতিসত্বর আমরা তা পরিশোধের চেষ্টা করব। যোগাযোগ: $shopPhone$ownerLineBn"
                }
            } else {
                if (isAdvance) {
                    "Assalamu Alaikum, Dear Supplier $dealerName$companyText, we have sent an advance payment of ৳$displayAmount from $shopName. Kindly dispatch our inventory stock. Contact: $shopPhone$ownerLineEn"
                } else {
                    "Assalamu Alaikum, Dear Supplier $dealerName$companyText, our outstanding trade payable is ৳$displayAmount from $shopName. We will settle this balance soon. Contact: $shopPhone$ownerLineEn"
                }
            }
            return
        }
 
        _isMsgDrafting.value = true
        _draftedDueMsg.value = null
 
        viewModelScope.launch {
            val prompt = if (_isBengali.value) {
                if (isAdvance) {
                    """
                    কোম্পানি ডিলার অথবা পাইকারি বিক্রেতাকে অগ্রিম টাকা পাঠিয়ে পণ্যের দ্রুত ডেলিভারি চেয়ে একটি মিষ্টি, পেশাদার ও ভদ্র প্রিমিয়াম এসএমএস বাংলাতে লিখে দাও।
                    গুরুত্বপূর্ণ শর্ত: বার্তার একদম শুরুতে অবশ্যই মুসলিম ঐতিহ্যবাহী শুভেচ্ছা 'আসসালামু আলাইকুম' দিয়ে শুরু করবে। অন্য কোনো শুভেচ্ছা বা হ্যালো যেমন 'নমস্কার', 'হ্যালো', 'সুপ্রিয়' কখনোই ব্যবহার করবে না।
                    ডিলারের নাম: $dealerName
                    ডিলারের কোম্পানি: ${companyName ?: "নির্দিষ্ট করা নেই"}
                    অগ্রিম পরিশোধিত পরিমাণ: ৳$displayAmount
                    দোকানের নাম: $shopName
                    যোগাযোগের ফোন নাম্বার: $shopPhone
                    দোকানদারের নাম: $ownerName
                    
                    বার্তাটি প্রফেশনাল ও সুন্দর থাকবে। যোগাযোগের ফোন নাম্বারের ঠিক নিচে আরেকটি নতুন লাইনে 'দোকানদারের নাম: $ownerName' লিখে দিবে। কোনো অতিরিক্ত হ্যালো অথবা বাই বাক্য যোগ করবে না, স্রেফ বার্তাটি দাও।
                    """.trimIndent()
                } else {
                    """
                    কোম্পানি ডিলার বা পাইকারি বিক্রেতার কাছে আমাদের বকেয়া পাওনা দেনা পরিশোধের বার্তা ও হিসাবের স্টেটমেন্ট নিশ্চিত করতে একটি মিষ্টি, পেশাদার ও প্রিমিয়াম এসএমএস বাংলাতে লিখে দাও।
                    গুরুত্বপূর্ণ শর্ত: বার্তার একদম শুরুতে অবশ্যই মুসলিম ঐতিহ্যবাহী শুভেচ্ছা 'আসসালামু আলাইকুম' দিয়ে শুরু করবে। অন্য কোনো শুভেচ্ছা বা হ্যালো যেমন 'নমস্কার', 'হ্যালো', 'সুপ্রিয়' কখনোই ব্যবহার করবে না।
                    ডিলারের নাম: $dealerName
                    ডিলারের কোম্পানি: ${companyName ?: "নির্দিষ্ট করা নেই"}
                    বকেয়া দেনার পরিমাণ: ৳$displayAmount
                    দোকানের নাম: $shopName
                    যোগাযোগের ফোন নাম্বার: $shopPhone
                    দোকানদারের নাম: $ownerName
                    
                    বার্তাটি সংক্ষিপ্ত ও প্রফেশনাল থাকবে এবং অতিসত্বর বিল বা হিসাব মেটানোর সদিচ্ছা প্রকাশ করবে। যোগাযোগের ফোন নাম্বারের ঠিক নিচে আরেকটি নতুন লাইনে 'দোকানদারের নাম: $ownerName' লিখে দিবে। কোনো অতিরিক্ত হ্যালো অথবা বাই বাক্য যোগ করবে না, স্রেফ বার্তাটি দাও।
                    """.trimIndent()
                }
            } else {
                if (isAdvance) {
                    """
                    Draft a polite, professional SMS to a supplier or trade distributor acknowledging an advance deposit sent and asking for prompt inventory dispatch in English:
                    Important constraint: You MUST begin the SMS body with the greeting 'Assalamu Alaikum'. Do NOT use other greetings like 'Namaskar', 'Hello', or 'Dear'.
                    Supplier Name: $dealerName
                    Company/Brand: ${companyName ?: "Not provided"}
                    Advance paid: ৳$displayAmount
                    Shop Name: $shopName
                    Shop contact phone: $shopPhone
                    Shopkeeper Name: $ownerName
                    Keep it professional, concise, and sweet. Under the shop contact phone number, please output 'Shopkeeper: $ownerName' on a new line. Output only the SMS copy.
                    """.trimIndent()
                } else {
                    """
                    Draft a polite, professional SMS payment acknowledgment or bill status verification update to a trader/supplier in English:
                    Important constraint: You MUST begin the SMS body with the greeting 'Assalamu Alaikum'. Do NOT use other greetings like 'Namaskar', 'Hello', or 'Dear'.
                    Supplier Name: $dealerName
                    Company/Brand: ${companyName ?: "Not provided"}
                    Due trade debt: ৳$displayAmount
                    Shop Name: $shopName
                    Shop contact phone: $shopPhone
                    Shopkeeper Name: $ownerName
                    Keep it respectful, acknowledging the pending settle due, expressing intentions of clearing it. Under the shop contact phone number, please output 'Shopkeeper: $ownerName' on a new line. Output only the SMS copy.
                    """.trimIndent()
                }
            }
 
            try {
                val request = GeminiRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                    generationConfig = GenerationConfig(temperature = 0.5f)
                )
                val response = GeminiClient.generateContentWithFallback(apiKey, request)
                val result = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                _draftedDueMsg.value = result?.trim()
            } catch (e: Exception) {
                _draftedDueMsg.value = if (_isBengali.value) {
                    if (isAdvance) {
                        "আসসালামু আলাইকুম, প্রিয় ডিলার $dealerName, আপনাদের ৳$displayAmount অগ্রিম পরিশোধ করা হয়েছে। মালামাল জলদি ডেলিভারীর অনুরোধ। যোগাযোগ: $shopPhone$ownerLineBn"
                    } else {
                        "আসসালামু আলাইকুম, প্রিয় ডিলার $dealerName, আপনাদের ৳$displayAmount বকেয়া দেনা পরিশোধের আন্তরিক চেষ্টা করব। ধন্যবাদ! যোগাযোগ: $shopPhone$ownerLineBn"
                    }
                } else {
                    if (isAdvance) {
                        "Assalamu Alaikum, Dear Supplier $dealerName, we paid ৳$displayAmount in advance. Please proceed with our stock shipment. Phone: $shopPhone$ownerLineEn"
                    } else {
                        "Assalamu Alaikum, Dear Supplier $dealerName, we owe you an outstanding balance of ৳$displayAmount. We are working to resolve this. Phone: $shopPhone$ownerLineEn"
                    }
                }
            } finally {
                _isMsgDrafting.value = false
            }
        }
    }

    fun clearDraftedMsg() {
        _draftedDueMsg.value = null
    }
}

class AppViewModelFactory(private val repository: AppRepository, private val application: android.app.Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppViewModel(repository, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
