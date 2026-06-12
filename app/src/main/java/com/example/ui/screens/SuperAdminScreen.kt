package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
                userList = remoteUsers
                    .distinctBy { it.email.trim().lowercase() }
                    .filter { it.email.trim().lowercase() != "demo@example.com" }
                totalShopsCount = userList.size

                val statusMap = mutableMapOf<String, Boolean>()
                val syncTimeMap = mutableMapOf<String, Long>()

                // Calculate cumulative stats
                var tempCust = 0
                var tempTx = 0
                
                // Let's count totals
                userList.forEach { u ->
                    // download stats in the background
                    val payload = withContext(Dispatchers.IO) {
                        CloudSyncEngine.downloadPayload(u.email)
                    }
                    if (payload != null) {
                        tempCust += payload.customers.size
                        tempTx += payload.transactions.size
                        syncTimeMap[u.email] = payload.timestamp
                        // Consider user active if they have synced in internal timestamp within last 12 hrs
                        statusMap[u.email] = (System.currentTimeMillis() - payload.timestamp) <= (12 * 60 * 60 * 1000)
                    } else {
                        statusMap[u.email] = false
                        syncTimeMap[u.email] = 0L
                    }
                }
                activeStatuses = statusMap
                lastSyncTimes = syncTimeMap
                totalCustomersCount = tempCust
                totalTransactionsCount = tempTx

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background)
                .padding(16.dp)
        ) {
            // Stats Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.secondaryContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = if (isBn) "📊 লাইভ ডেটাবেজ ও ইউজার পরিসংখ্যান" else "📊 Live Database & Installer Statistics",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = if (isBn) "মোট রেজিস্টার্ড শপ/ইউজার" else "Total Active Shops",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                            Text(
                                text = if (isLoading && totalShopsCount == 0) "লোডিং..." else "$totalShopsCount টি",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = colors.primary
                            )
                        }
                        Column {
                            Text(
                                text = if (isBn) "সমগ্র গ্রাহক সংখ্যা" else "Total App Customers",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                            Text(
                                text = if (isLoading && totalCustomersCount == 0) "লোডিং..." else "$totalCustomersCount জন",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = colors.secondary
                            )
                        }
                        Column {
                            Text(
                                text = if (isBn) "মোট ট্রানজেকশন" else "Total Transactions",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                            Text(
                                text = if (isLoading && totalTransactionsCount == 0) "লোডিং..." else "$totalTransactionsCount টি",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = colors.tertiary
                            )
                        }
                    }
                }
            }

            if (feedbackMessage != null) {
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

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
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
            } else if (userList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isBn) "কোন রেজিস্টার্ড ইউজার পাওয়া যায়নি" else "No registered users found in directory",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onBackground.copy(alpha = 0.5f)
                    )
                }
            } else {
                Text(
                    text = if (isBn) "নিবন্ধিত দোকান ও মালিকদের তালিকা:" else "Registered Shops and Owners Link List:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = colors.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(userList) { user ->
                        // Render detailed Admin Shop card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = colors.surface),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                ) {
                                    // Shop Picture
                                    val shopPic = user.shopPicture
                                    if (!shopPic.isNullOrBlank()) {
                                        AsyncImage(
                                            model = shopPic,
                                            contentDescription = "Shop Image",
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(1.dp, colors.outlineVariant, RoundedCornerShape(8.dp)),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .background(colors.primaryContainer, RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = "Shop Placeholder",
                                                tint = colors.onPrimaryContainer,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Owner Profile Picture
                                    val profilePic = user.profilePicture
                                    if (!profilePic.isNullOrBlank()) {
                                        AsyncImage(
                                            model = profilePic,
                                            contentDescription = "Owner Profile Image",
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(CircleShape)
                                                .border(1.5.dp, colors.secondary, CircleShape),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .background(colors.secondaryContainer, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (user.shopName.isNotBlank()) user.shopName.take(1).uppercase() else "S",
                                                fontWeight = FontWeight.Bold,
                                                color = colors.onSecondaryContainer,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = user.shopName.ifBlank { if (isBn) "নামবিহীন দোকান" else "Unnamed Shop" },
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 15.sp,
                                            color = colors.onSurface
                                        )
                                        
                                        val jointOwners = com.example.data.OwnerParser.deserialize(user.ownerName, user.phone, user.email)
                                        Text(
                                            text = if (isBn) "মোট অংশীদার: ${jointOwners.size} জন" else "Total Partners: ${jointOwners.size}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = colors.secondary
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))
                                        val isActive = activeStatuses[user.email] ?: false
                                        val syncTime = lastSyncTimes[user.email] ?: 0L
                                        
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
                                
                                val parsedOwners = com.example.data.OwnerParser.deserialize(user.ownerName, user.phone, user.email)

                                parsedOwners.forEachIndexed { idx, owner ->
                                    val name = owner.name.ifBlank { if (isBn) "মালিক সংখ্যা ${idx+1}" else "Owner #${idx+1}" }
                                    Text(
                                        text = if (isBn) 
                                            "👤 $name (মোবাইল: ${owner.phone}, ইমেইল: ${owner.email})" 
                                        else 
                                            "👤 $name (Phone: ${owner.phone}, Email: ${owner.email})",
                                        fontSize = 11.sp,
                                        color = colors.onSurface.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                                    )
                                }

                                 if (!user.ipAddress.isNullOrBlank() || !user.registerLocation.isNullOrBlank()) {
                                     Spacer(modifier = Modifier.height(4.dp))
                                     Text(
                                         text = if (isBn) 
                                             "🌐 নিবন্ধিত আইপি: ${user.ipAddress ?: "অজানা"} (${user.registerLocation ?: "অজানা"})" 
                                         else 
                                             "🌐 Registered IP: ${user.ipAddress ?: "Unknown"} (${user.registerLocation ?: "Unknown"})",
                                         fontSize = 11.sp,
                                         fontWeight = FontWeight.Bold,
                                         color = colors.primary,
                                         modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                                     )
                                 }


                                Spacer(modifier = Modifier.height(8.dp))
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
                                                val updatedUser = user.copy(isBlocked = !user.isBlocked)
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

    // 2. Edit Profile Dialog
    if (showEditDialog && userToEdit != null) {
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
                            model = editProfilePic.ifBlank { "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=200&q=80" },
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
                    OutlinedTextField(
                        value = editProfilePic,
                        onValueChange = { editProfilePic = it },
                        label = { Text(if (isBn) "প্রোফাইল ছবি লিংক" else "Profile Picture Link") },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))
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
                            model = editShopPic.ifBlank { "https://images.unsplash.com/photo-1578916171728-46686eac8d58?auto=format&fit=crop&w=300&q=80" },
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
                    OutlinedTextField(
                        value = editShopPic,
                        onValueChange = { editShopPic = it },
                        label = { Text(if (isBn) "দোকানের ছবি লিংক" else "Shop Picture Link") },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                        modifier = Modifier.fillMaxWidth()
                    )
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
