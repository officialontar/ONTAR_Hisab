package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
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

    fun refreshData() {
        scope.launch {
            isLoading = true
            feedbackMessage = null
            try {
                // Fetch all registered users in the cloud dynamically
                val remoteUsers = withContext(Dispatchers.IO) {
                    CloudSyncEngine.fetchAllRegisteredUsers()
                }
                userList = remoteUsers.distinctBy { it.email.trim().lowercase() }
                totalShopsCount = userList.size

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
                    }
                }
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
}
