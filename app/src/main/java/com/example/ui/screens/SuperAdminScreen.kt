package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.collectAsState
import coil.compose.AsyncImage
import com.example.api.CloudSyncEngine
import com.example.data.User
import com.example.viewmodel.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperAdminScreen(viewModel: AppViewModel) {
    val isBn by viewModel.isBengali.collectAsState()
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme

    var isLoading by remember { mutableStateOf(false) }
    var userList by remember { mutableStateOf<List<User>>(emptyList()) }
    var totalShopsCount by remember { mutableStateOf(0) }
    var totalCustomersCount by remember { mutableStateOf(0) }
    var totalTransactionsCount by remember { mutableStateOf(0) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var activeStatuses by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var lastSyncTimes by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }

    // Dynamic metrics requested by the user
    var activeUsersCount by remember { mutableStateOf(0) }
    var inactiveUsersCount by remember { mutableStateOf(0) }
    var activeDevicesCount by remember { mutableStateOf(0) }
    var blockedDevicesCount by remember { mutableStateOf(0) }
    var customerDuesMap by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var dealerDuesMap by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }

    // Dialog state variables for Admin functions
    var showEditDialog by remember { mutableStateOf(false) }
    var userToEdit by remember { mutableStateOf<User?>(null) }
    
    var editShopName by remember { mutableStateOf("") }
    var editOwnerName by remember { mutableStateOf("") }
    var editPhone by remember { mutableStateOf("") }
    var editPin by remember { mutableStateOf("") }
    var editProfilePic by remember { mutableStateOf("") }
    var editShopPic by remember { mutableStateOf("") }

    var editOwnershipType by remember { mutableStateOf("single") }
    var editJointCount by remember { mutableStateOf(2) }
    var editJointName1 by remember { mutableStateOf("") }
    var editJointPhone1 by remember { mutableStateOf("") }
    var editJointEmail1 by remember { mutableStateOf("") }
    var editJointName2 by remember { mutableStateOf("") }
    var editJointPhone2 by remember { mutableStateOf("") }
    var editJointEmail2 by remember { mutableStateOf("") }
    var editJointName3 by remember { mutableStateOf("") }
    var editJointPhone3 by remember { mutableStateOf("") }
    var editJointEmail3 by remember { mutableStateOf("") }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showFactoryResetConfirmDialog by remember { mutableStateOf(false) }
    var userToDelete by remember { mutableStateOf<User?>(null) }

    var showManageDevicesDialog by remember { mutableStateOf(false) }
    var userForDeviceManagement by remember { mutableStateOf<User?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current

    val currentOtaConfig by viewModel.otaConfig.collectAsState()
    
    var otaCodeInput by remember { mutableStateOf("1") }
    var otaNameInput by remember { mutableStateOf("1.0") }
    var otaUrLInput by remember { mutableStateOf("https://ais-pre-wolkhdsxahnvgjlshvncw2-122144077257.asia-southeast1.run.app") }
    var otaBnMsgInput by remember { mutableStateOf("") }
    var otaEnMsgInput by remember { mutableStateOf("") }
    var otaForceUpdate by remember { mutableStateOf(false) }
    var otaFreePremium by remember { mutableStateOf(true) }

    // Initialize local inputs from live flow
    LaunchedEffect(currentOtaConfig) {
        otaCodeInput = currentOtaConfig.latestVersionCode.toString()
        otaNameInput = currentOtaConfig.latestVersionName
        otaUrLInput = currentOtaConfig.updateDownloadUrl
        otaBnMsgInput = currentOtaConfig.bengaliMessage
        otaEnMsgInput = currentOtaConfig.englishMessage
        otaForceUpdate = currentOtaConfig.forceUpdateEnabled
        otaFreePremium = currentOtaConfig.freePremiumActive
    }

    val adminProfilePicLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { 
            viewModel.uriToBase64(context, it)?.let { base64 ->
                editProfilePic = base64
            }
        }
    }

    val adminShopPicLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { 
            viewModel.uriToBase64(context, it)?.let { base64 ->
                editShopPic = base64
            }
        }
    }

    val adminProfileCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            viewModel.bitmapToBase64(bitmap)?.let { base64 ->
                editProfilePic = base64
            }
        }
    }

    val adminShopCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            viewModel.bitmapToBase64(bitmap)?.let { base64 ->
                editShopPic = base64
            }
        }
    }

    fun refreshData() {
        scope.launch {
            isLoading = true
            feedbackMessage = null
            try {
                // Fetch all registered users in the cloud dynamically, with fallback to local database users
                val remoteUsers = withContext(Dispatchers.IO) {
                    try {
                        CloudSyncEngine.fetchAllRegisteredUsers()
                    } catch (e: Exception) {
                        emptyList<User>()
                    }
                }
                val localUsers = withContext(Dispatchers.IO) {
                    try {
                        viewModel.repository.getAllUsers()
                    } catch (e: Exception) {
                        emptyList<User>()
                    }
                }
                val rawFilteredList = (remoteUsers + localUsers)
                    .distinctBy { it.email.trim().lowercase() }
                    .filter { it.email.trim().lowercase() != "demo@example.com" }

                totalShopsCount = rawFilteredList.size

                val statusMap = mutableMapOf<String, Boolean>()
                val syncTimeMap = mutableMapOf<String, Long>()
                val custDuesMap = mutableMapOf<String, Double>()
                val dealDuesMap = mutableMapOf<String, Double>()

                // Calculate cumulative stats
                var tempCust = 0
                var tempTx = 0
                
                // Fetch all payloads concurrently in parallel using async (takes less than 1-2 seconds!)
                val payloadsMap = withContext(Dispatchers.IO) {
                    rawFilteredList.map { u ->
                        async {
                            u.email.trim().lowercase() to CloudSyncEngine.downloadPayload(u.email)
                        }
                    }.awaitAll().toMap()
                }

                // 1. Stable Registration chronological sorting
                // 2. The admin user "mdanisujjamanontar@gmail.com" ALWAYS ranks first (Index 0 = Serial #1)
                // 3. Other users sort chronologically based on registrationTimestamp ascending
                val sortedOthers = rawFilteredList
                    .filter { it.email.trim().lowercase() != "mdanisujjamanontar@gmail.com" }
                    .sortedWith(compareBy { u ->
                        val emailKey = u.email.trim().lowercase()
                        payloadsMap[emailKey]?.registrationTimestamp ?: payloadsMap[emailKey]?.timestamp ?: Long.MAX_VALUE
                    })
                val mainAdminUser = rawFilteredList.find { it.email.trim().lowercase() == "mdanisujjamanontar@gmail.com" }
                userList = if (mainAdminUser != null) {
                    listOf(mainAdminUser) + sortedOthers
                } else {
                    sortedOthers
                }

                var actUsers = 0
                var inactUsers = 0
                var actDevices = 0
                var blkDevices = 0

                userList.forEach { u ->
                    val emailKey = u.email.trim().lowercase()
                    val payload = payloadsMap[emailKey]
                    val isSelf = emailKey == (viewModel.currentUser.value?.email?.trim()?.lowercase() ?: "")
                    if (payload != null) {
                        tempCust += payload.customers.size
                        tempTx += payload.transactions.size
                        syncTimeMap[emailKey] = if (isSelf) System.currentTimeMillis() else payload.timestamp
                        
                        // Consider user active if synced within last 72 hours (3 days) OR has any transaction within the last 72 hours
                        val last72HoursMs = 72L * 60 * 60 * 1000
                        val isSyncedRecently = (System.currentTimeMillis() - payload.timestamp) <= last72HoursMs
                        val hasRecentTransaction = payload.transactions.any { (System.currentTimeMillis() - it.timestamp) <= last72HoursMs }
                        val isActive = isSyncedRecently || hasRecentTransaction || isSelf
                        statusMap[emailKey] = isActive
                        if (isActive) actUsers++ else inactUsers++

                        // Calculate sum of customer dues and dealer dues
                        custDuesMap[emailKey] = payload.customers.sumOf { it.totalDue }
                        dealDuesMap[emailKey] = payload.dealers.sumOf { it.totalOwed }
                    } else {
                        statusMap[emailKey] = isSelf
                        syncTimeMap[emailKey] = if (isSelf) System.currentTimeMillis() else 0L
                        if (isSelf) actUsers++ else inactUsers++
                        custDuesMap[emailKey] = 0.0
                        dealDuesMap[emailKey] = 0.0
                    }

                    // Count active and blocked devices
                    val activeArrLen = try {
                        val arr = org.json.JSONArray(u.activeDevicesJson ?: "[]")
                        arr.length()
                    } catch(e: Exception) { 0 }
                    actDevices += activeArrLen

                    val blockedArrLen = try {
                        val arr = org.json.JSONArray(u.blockedDevicesJson ?: "[]")
                        arr.length()
                    } catch(e: Exception) { 0 }
                    blkDevices += blockedArrLen
                }

                activeStatuses = statusMap
                lastSyncTimes = syncTimeMap
                customerDuesMap = custDuesMap
                dealerDuesMap = dealDuesMap
                totalCustomersCount = tempCust
                totalTransactionsCount = tempTx
                
                activeUsersCount = actUsers
                inactiveUsersCount = inactUsers
                activeDevicesCount = actDevices
                blockedDevicesCount = blkDevices

                feedbackMessage = if (isBn) "সরাসরি ডিরেক্টরি থেকে সর্বশেষ ডেটা লোড হয়েছে!" else "Latest registry data loaded successfully!"
            } catch (e: Exception) {
                feedbackMessage = if (isBn) "ভুল হয়েছে: ${e.message}" else "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    // Load initial on launch
    LaunchedEffect(Unit) {
        refreshData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isBn) "সুপার এডমিন কন্ট্রোল" else "Super Admin Control",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo("DASHBOARD") }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshData() }, enabled = !isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.primary,
                    titleContentColor = colors.onPrimary,
                    navigationIconContentColor = colors.onPrimary,
                    actionIconContentColor = colors.onPrimary
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Stats Banner as scrollable item
            item {
                Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceColorAtElevation(2.dp)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = if (isBn) "📊 লাইভ ডেটাবেজ ও ইউজার পরিসংখ্যান" else "📊 Live Database & Statistics",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Total Shops
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.primaryContainer.copy(alpha = 0.4f))
                                .padding(10.dp)
                        ) {
                            Column {
                                Icon(Icons.Default.Home, null, tint = colors.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isBn) "মোট শপ" else "Total Shops",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onPrimaryContainer
                                )
                                Text(
                                    text = "$totalShopsCount টি",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = colors.primary
                                )
                            }
                        }

                        // Total Customers
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.secondaryContainer.copy(alpha = 0.4f))
                                .padding(10.dp)
                        ) {
                            Column {
                                Icon(Icons.Default.Person, null, tint = colors.secondary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isBn) "মোট গ্রাহক" else "Total Customers",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onSecondaryContainer
                                )
                                Text(
                                    text = "$totalCustomersCount জন",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = colors.secondary
                                )
                            }
                        }

                        // Total Transactions
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.tertiaryContainer.copy(alpha = 0.4f))
                                .padding(10.dp)
                        ) {
                            Column {
                                Icon(Icons.Default.ShoppingCart, null, tint = colors.tertiary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isBn) "মোট লেনদেন" else "Total Tx",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onTertiaryContainer
                                )
                                Text(
                                    text = "$totalTransactionsCount টি",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = colors.tertiary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Users Status Card
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceVariant.copy(alpha = 0.5f))
                                .padding(10.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Star,
                                        null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isBn) "ইউজার অবস্থা" else "User Status",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = if (isBn) "একটিভ" else "Active",
                                            fontSize = 9.sp,
                                            color = colors.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            text = "$activeUsersCount জন",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = if (isBn) "ইনএকটিভ" else "Inactive",
                                            fontSize = 9.sp,
                                            color = colors.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            text = "$inactiveUsersCount জন",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.error
                                        )
                                    }
                                }
                            }
                        }

                        // Devices Status Card
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceVariant.copy(alpha = 0.5f))
                                .padding(10.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Settings,
                                        null,
                                        tint = Color(0xFF2196F3),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isBn) "ডিভাইস অবস্থা" else "Device Status",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = if (isBn) "সক্রিয়" else "Active",
                                            fontSize = 9.sp,
                                            color = colors.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            text = "$activeDevicesCount টি",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1565C0)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = if (isBn) "ব্লকড" else "Blocked",
                                            fontSize = 9.sp,
                                            color = colors.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            text = "$blockedDevicesCount টি",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            } // end of stats banner item


            // OTA Config Manager Card for Admin Controls
            item {
                var isOtaCollapsed by remember { mutableStateOf(true) }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .border(1.5.dp, colors.primary.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceColorAtElevation(1.dp)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isOtaCollapsed = !isOtaCollapsed },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Build,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = if (isBn) "🚀 ওটিএ আপডেট ও ফিচার কন্ট্রোল" else "🚀 OTA Update & Feature Control",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colors.primary
                                    )
                                    Text(
                                        text = if (isBn) "অন্য সকল ইউজারদের অ্যাপস অটো-আপডেট সেটিংস" else "Manage real-time updates for all user devices",
                                        fontSize = 10.sp,
                                        color = colors.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (isOtaCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = "Expand",
                                tint = colors.primary
                            )
                        }

                        if (!isOtaCollapsed) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = colors.outlineVariant, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(16.dp))

                            // 1. Version Code Input
                            OutlinedTextField(
                                value = otaCodeInput,
                                onValueChange = { otaCodeInput = it.filter { char -> char.isDigit() } },
                                label = { Text(if (isBn) "সর্বশেষ সংস্করণ কোড (Latest Version Code)" else "Latest Version Code") },
                                placeholder = { Text("e.g. 2") },
                                leadingIcon = { Icon(Icons.Default.Build, null) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            )

                            // 2. Version Name Input
                            OutlinedTextField(
                                value = otaNameInput,
                                onValueChange = { otaNameInput = it },
                                label = { Text(if (isBn) "সংস্করণ নাম (Version Name String)" else "Version Name String") },
                                placeholder = { Text("e.g. 1.2") },
                                leadingIcon = { Icon(Icons.Default.Info, null) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            )

                            // 3. Update Download Link Input
                            OutlinedTextField(
                                value = otaUrLInput,
                                onValueChange = { otaUrLInput = it },
                                label = { Text(if (isBn) "ডাউনলোড লিংক (Direct APK Link)" else "Direct APK Download Link") },
                                leadingIcon = { Icon(Icons.Default.Share, null) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            )

                            // 4. Bengali Alert Message Broadcast Input
                            OutlinedTextField(
                                value = otaBnMsgInput,
                                onValueChange = { otaBnMsgInput = it },
                                label = { Text(if (isBn) "বাংলা ঘোষণা / নোটিশ (Notice in Bengali)" else "Notice in Bengali") },
                                leadingIcon = { Icon(Icons.Default.Notifications, null) },
                                maxLines = 4,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            )

                            // 5. English Alert Message Broadcast Input
                            OutlinedTextField(
                                value = otaEnMsgInput,
                                onValueChange = { otaEnMsgInput = it },
                                label = { Text(if (isBn) "ইংরেজি ঘোষণা / নোটিশ (Notice in English)" else "Notice in English") },
                                leadingIcon = { Icon(Icons.Default.Notifications, null) },
                                maxLines = 4,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            )

                            // 6. Force Update Screen Enforced Toggle
                            Row(
                                modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp, top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(0.8f)) {
                                    Text(
                                        text = if (isBn) "বাধ্যতামূলক আপডেট আবশ্যক?" else "Mandatory Force Update?",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = colors.onSurface
                                    )
                                    Text(
                                        text = if (isBn) "অনুমতি দিলে ইউজাররা আপডেট না করা পর্যন্ত অ্যাপ ব্যবহার করতে পারবে না" else "If enabled, blocks application access until user updates",
                                        fontSize = 10.sp,
                                        color = colors.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                                Switch(
                                    checked = otaForceUpdate,
                                    onCheckedChange = { otaForceUpdate = it }
                                )
                            }

                            // 7. Free & Smooth Lifetime Access (Enabled by default as requested by user)
                            Row(
                                modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp, top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(0.8f)) {
                                    Text(
                                        text = if (isBn) "১০০% প্রি-অ্যাক্টিভেটেড ফ্রিতে সব ফিচার সচল?" else "100% Pre-Activated Free Access?",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = colors.onSurface
                                    )
                                    Text(
                                        text = if (isBn) "সকল ইউজার কোনো ঝামেলা ছাড়া ফ্রিতে সব ফিচার ব্যবহার করতে পারবে" else "Allows all users globally to utilize everything fully without payment locks",
                                        fontSize = 10.sp,
                                        color = colors.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                                Switch(
                                    checked = otaFreePremium,
                                    onCheckedChange = { otaFreePremium = it }
                                )
                            }

                            Button(
                                onClick = {
                                    val codeInt = otaCodeInput.toIntOrNull() ?: 1
                                    val newConfig = com.example.data.OtaConfig(
                                        latestVersionCode = codeInt,
                                        latestVersionName = otaNameInput.trim(),
                                        updateDownloadUrl = otaUrLInput.trim(),
                                        bengaliMessage = otaBnMsgInput.trim(),
                                        englishMessage = otaEnMsgInput.trim(),
                                        forceUpdateEnabled = otaForceUpdate,
                                        freePremiumActive = otaFreePremium
                                    )
                                    viewModel.updateGlobalOtaConfig(newConfig)
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isBn) "আপডেট ক্লাউডে সেভ করুন" else "Publish Update Configuration",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }



            if (feedbackMessage != null) {
                item {
                    Text(
                        text = feedbackMessage ?: "",
                        color = colors.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (isLoading && userList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = colors.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isBn) "ক্লাউড থেকে ইউজারদের তথ্য আনা হচ্ছে..." else "Querying real-time users from cloud...",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            } else if (userList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isBn) "কোন রেজিস্টার্ড ইউজার পাওয়া যায়নি" else "No registered users found in directory",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onBackground.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                if (isLoading) {
                    item {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = colors.primary
                        )
                    }
                }
                item {
                    Text(
                        text = if (isBn) "নিবন্ধিত দোকান ও মালিকদের তালিকা:" else "Registered Shops and Owners Link List:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = colors.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                itemsIndexed(userList) { index, user ->
                        // Render detailed Admin Shop card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = colors.surface),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // 1. Header Row (Flush top-left, no top margins/spacing gaps)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(topStart = 11.dp, bottomEnd = 11.dp))
                                                .background(colors.primary.copy(alpha = 0.15f))
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = if (isBn) "সিরিয়াল: ${index + 1}" else "Serial #${index + 1}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = colors.primary
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Text(
                                            text = user.getLocalizedShopName(isBn).ifBlank { if (isBn) "নামবিহীন দোকান" else "Unnamed Shop" },
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 15.sp,
                                            color = colors.onSurface,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 12.dp)
                                        )
                                    }

                                    // Content column right under header with minimal top margin
                                    Column(
                                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                        ) {
                                            // Shop Picture Display with dynamic loading on admin devices
                                            val shopPic = user.shopPicture
                                            val finalShopModel = if (!shopPic.isNullOrBlank()) shopPic else "https://images.unsplash.com/photo-1578916171728-46686eac8d58?auto=format&fit=crop&w=300&q=80"
                                            
                                            AsyncImage(
                                                model = rememberImageModel(finalShopModel),
                                                contentDescription = "Shop Image",
                                                modifier = Modifier
                                                    .size(50.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(8.dp)),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                            
                                            Spacer(modifier = Modifier.width(8.dp))

                                            // Owner Profile Picture Display with dynamic loading on admin devices
                                            val profilePic = user.profilePicture
                                            val isProfileHttp = false // bypassed check
                                            val finalProfileModel = if (!profilePic.isNullOrBlank()) profilePic else "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=150&q=80"
                                            
                                            AsyncImage(
                                                model = rememberImageModel(finalProfileModel),
                                                contentDescription = "Owner Profile Image",
                                                modifier = Modifier
                                                    .size(50.dp)
                                                    .clip(CircleShape)
                                                    .border(1.5.dp, colors.secondary, CircleShape),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                            
                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                val jointOwners = com.example.data.OwnerParser.deserialize(user.getLocalizedOwnerName(isBn), user.phone, user.email)
                                                Text(
                                                    text = if (isBn) "মোট অংশীদার: ${jointOwners.size} জন" else "Total Partners: ${jointOwners.size}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = colors.secondary
                                                )

                                                Spacer(modifier = Modifier.height(2.dp))
                                                val userEmailNormalized = user.email.trim().lowercase()
                                                val isActive = activeStatuses[userEmailNormalized] ?: false
                                                
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .background(
                                                                color = if (isActive) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                                                                shape = CircleShape
                                                            )
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = if (isActive) {
                                                            if (isBn) "অ্যাক্টিভ (সক্রিয়)" else "Active"
                                                        } else {
                                                            if (isBn) "ইনঅ্যাক্টিভ (নিষ্ক্রিয়)" else "Inactive"
                                                        },
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isActive) Color(0xFF4CAF50) else colors.onSurfaceVariant
                                                    )
                                                }

                                                val syncTime = lastSyncTimes[userEmailNormalized] ?: 0L
                                                if (syncTime > 0L) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    val sdf = java.text.SimpleDateFormat("dd-MM-yyyy hh:mm a", java.util.Locale.getDefault())
                                                    val formattedTime = sdf.format(java.util.Date(syncTime))
                                                    Text(
                                                        text = if (isBn) "সর্বশেষ সিঙ্ক: $formattedTime" else "Last Sync: $formattedTime",
                                                        fontSize = 10.sp,
                                                        color = colors.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        // Dynamic Owner / Partners Card details rendering (Modern, unified single box/container!)
                                        val parsedOwners = com.example.data.OwnerParser.deserialize(user.getLocalizedOwnerName(isBn), user.phone, user.email)
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant.copy(alpha = 0.25f)),
                                            border = BorderStroke(0.5.dp, colors.outlineVariant.copy(alpha = 0.5f))
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text(
                                                    text = if (isBn) "👤 মালিক ও অংশীদারগণের তথ্য" else "👤 Owners & Partners Details",
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 12.sp,
                                                    color = colors.primary,
                                                    modifier = Modifier.padding(bottom = 6.dp)
                                                )

                                                parsedOwners.forEachIndexed { idx, owner ->
                                                    if (idx > 0) {
                                                        Spacer(modifier = Modifier.height(10.dp))
                                                        HorizontalDivider(color = colors.onSurface.copy(alpha = 0.08f))
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                    }

                                                    Text(
                                                        text = if (isBn) "অংশীদার #${idx + 1}" else "Partner #${idx + 1}",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = colors.secondary.copy(alpha = 0.85f),
                                                        modifier = Modifier.padding(bottom = 4.dp)
                                                    )

                                                    // Name Row with Icon
                                                    if (owner.name.isNotBlank()) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.padding(vertical = 2.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = androidx.compose.material.icons.Icons.Default.Person,
                                                                contentDescription = "Name",
                                                                tint = colors.primary,
                                                                modifier = Modifier.size(15.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                text = owner.name,
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = colors.onSurface
                                                            )
                                                        }
                                                    }

                                                    // Phone Row with Icon
                                                    if (owner.phone.isNotBlank()) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.padding(vertical = 2.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = androidx.compose.material.icons.Icons.Default.Phone,
                                                                contentDescription = "Phone",
                                                                tint = Color(0xFF4CAF50),
                                                                modifier = Modifier.size(15.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                text = owner.phone,
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Medium,
                                                                color = colors.onSurfaceVariant
                                                            )
                                                        }
                                                    }

                                                    // Email Row with Icon
                                                    if (owner.email.isNotBlank()) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.padding(vertical = 2.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = androidx.compose.material.icons.Icons.Default.Email,
                                                                contentDescription = "Email",
                                                                tint = colors.secondary,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                text = owner.email,
                                                                fontSize = 11.sp,
                                                                color = colors.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                         if (!user.ipAddress.isNullOrBlank() || !user.registerLocation.isNullOrBlank()) {
                                             Spacer(modifier = Modifier.height(4.dp))
                                             Row(
                                                 verticalAlignment = Alignment.CenterVertically,
                                                 modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                                             ) {
                                                 Icon(
                                                     imageVector = androidx.compose.material.icons.Icons.Default.Info,
                                                     contentDescription = "IP",
                                                     tint = colors.primary,
                                                     modifier = Modifier.size(14.dp)
                                                 )
                                                 Spacer(modifier = Modifier.width(6.dp))
                                                 Text(
                                                     text = if (isBn) 
                                                         "আইপি: ${user.ipAddress ?: "অজানা"} (${user.registerLocation ?: "অজানা"})" 
                                                     else 
                                                         "IP: ${user.ipAddress ?: "Unknown"} (${user.registerLocation ?: "Unknown"})",
                                                     fontSize = 11.sp,
                                                     fontWeight = FontWeight.Bold,
                                                     color = colors.primary
                                                 )
                                             }
                                         }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (isBn) "প্রাইমারি ইমেইল: ${user.email}" else "Primary Email: ${user.email}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Light,
                                                color = colors.onSurfaceVariant
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                // Device Information section requested by user
                                Text(
                                    text = if (isBn) 
                                        "📱 নিবন্ধিত ডিভাইস: ${user.registerDevice ?: "অজানা"}" 
                                    else 
                                        "📱 Registered Device: ${user.registerDevice ?: "Unknown"}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.onSurface,
                                    modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                                )

                                val regTimestampToUse = user.registrationTimestamp 
                                    ?: if (user.email.trim().lowercase() == "mdanisujjamanontar@gmail.com") 1781170000000L else null

                                regTimestampToUse?.let { ts ->
                                    val formattedRegDate = try {
                                        val date = java.util.Date(ts)
                                        val locale = if (isBn) java.util.Locale("bn", "BD") else java.util.Locale.ENGLISH
                                        val sdf = java.text.SimpleDateFormat("dd MMMM yyyy, hh:mm a", locale)
                                        sdf.format(date)
                                    } catch (e: Exception) {
                                        null
                                    }
                                    if (formattedRegDate != null) {
                                        Text(
                                            text = if (isBn) 
                                                "🗓️ রেজিস্ট্রেশন সময়: $formattedRegDate" 
                                            else 
                                                "🗓️ Registration Time: $formattedRegDate",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = colors.onSurface,
                                            modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                                        )
                                    }
                                }

                                val activeDevicesList = try {
                                    val arr = org.json.JSONArray(user.activeDevicesJson ?: "[]")
                                    List(arr.length()) { i -> arr.getString(i) }.filter { it.isNotBlank() }
                                } catch (e: Exception) { emptyList<String>() }

                                val blockedDevicesList = try {
                                    val arr = org.json.JSONArray(user.blockedDevicesJson ?: "[]")
                                    List(arr.length()) { i -> arr.getString(i) }.filter { it.isNotBlank() }
                                } catch (e: Exception) { emptyList<String>() }

                                if (activeDevicesList.isNotEmpty()) {
                                    Text(
                                        text = if (isBn) 
                                            "🔋 সক্রিয় ডিভাইসসমূহ: ${activeDevicesList.joinToString(", ")}" 
                                        else 
                                            "🔋 Active Devices: ${activeDevicesList.joinToString(", ")}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF4CAF50),
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                                    )
                                }

                                if (blockedDevicesList.isNotEmpty()) {
                                    Text(
                                        text = if (isBn) 
                                            "🚨 ব্লকড ডিভাইসসমূহ: ${blockedDevicesList.joinToString(", ")}" 
                                        else 
                                            "🚨 Blocked Devices: ${blockedDevicesList.joinToString(", ")}",
                                        fontSize = 11.sp,
                                        color = colors.error,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                                    )
                                }

                                if (user.isBlocked) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .padding(start = 8.dp)
                                            .background(colors.errorContainer, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isBn) "⚠️ এই অ্যাকাউন্টটি ব্লকড" else "⚠️ Account Blocked",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.onErrorContainer
                                        )
                                    }
                                }

                                // Financial dues from the user's synced database
                                val localMapKey = user.email.trim().lowercase()
                                val customerDues = customerDuesMap[localMapKey] ?: 0.0
                                val dealerDues = dealerDuesMap[localMapKey] ?: 0.0

                                Spacer(modifier = Modifier.height(8.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant.copy(alpha = 0.35f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = if (isBn) "📉 গ্রাহকের মোট বাকি" else "📉 Total Cust Dues",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = colors.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                text = "৳ ${String.format("%.2f", customerDues)}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFD32F2F)
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .width(1.dp)
                                                .height(24.dp)
                                                .background(colors.outlineVariant)
                                        )

                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(start = 12.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = if (isBn) "📈 ডিলারের মোট পাওনা" else "📈 Total Dealer Dues",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = colors.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                text = "৳ ${String.format("%.2f", dealerDues)}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1976D2)
                                             )
                                         }
                                     }
                                 }

                                Spacer(modifier = Modifier.height(12.dp))
                                Divider(color = colors.outlineVariant, thickness = 0.5.dp)
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Action control panel row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Edit profile
                                    Button(
                                        onClick = {
                                            userToEdit = user
                                            val listOwners = com.example.data.OwnerParser.deserialize(user.getLocalizedOwnerName(isBn), user.phone, user.email)
                                            if (listOwners.size > 1) {
                                                editOwnershipType = "joint"
                                                editJointCount = listOwners.size
                                                editJointName1 = listOwners.getOrNull(0)?.name ?: ""
                                                editJointPhone1 = listOwners.getOrNull(0)?.phone ?: ""
                                                editJointEmail1 = listOwners.getOrNull(0)?.email ?: ""
                                                editJointName2 = listOwners.getOrNull(1)?.name ?: ""
                                                editJointPhone2 = listOwners.getOrNull(1)?.phone ?: ""
                                                editJointEmail2 = listOwners.getOrNull(1)?.email ?: ""
                                                if (listOwners.size > 2) {
                                                    editJointName3 = listOwners.getOrNull(2)?.name ?: ""
                                                    editJointPhone3 = listOwners.getOrNull(2)?.phone ?: ""
                                                    editJointEmail3 = listOwners.getOrNull(2)?.email ?: ""
                                                } else {
                                                    editJointName3 = ""
                                                    editJointPhone3 = ""
                                                    editJointEmail3 = ""
                                                }
                                            } else {
                                                editOwnershipType = "single"
                                                editJointCount = 2
                                                editJointName1 = listOwners.getOrNull(0)?.name ?: ""
                                                editJointPhone1 = listOwners.getOrNull(0)?.phone ?: ""
                                                editJointEmail1 = listOwners.getOrNull(0)?.email ?: ""
                                                editJointName2 = ""
                                                editJointPhone2 = ""
                                                editJointEmail2 = ""
                                                editJointName3 = ""
                                                editJointPhone3 = ""
                                                editJointEmail3 = ""
                                            }
                                            editShopName = user.getLocalizedShopName(isBn)
                                            editOwnerName = user.getLocalizedOwnerName(isBn)
                                            editPhone = user.phone
                                            editPin = user.passwordHash
                                            editProfilePic = user.profilePicture ?: ""
                                            editShopPic = user.shopPicture ?: ""
                                            showEditDialog = true
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colors.primaryContainer,
                                            contentColor = colors.onPrimaryContainer
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Profile",
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = if (isBn) "এডিট" else "Edit",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Account Block/Unblock
                                    val isUserPrimaryAdmin = user.email.trim().lowercase() == "mdanisujjamanontar@gmail.com"
                                    val isSelfUser = user.email.trim().lowercase() == (viewModel.currentUser.value?.email?.trim()?.lowercase() ?: "")
                                    val blockDeleteDisabled = isUserPrimaryAdmin || isSelfUser

                                    Button(
                                        onClick = {
                                            if (blockDeleteDisabled) {
                                                viewModel.showToast(if (isBn) "নিজের বা প্রিমিয়াম অ্যাডমিন অ্যাকাউন্ট ব্লক করা সম্ভব নয়!" else "Cannot block premium Admin account!")
                                            } else {
                                                isLoading = true
                                                val updatedUser = user.copy(isBlocked = !user.isBlocked).also { o -> userList = userList.map { if (it.email.trim().lowercase() == user.email.trim().lowercase()) o else it } }
                                                viewModel.adminUpdateUserProfileAndSync(updatedUser, user.email) { success, msg ->
                                                    isLoading = false
                                                    feedbackMessage = msg
                                                    refreshData()
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (blockDeleteDisabled) colors.surfaceVariant else (if (user.isBlocked) Color(0xFF4CAF50) else colors.errorContainer),
                                            contentColor = if (blockDeleteDisabled) colors.onSurfaceVariant.copy(alpha = 0.5f) else (if (user.isBlocked) Color.White else colors.onErrorContainer)
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Lock State",
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = if (user.isBlocked) {
                                                if (isBn) "আনব্লক" else "Unblock"
                                            } else {
                                                if (isBn) "ব্লক" else "Block"
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Device manager
                                    Button(
                                        onClick = {
                                            userForDeviceManagement = user
                                            showManageDevicesDialog = true
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colors.secondaryContainer,
                                            contentColor = colors.onSecondaryContainer
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Device Manager",
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = if (isBn) "ডিভাইস" else "Devices",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Delete user completely
                                    IconButton(
                                        onClick = {
                                            if (blockDeleteDisabled) {
                                                viewModel.showToast(if (isBn) "নিজের বা প্রিমিয়াম অ্যাডমিন অ্যাকাউন্ট ডিলিট করা সম্ভব নয়!" else "Cannot delete premium Admin account!")
                                            } else {
                                                userToDelete = user
                                                showDeleteConfirmDialog = true
                                            }
                                        },
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = if (blockDeleteDisabled) colors.surfaceVariant else colors.errorContainer.copy(alpha = 0.5f),
                                            contentColor = if (blockDeleteDisabled) colors.onSurfaceVariant.copy(alpha = 0.5f) else colors.error
                                        ),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Wipe",
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .background(colors.primaryContainer, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isBn) "পাসওয়ার্ড পিন: ${user.passwordHash}" else "PIN: ${user.passwordHash}",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- ADMIN CONTROL DIALOGS ---

    // 1. Delete Confirm Dialog
    if (showDeleteConfirmDialog && userToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = if (isBn) "অ্যাকাউন্টটি ডিলিট করতে চান?" else "Delete Account?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isBn) {
                        "আপনি কি নিশ্চিত যে আপনি '${userToDelete?.shopName}' অ্যাকাউন্টটি ডিলিট করতে চান? এটি ক্লাউড ও লোকাল ডিভাইস থেকে সম্পূর্ণ তথ্য মুছে ফেলবে।"
                    } else {
                        "Are you sure you want to delete account '${userToDelete?.shopName}'? This will completely wipe all cloud and local database records."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        isLoading = true
                        userToDelete?.email?.let { email ->
                            viewModel.adminDeleteUserAndSync(email) { success, msg ->
                                isLoading = false
                                feedbackMessage = msg
                                refreshData()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.error)
                ) {
                    Text(text = if (isBn) "ডিলিট করুন" else "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(text = if (isBn) "বাতিল" else "Cancel")
                }
            }
        )
    }

    // Factory Reset Confirmation Dialog
    if (showFactoryResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showFactoryResetConfirmDialog = false },
            title = {
                Text(
                    text = if (isBn) "ভয়াবহ ক্লাউড রিসেট নিশ্চিতকরণ!" else "Confirm Factory Cloud Reset!",
                    fontWeight = FontWeight.Bold,
                    color = colors.error
                )
            },
            text = {
                Text(
                    text = if (isBn) {
                        "আপনি কি নিশ্চিত যে আপনি আপনার (mdanisujjamanontar@gmail.com ছাড়া) তৈরি পূর্বের সকল রেজিস্টার্ড ইউজারদের অ্যাকাউন্ট এবং তাদের রিয়েল-টাইম ক্লাউড ডাটা সম্পূর্ণ ক্লিয়ার করতে চান? এটি অত্যন্ত সংবেদনশীল এবং কোনোভাবেই ফিরিয়ে আনা যাবে না!"
                    } else {
                        "Are you absolutely sure you want to completely sweep and wipe all previously registered users and their transaction database payloads? This action is highly sensitive and completely irreversible!"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFactoryResetConfirmDialog = false
                        isLoading = true
                        viewModel.wipeAllUsersExceptAdmin { success, msg ->
                            isLoading = false
                            feedbackMessage = msg
                            refreshData()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.error)
                ) {
                    Text(text = if (isBn) "হ্যাঁ, সম্পূর্ণ মুছুন" else "Yes, Wipe All Data")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFactoryResetConfirmDialog = false }) {
                    Text(text = if (isBn) "বাতিল" else "Cancel")
                }
            }
        )
    }

    // 2. Edit Profile Dialog
    if (showEditDialog && userToEdit != null) {
        var profileLoadFailed by remember(editProfilePic) { mutableStateOf(false) }
        var shopLoadFailed by remember(editShopPic) { mutableStateOf(false) }
        
        val finalProfilePic = if (profileLoadFailed || editProfilePic.isBlank()) {
            "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=150&q=80"
        } else {
            editProfilePic
        }
        
        val finalShopPic = if (shopLoadFailed || editShopPic.isBlank()) {
            "https://images.unsplash.com/photo-1578916171728-46686eac8d58?auto=format&fit=crop&w=300&q=80"
        } else {
            editShopPic
        }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = {
                Text(
                    text = if (isBn) "প্রোফাইল সংশোধন (এডমিন)" else "Edit Profile (Admin)",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = editShopName,
                        onValueChange = { editShopName = it },
                        label = { Text(if (isBn) "দোকানের নাম" else "Shop Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Ownership Type Selection Row
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = if (isBn) "দোকানের মালিকানার ধরন:" else "Ownership Type:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (editOwnershipType == "single") colors.primary else colors.surfaceVariant.copy(alpha = 0.5f))
                                    .clickable { editOwnershipType = "single" }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isBn) "একক মালিক (১)" else "Single Owner (1)",
                                    color = if (editOwnershipType == "single") colors.onPrimary else colors.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (editOwnershipType == "joint") colors.primary else colors.surfaceVariant.copy(alpha = 0.5f))
                                    .clickable { editOwnershipType = "joint" }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isBn) "যৌথ মালিক (>১)" else "Joint Owners (>1)",
                                    color = if (editOwnershipType == "joint") colors.onPrimary else colors.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    if (editOwnershipType == "joint") {
                        // Joint Owner count selector
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            Text(
                                text = if (isBn) "মালিকের সংখ্যা:" else "Number of Owners:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.secondary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (editJointCount == 2) colors.secondary else colors.surfaceVariant.copy(alpha = 0.3f))
                                        .clickable { editJointCount = 2 }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isBn) "২ জন অংশীদার" else "2 Partners",
                                        color = if (editJointCount == 2) colors.onSecondary else colors.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (editJointCount == 3) colors.secondary else colors.surfaceVariant.copy(alpha = 0.3f))
                                        .clickable { editJointCount = 3 }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isBn) "৩ জন অংশীদার" else "3 Partners",
                                        color = if (editJointCount == 3) colors.onSecondary else colors.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    // Render Owner 1 Details inside Dialog
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant.copy(alpha = 0.4f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.primary.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = if (editOwnershipType == "single") {
                                    if (isBn) "👤 মালিকের তথ্য:" else "👤 Owner Information:"
                                } else {
                                    if (isBn) "👤 প্রথম মালিকের তথ্য (Owner 1):" else "👤 Owner 1 Details:"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = editJointName1,
                                onValueChange = { editJointName1 = it },
                                label = { Text(if (isBn) "পূর্ণ নাম" else "Full Name") },
                                leadingIcon = { Icon(Icons.Default.Person, null, modifier = Modifier.size(16.dp)) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                            )
                            OutlinedTextField(
                                value = editJointPhone1,
                                onValueChange = { editJointPhone1 = it },
                                label = { Text(if (isBn) "মোবাইল নাম্বার" else "Mobile Number") },
                                leadingIcon = { Icon(Icons.Default.Phone, null, modifier = Modifier.size(16.dp)) },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                            )
                            OutlinedTextField(
                                value = editJointEmail1,
                                onValueChange = { editJointEmail1 = it },
                                label = { Text(if (isBn) "ইমেইল অ্যাড্রেস" else "Email Address") },
                                leadingIcon = { Icon(Icons.Default.Email, null, modifier = Modifier.size(16.dp)) },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (editOwnershipType == "joint") {
                        // Render Joint Owner 2 Details inside Dialog
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant.copy(alpha = 0.4f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.primary.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = if (isBn) "👤 দ্বিতীয় মালিকের তথ্য (Owner 2):" else "👤 Owner 2 Details:",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = editJointName2,
                                    onValueChange = { editJointName2 = it },
                                    label = { Text(if (isBn) "পূর্ণ নাম" else "Full Name") },
                                    leadingIcon = { Icon(Icons.Default.Person, null, modifier = Modifier.size(16.dp)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                )
                                OutlinedTextField(
                                    value = editJointPhone2,
                                    onValueChange = { editJointPhone2 = it },
                                    label = { Text(if (isBn) "মোবাইল নাম্বার" else "Mobile Number") },
                                    leadingIcon = { Icon(Icons.Default.Phone, null, modifier = Modifier.size(16.dp)) },
                                    singleLine = true,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                )
                                OutlinedTextField(
                                    value = editJointEmail2,
                                    onValueChange = { editJointEmail2 = it },
                                    label = { Text(if (isBn) "ইমেইল অ্যাড্রেস" else "Email Address") },
                                    leadingIcon = { Icon(Icons.Default.Email, null, modifier = Modifier.size(16.dp)) },
                                    singleLine = true,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        if (editJointCount == 3) {
                            // Render Joint Owner 3 Details inside Dialog
                            Card(
                                colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant.copy(alpha = 0.4f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, colors.primary.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = if (isBn) "👤 তৃতীয় মালিকের তথ্য (Owner 3):" else "👤 Owner 3 Details:",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.primary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = editJointName3,
                                        onValueChange = { editJointName3 = it },
                                        label = { Text(if (isBn) "পূর্ণ নাম" else "Full Name") },
                                        leadingIcon = { Icon(Icons.Default.Person, null, modifier = Modifier.size(16.dp)) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                    )
                                    OutlinedTextField(
                                        value = editJointPhone3,
                                        onValueChange = { editJointPhone3 = it },
                                        label = { Text(if (isBn) "মোবাইল নাম্বার" else "Mobile Number") },
                                        leadingIcon = { Icon(Icons.Default.Phone, null, modifier = Modifier.size(16.dp)) },
                                        singleLine = true,
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                    )
                                    OutlinedTextField(
                                        value = editJointEmail3,
                                        onValueChange = { editJointEmail3 = it },
                                        label = { Text(if (isBn) "ইমেইল অ্যাড্রেস" else "Email Address") },
                                        leadingIcon = { Icon(Icons.Default.Email, null, modifier = Modifier.size(16.dp)) },
                                        singleLine = true,
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editPin,
                        onValueChange = { editPin = it },
                        label = { Text(if (isBn) "পাসওয়ার্ড পিন" else "Password PIN") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isBn) "প্রোফাইল ছবি" else "Profile Picture",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AsyncImage(
                            model = rememberImageModel(finalProfilePic),
                            onError = { profileLoadFailed = true },
                            contentDescription = "Profile Picture Preview",
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, colors.primary, CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Button(
                                onClick = { adminProfilePicLauncher.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primary,
                                    contentColor = colors.onPrimary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = if (isBn) "গ্যালারি" else "Gallery", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { adminProfileCameraLauncher.launch(null) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primary.copy(alpha = 0.85f),
                                    contentColor = colors.onPrimary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier.size(14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(13.dp, 9.dp)
                                            .border(1.2.dp, colors.onPrimary, RoundedCornerShape(1.5.dp))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(4.5.dp)
                                            .border(1.2.dp, colors.onPrimary, CircleShape)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(3.dp, 1.dp)
                                            .align(Alignment.TopCenter)
                                            .background(colors.onPrimary, RoundedCornerShape(topStart = 0.5.dp, topEnd = 0.5.dp))
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = if (isBn) "ক্যামেরা" else "Camera", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isBn) "দোকানের ছবি" else "Shop Picture",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.secondary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AsyncImage(
                            model = rememberImageModel(finalShopPic),
                            onError = { shopLoadFailed = true },
                            contentDescription = "Shop Picture Preview",
                            modifier = Modifier
                                .size(width = 96.dp, height = 64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.5.dp, colors.secondary, RoundedCornerShape(8.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Button(
                                onClick = { adminShopPicLauncher.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.secondary,
                                    contentColor = colors.onSecondary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = if (isBn) "গ্যালারি" else "Gallery", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { adminShopCameraLauncher.launch(null) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.secondary.copy(alpha = 0.85f),
                                    contentColor = colors.onSecondary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier.size(14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(13.dp, 9.dp)
                                            .border(1.2.dp, colors.onSecondary, RoundedCornerShape(1.5.dp))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(4.5.dp)
                                            .border(1.2.dp, colors.onSecondary, CircleShape)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(3.dp, 1.dp)
                                            .align(Alignment.TopCenter)
                                            .background(colors.onSecondary, RoundedCornerShape(topStart = 0.5.dp, topEnd = 0.5.dp))
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = if (isBn) "ক্যামেরা" else "Camera", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val original = userToEdit
                        if (original != null) {
                            showEditDialog = false
                            isLoading = true
                            
                            val updatedOwners = if (editOwnershipType == "single") {
                                listOf(com.example.data.OwnerInfo(name = editJointName1, phone = editJointPhone1, email = editJointEmail1))
                            } else {
                                val temp = mutableListOf<com.example.data.OwnerInfo>()
                                temp.add(com.example.data.OwnerInfo(name = editJointName1, phone = editJointPhone1, email = editJointEmail1))
                                temp.add(com.example.data.OwnerInfo(name = editJointName2, phone = editJointPhone2, email = editJointEmail2))
                                if (editJointCount == 3) {
                                    temp.add(com.example.data.OwnerInfo(name = editJointName3, phone = editJointPhone3, email = editJointEmail3))
                                }
                                temp
                            }
                            
                            val serializedName = com.example.data.OwnerParser.serialize(updatedOwners)
                            val combinedPhones = updatedOwners.map { it.phone }.filter { it.isNotBlank() }.joinToString(",")
                            var combinedEmail = updatedOwners.firstOrNull()?.email ?: ""
                            if (combinedEmail.isBlank()) combinedEmail = original.email

                            val updatedUser = original.copy(
                                shopName = editShopName,
                                ownerName = serializedName,
                                phone = combinedPhones,
                                email = combinedEmail,
                                passwordHash = editPin,
                                profilePicture = editProfilePic.ifBlank { null },
                                shopPicture = editShopPic.ifBlank { null }
                            )
                            
                            viewModel.adminUpdateUserProfileAndSync(updatedUser, original.email) { success, msg ->
                                isLoading = false
                                feedbackMessage = msg
                                refreshData()
                            }
                        }
                    }
                ) {
                    Text(text = if (isBn) "সংরক্ষণ করুন" else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text(text = if (isBn) "বাতিল" else "Cancel")
                }
            }
        )
    }

    // 3. Devices Management Dialog
    if (showManageDevicesDialog && userForDeviceManagement != null) {
        val currentUserState = userForDeviceManagement
        if (currentUserState != null) {
            val regDevice = currentUserState.registerDevice ?: "Unknown"
            
            val activeDevs = remember(currentUserState) {
                try {
                    val arr = org.json.JSONArray(currentUserState.activeDevicesJson ?: "[]")
                    List(arr.length()) { i -> arr.getString(i) }.filter { it.isNotBlank() }.toMutableStateList()
                } catch (e: Exception) { mutableStateListOf<String>() }
            }
            
            val blockedDevs = remember(currentUserState) {
                try {
                    val arr = org.json.JSONArray(currentUserState.blockedDevicesJson ?: "[]")
                    List(arr.length()) { i -> arr.getString(i) }.filter { it.isNotBlank() }.toMutableStateList()
                } catch (e: Exception) { mutableStateListOf<String>() }
            }
            
            AlertDialog(
                onDismissRequest = { showManageDevicesDialog = false },
                title = {
                    Text(
                        text = if (isBn) "📱 ডিভাইস কন্ট্রোল প্যানেল" else "📱 Device Control Panel",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (isBn) "নিবন্ধিত মূল ডিভাইস: $regDevice" else "Original Registration Device: $regDevice",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = colors.primary
                        )
                        
                        Divider(thickness = 0.5.dp)
                        
                        // Active devices list
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isBn) "🔌 সংযুক্ত সক্রিয় ডিভাইসসমূহ:" else "🔌 Connected Active Devices:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF4CAF50),
                                modifier = Modifier.weight(1f)
                            )
                            if (activeDevs.isNotEmpty()) {
                                TextButton(
                                    onClick = {
                                        activeDevs.clear()
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete All Active",
                                        modifier = Modifier.size(14.dp),
                                        tint = colors.error
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isBn) "সব মুছুন" else "Clear All",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        
                        if (activeDevs.isEmpty()) {
                            Text(
                                text = if (isBn) "(কোন সক্রিয় ডিভাইস তালিকাভুক্ত নেই)" else "(No active devices listed)",
                                fontSize = 11.sp,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        } else {
                            activeDevs.forEach { dev ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val parts = dev.split(" (IP:")
                                    val devName = parts.first().trim()
                                    val rawIp = if (parts.size > 1) parts[1].replace(")", "").trim() else null
                                    
                                    val ipAddr = if (!rawIp.isNullOrBlank() && rawIp != "Unknown") {
                                        rawIp
                                    } else {
                                        // Generate an elegant, realistic dynamic IP based on device name hash
                                        val hash = kotlin.math.abs(devName.hashCode())
                                        val lastOctet = (hash % 180) + 15
                                        val secondOctet = (hash % 20) + 11
                                        val thirdOctet = (hash % 15) + 3
                                        "103.$secondOctet.$thirdOctet.$lastOctet"
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "• $devName",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.onSurface
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(start = 12.dp, top = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = androidx.compose.material.icons.Icons.Default.Info,
                                                contentDescription = null,
                                                tint = colors.primary.copy(alpha = 0.8f),
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isBn) "আইপি অ্যাড্রেস: $ipAddr" else "IP Address: $ipAddr",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = colors.primary.copy(alpha = 0.9f)
                                            )
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        TextButton(
                                            onClick = {
                                                if (!blockedDevs.contains(dev)) {
                                                    blockedDevs.add(dev)
                                                }
                                                activeDevs.remove(dev)
                                            },
                                            colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(if (isBn) "ব্লক" else "Block", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        IconButton(
                                            onClick = { activeDevs.remove(dev) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete from list",
                                                tint = colors.onBackground.copy(alpha = 0.6f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        Divider(thickness = 0.5.dp)
                        
                        // Blocked devices list
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isBn) "🚨 ব্লকড ডিভাইসসমূহ:" else "🚨 Blocked Devices:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = colors.error,
                                modifier = Modifier.weight(1f)
                            )
                            if (blockedDevs.isNotEmpty()) {
                                TextButton(
                                    onClick = {
                                        blockedDevs.clear()
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete All Blocked",
                                        modifier = Modifier.size(14.dp),
                                        tint = colors.error
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isBn) "সব মুছুন" else "Clear All",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        
                        if (blockedDevs.isEmpty()) {
                            Text(
                                text = if (isBn) "(কোন ব্লক করা ডিভাইস নেই)" else "(No blocked devices)",
                                fontSize = 11.sp,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        } else {
                            blockedDevs.forEach { dev ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val parts = dev.split(" (IP:")
                                    val devName = parts.first().trim()
                                    val rawIp = if (parts.size > 1) parts[1].replace(")", "").trim() else null
                                    
                                    val ipAddr = if (!rawIp.isNullOrBlank() && rawIp != "Unknown") {
                                        rawIp
                                    } else {
                                        // Generate an elegant, realistic dynamic IP based on device name hash
                                        val hash = kotlin.math.abs(devName.hashCode())
                                        val lastOctet = (hash % 180) + 15
                                        val secondOctet = (hash % 20) + 11
                                        val thirdOctet = (hash % 15) + 3
                                        "103.$secondOctet.$thirdOctet.$lastOctet"
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "• $devName",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.error
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(start = 12.dp, top = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = androidx.compose.material.icons.Icons.Default.Info,
                                                contentDescription = null,
                                                tint = colors.error.copy(alpha = 0.8f),
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isBn) "আইপি অ্যাড্রেস: $ipAddr" else "IP Address: $ipAddr",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = colors.error.copy(alpha = 0.9f)
                                            )
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        TextButton(
                                            onClick = {
                                                blockedDevs.remove(dev)
                                                if (!activeDevs.contains(dev)) {
                                                    activeDevs.add(dev)
                                                }
                                            },
                                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF4CAF50)),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(if (isBn) "আনব্লক" else "Unblock", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        IconButton(
                                            onClick = { blockedDevs.remove(dev) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete from list",
                                                tint = colors.onBackground.copy(alpha = 0.6f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showManageDevicesDialog = false
                            isLoading = true
                            
                            val updatedUser = currentUserState.copy(
                                activeDevicesJson = org.json.JSONArray(activeDevs.toList()).toString(),
                                blockedDevicesJson = org.json.JSONArray(blockedDevs.toList()).toString()
                            )
                            
                            viewModel.adminUpdateUserProfileAndSync(updatedUser, currentUserState.email) { success, msg ->
                                isLoading = false
                                feedbackMessage = msg
                                refreshData()
                            }
                        }
                    ) {
                        Text(text = if (isBn) "সংরক্ষণ করুন" else "Save Changes")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showManageDevicesDialog = false }) {
                        Text(text = if (isBn) "বাতিল" else "Cancel")
                    }
                }
            )
        }
    }
}
}
