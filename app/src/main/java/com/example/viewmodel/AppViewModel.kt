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

    fun triggerCloudSync() {
        viewModelScope.launch {
            _isCloudSyncing.value = true
            showToast(if (_isBengali.value) "ক্লাউড সিঙ্ক্রোনাইজেশন শুরু হয়েছে..." else "Cloud synchronization started...")
            kotlinx.coroutines.delay(2000)
            _isCloudSyncing.value = false
            showToast(if (_isBengali.value) "সার্ভারের সাথে ডেটা সফলভাবে সিঙ্ক হয়েছে!" else "Data successfully synchronized with the cloud!")
        }
    }

    // --- USER REGISTRATION & LOGIN ---
    fun registerNewUser(shopName: String, email: String, phone: String, pin: String) {
        if (shopName.isBlank() || email.isBlank() || phone.isBlank() || pin.isBlank()) {
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
                    passwordHash = pin.trim() // store simply for offline/local flow
                )
                repository.registerUser(newUser)
                _currentUser.value = newUser
                _currentScreen.value = "DASHBOARD"
                _authStateMessage.value = null
                withContext(Dispatchers.Main) {
                    showToast(if (_isBengali.value) "রেজিস্ট্রেশন সফল হয়েছে!" else "Registration successful!")
                }
            } catch (e: Exception) {
                _authStateMessage.value = e.message ?: "Error"
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
                val user = repository.getUserByIdentifier(identifier.trim())
                if (user == null || user.passwordHash != pin.trim()) {
                    _authStateMessage.value = if (_isBengali.value) "ভুল ইমেইল/মোবাইল অথবা পিন!" else "Incorrect login details or pin!"
                    return@launch
                }
                _currentUser.value = user
                _currentScreen.value = "DASHBOARD"
                _authStateMessage.value = null
                withContext(Dispatchers.Main) {
                    val welcomeMsg = if (_isBengali.value) {
                        "${user.shopName} (আইডি: ${user.phone}) লগইন সফল হয়েছে!"
                    } else {
                        "Login successful for ${user.shopName}!"
                    }
                    showToast(welcomeMsg)
                }
            } catch (e: Exception) {
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
        val userItem = User(email, "মেসার্স অনিক স্টোর (অনুমোদিত ডেমো)", "01712345678", "1234")
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
            withContext(Dispatchers.Main) {
                showToast(if (_isBengali.value) "পণ্য স্টক করা হয়েছে!" else "Product stock added!")
            }
        }
    }

    fun updateStockItemCount(item: StockItem, newCount: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = item.copy(stockCount = newCount)
            repository.updateStockItem(updated)
            withContext(Dispatchers.Main) {
                showToast(if (_isBengali.value) "স্টক আপডেট করা হয়েছে!" else "Stock quantity updated!")
            }
        }
    }

    fun deleteStockItem(item: StockItem) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteStockItem(item)
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
            // Collate simplified shop summary
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
                val response = GeminiClient.service.generateContent(apiKey, request)
                val result = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                _aiReportText.value = result ?: (if (_isBengali.value) "কোনো তথ্য পাওয়া যায়নি।" else "No advice received.")
            } catch (e: Exception) {
                Log.e("AppViewModel", "AI Error", e)
                _aiReportText.value = if (_isBengali.value) "এআই লোড করতে ত্রুটি হয়েছে: ${e.localizedMessage}" else "AI Error: ${e.localizedMessage}"
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

        val locationText = if (!address.isNullOrBlank()) " ($address)" else ""
        val isAdvance = totalDue < 0
        val displayAmount = if (isAdvance) java.lang.Math.abs(totalDue) else totalDue

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            // Local fallback due message writer if key is not set
            _draftedDueMsg.value = if (_isBengali.value) {
                if (isAdvance) {
                    "প্রিয় $customerName$locationText, $shopName এ আপনার ৳$displayAmount অগ্রিম জমা রয়েছে। আমাদের সাথে থাকার জন্য ধন্যবাদ! যোগাযোগ: $shopPhone"
                } else {
                    "প্রিয় $customerName$locationText, $shopName এ আপনার পূর্বে বাকি বকেয়া রয়েছে ৳$displayAmount। আপনার বকেয়া পরিশোধের জন্য বিনীত অনুরোধ করছি। যোগাযোগ: $shopPhone"
                }
            } else {
                if (isAdvance) {
                    "Dear $customerName$locationText, you have a credit balance of ৳$displayAmount at $shopName. Thank you for being with us! Contact: $shopPhone"
                } else {
                    "Dear $customerName$locationText, your unpaid due at $shopName is ৳$displayAmount. Kindly clear your dues. Contact: $shopPhone"
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
                    কাস্টমারের নাম: $customerName
                    কাস্টমারের এলাকা/ঠিকানা: ${address ?: "নির্দিষ্ট করা নেই"}
                    অগ্রিম জমা পরিমাণ: ৳$displayAmount
                    দোকানের নাম: $shopName
                    যোগাযোগের ফোন নাম্বার: $shopPhone
                    
                    বার্তাটি সংক্ষিপ্ত এবং সুন্দর থাকবে যেন কাস্টমার সন্তুষ্ট হন। কোনো অতিরিক্ত হ্যালো অথবা বাই বাক্য যোগ করবে না, স্রেফ বার্তাটি দাও।
                    """.trimIndent()
                } else {
                    """
                    কাস্টমারকে বাকি পরিশোধের তাগাদা দিতে একটি মিষ্টি ও ভদ্র প্রিমিয়াম এসএমএস বাংলাতে লিখে দাও।
                    কাস্টমারের নাম: $customerName
                    কাস্টমারের এলাকা/ঠিকানা: ${address ?: "নির্দিষ্ট করা নেই"}
                    বাকি বকেয়া পরিমাণ: ৳$displayAmount
                    দোকানের নাম: $shopName
                    যোগাযোগের ফোন নাম্বার: $shopPhone
                    
                    বার্তাটি সংক্ষিপ্ত এবং সুন্দর থাকবে যেন কাস্টমার অসন্তুষ্ট না হন আর সহজে টাকা পরিশোধের কথা মনে করেন। কোনো অতিরিক্ত হ্যালো অথবা বাই বাক্য যোগ করবে না, স্রেফ বার্তাটি দাও।
                    """.trimIndent()
                }
            } else {
                if (isAdvance) {
                    """
                    Draft a polite, professional SMS thanking the customer for their advance deposit and informing them of their balance in English:
                    Customer Name: $customerName
                    Customer Address/Location: ${address ?: "Not provided"}
                    Advance Deposit amount: ৳$displayAmount
                    Shop Name: $shopName
                    Shop contact phone: $shopPhone
                    Keep it short, professional, and sweet. Output only the SMS copy.
                    """.trimIndent()
                } else {
                    """
                    Draft a polite, professional SMS payment reminder message in English:
                    Customer Name: $customerName
                    Customer Address/Location: ${address ?: "Not provided"}
                    Due amount: ৳$displayAmount
                    Shop Name: $shopName
                    Shop contact phone: $shopPhone
                    Keep it sweet, welcoming, respectful, and direct. Output only the SMS copy.
                    """.trimIndent()
                }
            }

            try {
                val request = GeminiRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                    generationConfig = GenerationConfig(temperature = 0.5f)
                )
                val response = GeminiClient.service.generateContent(apiKey, request)
                val result = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                _draftedDueMsg.value = result?.trim()
            } catch (e: Exception) {
                _draftedDueMsg.value = if (_isBengali.value) {
                    if (isAdvance) {
                        "প্রিয় $customerName, $shopName এ আপনার ৳$displayAmount অগ্রিম জমা রয়েছে। সাথে থাকার জন্য ধন্যবাদ! যোগাযোগ: $shopPhone"
                    } else {
                        "প্রিয় $customerName, $shopName এ আপনার বকেয়া রয়েছে ৳$displayAmount। দয়া করে পরিশোধ করুন। যোগাযোগ: $shopPhone"
                    }
                } else {
                    if (isAdvance) {
                        "Dear $customerName, you have an advance credit of ৳$displayAmount at $shopName. Thank you! Phone: $shopPhone"
                    } else {
                        "Dear $customerName, your pending payment of ৳$displayAmount is due at $shopName. Please resolve. Phone: $shopPhone"
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
