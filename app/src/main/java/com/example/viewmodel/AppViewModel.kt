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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(private val repository: AppRepository) : ViewModel() {

    // Language State: true for Bengali, false for English
    private val _isBengali = MutableStateFlow(true)
    val isBengali: StateFlow<Boolean> = _isBengali.asStateFlow()

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
            showToast(if (_isBengali.value) "${targetUser.shopName} এ পরিবর্তন করা হয়েছে" else "Switched to ${targetUser.shopName}")
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
        viewModelScope.launch {
            _currentUser.collect { user ->
                if (user != null) {
                    loadShopsForActiveUser()
                    triggerCloudSync(isManual = false)
                }
            }
        }

        // Periodic Cloud Sync Loop: Automatically sync every 30 seconds
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30000)
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

    fun triggerCloudSync(isManual: Boolean = true) {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isCloudSyncing.value = true
            if (isManual) {
                withContext(Dispatchers.Main) {
                    showToast(if (_isBengali.value) "সার্ভারের সাথে সিঙ্ক শুরু হয়েছে..." else "Cloud sync started...")
                }
            }

            try {
                // 1. Download payload from Cloud
                val remotePayload = com.example.api.CloudSyncEngine.downloadPayload(user.email)
                
                // 2. Prepare local sets
                val localStock = stockItems.value
                val localCustomers = customers.value
                val localDealers = dealers.value
                val localTx = transactions.value

                if (remotePayload != null) {
                    // --- MERGING STOCK ITEMS ---
                    val allStockNames = (localStock.map { it.name } + remotePayload.stockItems.map { it.name }).distinct()
                    for (name in allStockNames) {
                        val localItem = localStock.find { it.name == name }
                        val remoteItem = remotePayload.stockItems.find { it.name == name }
                        if (localItem != null && remoteItem != null) {
                            // Sync remote values to local to keep devices matching
                            val updated = localItem.copy(
                                purchasePrice = remoteItem.purchasePrice,
                                salesPrice = remoteItem.salesPrice,
                                stockCount = remoteItem.stockCount,
                                category = remoteItem.category,
                                unit = remoteItem.unit
                            )
                            repository.updateStockItem(updated)
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
                            val updated = matchingLocal.copy(
                                address = remoteCust.address ?: matchingLocal.address,
                                totalDue = remoteCust.totalDue,
                                photoUri = remoteCust.photoUri ?: matchingLocal.photoUri
                            )
                            repository.updateCustomer(updated)
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
                            val updated = matchingLocal.copy(
                                company = remoteDlr.company ?: matchingLocal.company,
                                totalOwed = remoteDlr.totalOwed,
                                photoUri = remoteDlr.photoUri ?: matchingLocal.photoUri
                            )
                            repository.updateDealer(updated)
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
                }

                // 3. Upload latest consolidated merged local database back to Cloud
                val finalStock = repository.getStockItems(user.email).firstOrNull() ?: emptyList()
                val finalCustomers = repository.getCustomers(user.email).firstOrNull() ?: emptyList()
                val finalDealers = repository.getDealers(user.email).firstOrNull() ?: emptyList()
                val finalTx = repository.getTransactions(user.email).firstOrNull() ?: emptyList()

                val uploadPayload = com.example.api.SyncPayload(
                    user = user,
                    stockItems = finalStock,
                    customers = finalCustomers,
                    dealers = finalDealers,
                    transactions = finalTx,
                    timestamp = System.currentTimeMillis()
                )

                val uploadSuccess = com.example.api.CloudSyncEngine.uploadPayload(user.email, uploadPayload)

                withContext(Dispatchers.Main) {
                    _isCloudSyncing.value = false
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
                val existing = repository.getUser(email)
                if (existing != null) {
                    _authStateMessage.value = if (_isBengali.value) "এই ইমেইলটি ইতিমধ্যে ব্যবহৃত হয়েছে!" else "Email already registered!"
                    return@launch
                }
                val newUser = User(
                    email = email.trim(),
                    shopName = shopName.trim(),
                    phone = phone.trim(),
                    passwordHash = pin.trim(), // store simply for offline/local flow
                    ownerName = ownerName.trim(),
                    profilePicture = profilePic,
                    shopPicture = shopPic
                )
                repository.registerUser(newUser)
                
                // Immediate Cloud Upload
                val initialPayload = com.example.api.SyncPayload(
                    user = newUser,
                    stockItems = emptyList(),
                    customers = emptyList(),
                    dealers = emptyList(),
                    transactions = emptyList(),
                    timestamp = System.currentTimeMillis()
                )
                com.example.api.CloudSyncEngine.uploadPayload(newUser.email, initialPayload)

                _currentUser.value = newUser
                _currentScreen.value = "DASHBOARD"
                _authStateMessage.value = null
                triggerCloudSync(isManual = false)
                withContext(Dispatchers.Main) {
                    showToast(if (_isBengali.value) "রেজিস্ট্রেশন সফল হয়েছে!" else "Registration successful!")
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
                val updatedUser = User(
                    email = targetEmail,
                    shopName = shopName.trim(),
                    phone = phone.trim(),
                    passwordHash = pin.trim(),
                    profilePicture = profilePic,
                    ownerName = ownerName.trim(),
                    shopPicture = shopPic
                )

                if (oldUser != null && oldUser.email != targetEmail) {
                    // Check if new email is already taken
                    val existing = repository.getUser(targetEmail)
                    if (existing != null) {
                        withContext(Dispatchers.Main) {
                            showToast(if (_isBengali.value) "এই ইমেইলটি ইতিমধ্যে অন্য দোকানে ব্যবহৃত হয়েছে!" else "This email is already in use by another shop!")
                        }
                        return@launch
                    }
                    // Register new user record
                    repository.registerUser(updatedUser)
                    // Cascade update references
                    repository.updateStockItemEmail(oldUser.email, targetEmail)
                    repository.updateCustomerEmail(oldUser.email, targetEmail)
                    repository.updateDealerEmail(oldUser.email, targetEmail)
                    repository.updateTransactionEmail(oldUser.email, targetEmail)
                    // Delete old user record
                    repository.deleteUserByEmail(oldUser.email)
                } else {
                    repository.updateUser(updatedUser)
                }

                _currentUser.value = updatedUser

                // Sync updated user to Cloud Sync bucket
                val cloudPayload = com.example.api.CloudSyncEngine.downloadPayload(targetEmail)
                val newPayload = if (cloudPayload != null) {
                    cloudPayload.copy(user = updatedUser, timestamp = System.currentTimeMillis())
                } else {
                    com.example.api.SyncPayload(
                        user = updatedUser,
                        stockItems = emptyList(),
                        customers = emptyList(),
                        dealers = emptyList(),
                        transactions = emptyList(),
                        timestamp = System.currentTimeMillis()
                    )
                }
                com.example.api.CloudSyncEngine.uploadPayload(targetEmail, newPayload)

                withContext(Dispatchers.Main) {
                    showToast(if (_isBengali.value) "প্রোফাইল সফলভাবে আপডেট ও সিন্ক্রোনাইজ করা হয়েছে!" else "Profile updated and synchronized successfully!")
                }
            } catch (e: Exception) {
                Log.e("AppViewModel", "Profile update error", e)
                withContext(Dispatchers.Main) {
                    showToast(if (_isBengali.value) "স্থানীয়ভাবে সেভ হয়েছে কিন্তু ক্লাউড আপডেট ব্যর্থ হয়েছে" else "Saved locally but cloud update failed")
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
                    cloudPayload.copy(user = updatedUser, timestamp = System.currentTimeMillis())
                } else {
                    com.example.api.SyncPayload(
                        user = updatedUser,
                        stockItems = emptyList(),
                        customers = emptyList(),
                        dealers = emptyList(),
                        transactions = emptyList(),
                        timestamp = System.currentTimeMillis()
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
                    cloudPayload.copy(user = updatedUser, timestamp = System.currentTimeMillis())
                } else {
                    com.example.api.SyncPayload(
                        user = updatedUser,
                        stockItems = emptyList(),
                        customers = emptyList(),
                        dealers = emptyList(),
                        transactions = emptyList(),
                        timestamp = System.currentTimeMillis()
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
                            cloudPayload.stockItems.forEach { repository.insertStockItem(it.copy(id = 0)) }
                            
                            val localCustomerMap = mutableMapOf<Int, Int>()
                            cloudPayload.customers.forEach { cust ->
                                repository.insertCustomer(cust.copy(id = 0))
                            }
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

                            val localDealerMap = mutableMapOf<Int, Int>()
                            cloudPayload.dealers.forEach { dlr ->
                                repository.insertDealer(dlr.copy(id = 0))
                            }
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

                            cloudPayload.transactions.forEach { tx ->
                                val localCustId = tx.customerId?.let { localCustomerMap[it] }
                                val localDlrId = tx.dealerId?.let { localDealerMap[it] }
                                repository.insertTransaction(tx.copy(id = 0, customerId = localCustId, dealerId = localDlrId))
                            }

                            user = cloudUser
                        }
                    }
                }

                if (user == null || user.passwordHash != pin.trim()) {
                    _authStateMessage.value = if (_isBengali.value) "ভুল ইমেইল/মোবাইল অথবা পিন!" else "Incorrect login details or pin!"
                    return@launch
                }
                
                _currentUser.value = user
                _currentScreen.value = "DASHBOARD"
                _authStateMessage.value = null

                // Fire a background sync to fetch any latest updates
                triggerCloudSync(isManual = false)

                withContext(Dispatchers.Main) {
                    val welcomeMsg = if (_isBengali.value) {
                        "${user.shopName} (আইডি: ${user.phone}) লগইন সফল হয়েছে!"
                    } else {
                        "Login successful for ${user.shopName}!"
                    }
                    showToast(welcomeMsg)
                }
            } catch (e: Exception) {
                Log.e("AppViewModel", "Login error", e)
                _authStateMessage.value = e.message ?: "Authentication error"
            }
        }
    }

    fun logout() {
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

    // --- SALES & BILLING (টালি ও বিক্রি) ---
    fun recordSale(item: StockItem, quantityToSell: Int, customerId: Int? = null) {
        val email = _currentUser.value?.email ?: return
        if (item.stockCount < quantityToSell) {
            showToast(if (_isBengali.value) "স্টকে পর্যাপ্ত মালামাল নেই!" else "Insufficient stock available!")
            return
        }

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
                // Fetch and update customer total due balance
                // Simple update will read from current customer list and update
                val currentCustomers = customers.value
                val matched = currentCustomers.find { it.id == customerId }
                if (matched != null) {
                    val updatedCustomer = matched.copy(totalDue = matched.totalDue + saleAmount)
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
            triggerCloudSync(isManual = false)
            withContext(Dispatchers.Main) {
                showToast(if (_isBengali.value) "নতুন কাস্টমার যোগ করা হয়েছে!" else "New customer added!")
            }
        }
    }

    fun recordCustomerPayment(customer: Customer, amountPaid: Double) {
        val email = _currentUser.value?.email ?: return
        if (amountPaid <= 0) return

        viewModelScope.launch(Dispatchers.IO) {
            // Update customer balance due (it reduces because they paid)
            val updated = customer.copy(totalDue = customer.totalDue - amountPaid)
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
            withContext(Dispatchers.Main) {
                showToast(if (_isBengali.value) "পেমেন্ট সফলভাবে জমা হ​য়েছে" else "Payment received successfully")
            }
        }
    }

    fun recordCustomerCustomDue(customer: Customer, dueAmount: Double, reason: String) {
        val email = _currentUser.value?.email ?: return
        if (dueAmount <= 0) return

        viewModelScope.launch(Dispatchers.IO) {
            // Update customer total due
            val updated = customer.copy(totalDue = customer.totalDue + dueAmount)
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
            withContext(Dispatchers.Main) {
                showToast(if (_isBengali.value) "বাকি হিসাব যোগ করা হয়েছে" else "Custom due amount added")
            }
        }
    }

    fun deleteCustomer(customer: Customer) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteCustomer(customer)
            withContext(Dispatchers.Main) {
                showToast(if (_isBengali.value) "কাস্টমার রিমুভ করা হয়েছে" else "Customer removed successfully")
            }
        }
    }

    fun updateCustomerProfile(customer: Customer, name: String, phone: String, address: String, photoUri: String?) {
        if (name.isBlank() || phone.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val updated = customer.copy(
                name = name,
                phone = phone,
                address = if (address.isBlank()) null else address,
                photoUri = photoUri
            )
            repository.updateCustomer(updated)
            withContext(Dispatchers.Main) {
                showToast(if (_isBengali.value) "কাস্টমার প্রোফাইল আপডেট করা হয়েছে!" else "Customer profile updated successfully!")
            }
        }
    }

    // --- DEALER (পাওনাদার/ডিলার খাতা) ---
    fun addDealer(name: String, phone: String, company: String, photoUri: String? = null, initialOwed: Double = 0.0) {
        val email = _currentUser.value?.email ?: return
        if (name.isBlank() || phone.isBlank()) return

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
            withContext(Dispatchers.Main) {
                showToast(if (_isBengali.value) "নতুন ডিলার যোগ হয়েছে!" else "New dealer added successfully!")
            }
        }
    }

    fun recordDealerPayment(dealer: Dealer, amountPaid: Double) {
        val email = _currentUser.value?.email ?: return
        if (amountPaid <= 0) return

        viewModelScope.launch(Dispatchers.IO) {
            // We reduce the debt we owe to them
            val updated = dealer.copy(totalOwed = dealer.totalOwed - amountPaid)
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
            withContext(Dispatchers.Main) {
                showToast(if (_isBengali.value) "ডিলারের টাকা পরিশোধ সফল!" else "Dealer payout registered!")
            }
        }
    }

    fun recordDealerPurchase(dealer: Dealer, amountOwed: Double, itemPurchased: String) {
        val email = _currentUser.value?.email ?: return
        if (amountOwed <= 0) return

        viewModelScope.launch(Dispatchers.IO) {
            // We increase the debt we owe to them
            val updated = dealer.copy(totalOwed = dealer.totalOwed + amountOwed)
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
            withContext(Dispatchers.Main) {
                showToast(if (_isBengali.value) "ডিলার ক্রয় রেকর্ড করা হয়েছে" else "Dealer purchase recorded")
            }
        }
    }

    fun deleteDealer(dealer: Dealer) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteDealer(dealer)
            withContext(Dispatchers.Main) {
                showToast(if (_isBengali.value) "ডিলার রিমুভ করা হয়েছে" else "Dealer removed")
            }
        }
    }

    fun updateDealerProfile(dealer: Dealer, name: String, phone: String, company: String, photoUri: String?) {
        if (name.isBlank() || phone.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val updated = dealer.copy(
                name = name,
                phone = phone,
                company = if (company.isBlank()) null else company,
                photoUri = photoUri
            )
            repository.updateDealer(updated)
            withContext(Dispatchers.Main) {
                showToast(if (_isBengali.value) "ডিলার প্রোফাইল আপডেট করা হয়েছে!" else "Dealer profile updated successfully!")
            }
        }
    }

    // --- GEMINI AI ASSISTANT & SMS AI Draft ---
    fun askGeminiForBusinessHealth() {
        val apiKey: String = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" } ?: ""
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
        val apiKey: String = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" } ?: ""
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
        val apiKey: String = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" } ?: ""
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

class AppViewModelFactory(private val repository: AppRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
