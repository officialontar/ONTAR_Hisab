package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showFactoryResetConfirmDialog by remember { mutableStateOf(false) }
    var userToDelete by remember { mutableStateOf<User?>(null) }

    var showManageDevicesDialog by remember { mutableStateOf(false) }
    var userForDeviceManagement by remember { mutableStateOf<User?>(null) }

    val adminProfilePicLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { editProfilePic = it.toString() }
    }

    val adminShopPicLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { editShopPic = it.toString() }
    }

    fun refreshData() {
        scope.launch {
            isLoading = true
            feedbackMessage = null
            try {
                // Fetch all registered users in the cloud dynamically
                val remoteUsers = withContext(Dispatchers.IO) {
                    CloudSyncEngine.fetchAllRegisteredUsers()
                }
                val rawFilteredList = remoteUsers
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
                    if (payload != null) {
                        tempCust += payload.customers.size
                        tempTx += payload.transactions.size
                        syncTimeMap[u.email] = payload.timestamp
                        
                        // Consider user active if synced within last 12 hours
                        val isActive = (System.currentTimeMillis() - payload.timestamp) <= (12 * 60 * 60 * 1000)
                        statusMap[u.email] = isActive
                        if (isActive) actUsers++ else inactUsers++

                        // Calculate sum of customer dues and dealer dues
                        custDuesMap[u.email] = payload.customers.sumOf { it.totalDue }
                        dealDuesMap[u.email] = payload.dealers.sumOf { it.totalOwed }
                    } else {
                        statusMap[u.email] = false
                        syncTimeMap[u.email] = 0L
                        inactUsers++
                        custDuesMap[u.email] = 0.0
                        dealDuesMap[u.email] = 0.0
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
                                            text = user.shopName.ifBlank { if (isBn) "নামবিহীন দোকান" else "Unnamed Shop" },
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
                                            val isShopHttp = !shopPic.isNullOrBlank() && (shopPic.startsWith("http://") || shopPic.startsWith("https://"))
                                            val finalShopModel = if (isShopHttp) shopPic else "https://images.unsplash.com/photo-1578916171728-46686eac8d58?auto=format&fit=crop&w=300&q=80"
                                            
                                            AsyncImage(
                                                model = finalShopModel,
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
                                            val isProfileHttp = !profilePic.isNullOrBlank() && (profilePic.startsWith("http://") || profilePic.startsWith("https://"))
                                            val finalProfileModel = if (isProfileHttp) profilePic else "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=150&q=80"
                                            
                                            AsyncImage(
                                                model = finalProfileModel,
                                                contentDescription = "Owner Profile Image",
                                                modifier = Modifier
                                                    .size(50.dp)
                                                    .clip(CircleShape)
                                                    .border(1.5.dp, colors.secondary, CircleShape),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                            
                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                val jointOwners = com.example.data.OwnerParser.deserialize(user.ownerName, user.phone, user.email)
                                                Text(
                                                    text = if (isBn) "মোট অংশীদার: ${jointOwners.size} জন" else "Total Partners: ${jointOwners.size}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = colors.secondary
                                                )

                                                Spacer(modifier = Modifier.height(2.dp))
                                                val isActive = activeStatuses[user.email] ?: false
                                                
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

                                                val syncTime = lastSyncTimes[user.email] ?: 0L
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

                                        // Dynamic Owner / Partners Card details rendering (Aesthetic, Professional!)
                                        val parsedOwners = com.example.data.OwnerParser.deserialize(user.ownerName, user.phone, user.email)
                                        parsedOwners.forEachIndexed { idx, owner ->
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant.copy(alpha = 0.2f)),
                                                border = BorderStroke(0.5.dp, colors.outlineVariant.copy(alpha = 0.5f))
                                            ) {
                                                Column(modifier = Modifier.padding(8.dp)) {
                                                    Text(
                                                        text = if (isBn) "অংশীদার #${idx + 1}" else "Partner #${idx + 1}",
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 11.sp,
                                                        color = colors.primary,
                                                        modifier = Modifier.padding(bottom = 4.dp)
                                                    )

                                                    // Name Row
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(vertical = 2.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = androidx.compose.material.icons.Icons.Default.Person,
                                                            contentDescription = "Name",
                                                            tint = colors.primary,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = owner.name.ifBlank { if (isBn) "নামবিহীন অংশীদার" else "Unnamed Partner" },
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = colors.onSurface
                                                        )
                                                    }

                                                    // Phone Row
                                                    if (owner.phone.isNotBlank()) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.padding(vertical = 2.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = androidx.compose.material.icons.Icons.Default.Phone,
                                                                contentDescription = "Phone",
                                                                tint = Color(0xFF4CAF50),
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                text = owner.phone,
                                                                fontSize = 12.sp,
                                                                color = colors.onSurfaceVariant
                                                            )
                                                        }
                                                    }

                                                    // Email Row
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
                                val customerDues = customerDuesMap[user.email] ?: 0.0
                                val dealerDues = dealerDuesMap[user.email] ?: 0.0

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
                                            editShopName = user.shopName
                                            editOwnerName = user.ownerName ?: ""
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
                                                viewModel.adminUpdateUserProfileAndSync(updatedUser) { success, msg ->
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
                        label = { Text(if (isBn) "দোকানের নাম" else "Shop Name") }
                    )
                    OutlinedTextField(
                        value = editOwnerName,
                        onValueChange = { editOwnerName = it },
                        label = { Text(if (isBn) "মালিকের নাম / অংশীদার" else "Owner Name / Partners") }
                    )
                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = { Text(if (isBn) "মোবাইল" else "Phone") }
                    )
                    OutlinedTextField(
                        value = editPin,
                        onValueChange = { editPin = it },
                        label = { Text(if (isBn) "পাসওয়ার্ড পিন" else "Password PIN") }
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
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AsyncImage(
                            model = finalProfilePic,
                            onError = { profileLoadFailed = true },
                            contentDescription = "Profile Picture Preview",
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, colors.primary, CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                        Button(
                            onClick = { adminProfilePicLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primary,
                                contentColor = colors.onPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isBn) "গ্যালারি থেকে ছবি নিন" else "Choose from Gallery", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AsyncImage(
                            model = finalShopPic,
                            onError = { shopLoadFailed = true },
                            contentDescription = "Shop Picture Preview",
                            modifier = Modifier
                                .size(width = 96.dp, height = 64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.5.dp, colors.secondary, RoundedCornerShape(8.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                        Button(
                            onClick = { adminShopPicLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.secondary,
                                contentColor = colors.onSecondary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isBn) "গ্যালারি থেকে দোকান ছবি" else "Choose Shop Gallery", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                            
                            val updatedUser = original.copy(
                                shopName = editShopName,
                                ownerName = editOwnerName,
                                phone = editPhone,
                                passwordHash = editPin,
                                profilePicture = editProfilePic.ifBlank { null },
                                shopPicture = editShopPic.ifBlank { null }
                            )
                            
                            viewModel.adminUpdateUserProfileAndSync(updatedUser) { success, msg ->
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
                        Text(
                            text = if (isBn) "🔌 সংযুক্ত সক্রিয় ডিভাইসসমূহ:" else "🔌 Connected Active Devices:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF4CAF50)
                        )
                        
                        if (activeDevs.isEmpty()) {
                            Text(
                                text = if (isBn) "(কোন সক্রিয় ডিভাইস তালিকাভুক্ত নেই)" else "(No active devices listed)",
                                fontSize = 11.sp,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        } else {
                            activeDevs.forEach { dev ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "• $dev", fontSize = 12.sp, modifier = Modifier.weight(1f))
                                    TextButton(
                                        onClick = {
                                            if (!blockedDevs.contains(dev)) {
                                                blockedDevs.add(dev)
                                            }
                                            activeDevs.remove(dev)
                                        },
                                        colors = ButtonDefaults.textButtonColors(contentColor = colors.error)
                                    ) {
                                        Text(if (isBn) "ব্লক করুন" else "Block", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        
                        Divider(thickness = 0.5.dp)
                        
                        // Blocked devices list
                        Text(
                            text = if (isBn) "🚨 ব্লকড ডিভাইসসমূহ:" else "🚨 Blocked Devices:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = colors.error
                        )
                        
                        if (blockedDevs.isEmpty()) {
                            Text(
                                text = if (isBn) "(কোন ব্লক করা ডিভাইস নেই)" else "(No blocked devices)",
                                fontSize = 11.sp,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        } else {
                            blockedDevs.forEach { dev ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "• $dev", fontSize = 12.sp, color = colors.error, modifier = Modifier.weight(1f))
                                    TextButton(
                                        onClick = {
                                            blockedDevs.remove(dev)
                                            if (!activeDevs.contains(dev)) {
                                                activeDevs.add(dev)
                                            }
                                        },
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF4CAF50))
                                    ) {
                                        Text(if (isBn) "আনব্লক করুন" else "Unblock", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                            
                            viewModel.adminUpdateUserProfileAndSync(updatedUser) { success, msg ->
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
