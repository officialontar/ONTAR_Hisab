package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModelProvider
import coil.compose.AsyncImage
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppViewModel
import com.example.viewmodel.AppViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(this)
        val repository = AppRepository(database.appDao())
        val viewModelFactory = AppViewModelFactory(repository, application)
        val viewModel = ViewModelProvider(this, viewModelFactory)[AppViewModel::class.java]

        setContent {
            val isDarkUser by viewModel.isDarkMode.collectAsState()

            MyApplicationTheme(darkTheme = isDarkUser) {
                val context = LocalContext.current
                val toastMsg by viewModel.toastMessage.collectAsState()
                LaunchedEffect(toastMsg) {
                    toastMsg?.let {
                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                        viewModel.clearToast()
                    }
                }

                val currentScreen by viewModel.currentScreen.collectAsState()
                val isBn by viewModel.isBengali.collectAsState()
                val currentUser by viewModel.currentUser.collectAsState()
                val showRightMenuDrawer by viewModel.showRightMenuDrawer.collectAsState()
                val userShops by viewModel.userShops.collectAsState()

                if (currentScreen != "LOGIN") {
                    BackHandler {
                        if (showRightMenuDrawer) {
                            viewModel.toggleRightMenuDrawer(false)
                        } else if (currentScreen != "DASHBOARD") {
                            viewModel.navigateTo("DASHBOARD")
                        } else {
                            val activity = (context as? android.app.Activity)
                            activity?.finish()
                        }
                    }
                }

                // Password change dialog state
                var showChangePinInDrawer by remember { mutableStateOf(false) }
                var showFirebaseSettingsDialog by remember { mutableStateOf(false) }
                var oldPinInput by remember { mutableStateOf("") }
                var newPinInput by remember { mutableStateOf("") }
                var confirmPinInput by remember { mutableStateOf("") }

                // Add Shop Dialog State
                var showAddShopInDrawer by remember { mutableStateOf(false) }
                var newShopNameInput by remember { mutableStateOf("") }
                var newOwnerNameInput by remember { mutableStateOf("") }
                var newPhoneInput by remember { mutableStateOf("") }
                var newShopPicPreset by remember { mutableStateOf("https://images.unsplash.com/photo-1578916171728-46686eac8d58?auto=format&fit=crop&w=300&q=80") }
                var newOwnerPicPreset by remember { mutableStateOf("https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=200&q=80") }

                val newShopPicLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri ->
                    uri?.let { 
                        viewModel.uriToBase64(context, it)?.let { base64 ->
                            newShopPicPreset = base64
                        }
                    }
                }

                val newOwnerPicLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri ->
                    uri?.let { 
                        viewModel.uriToBase64(context, it)?.let { base64 ->
                            newOwnerPicPreset = base64
                        }
                    }
                }

                val newShopCameraLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.TakePicturePreview()
                ) { bitmap ->
                    if (bitmap != null) {
                        viewModel.bitmapToBase64(bitmap)?.let { base64 ->
                            newShopPicPreset = base64
                        }
                    }
                }

                val newOwnerCameraLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.TakePicturePreview()
                ) { bitmap ->
                    if (bitmap != null) {
                        viewModel.bitmapToBase64(bitmap)?.let { base64 ->
                            newOwnerPicPreset = base64
                        }
                    }
                }

                val colors = MaterialTheme.colorScheme

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (currentScreen != "LOGIN") {
                            NavigationBar(
                                containerColor = colors.surface,
                                tonalElevation = 8.dp
                            ) {
                                NavigationBarItem(
                                    selected = currentScreen == "CUSTOMER_LEDGER",
                                    onClick = { 
                                        viewModel.toggleRightMenuDrawer(false)
                                        viewModel.navigateTo("CUSTOMER_LEDGER") 
                                    },
                                    icon = { Icon(Icons.Default.AccountCircle, null) },
                                    label = { Text(if (isBn) "বাকি কাস্টমার" else "Due Customers", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                                )
                                NavigationBarItem(
                                    selected = currentScreen == "SALES_BILLING",
                                    onClick = { 
                                        viewModel.toggleRightMenuDrawer(false)
                                        viewModel.navigateTo("SALES_BILLING") 
                                    },
                                    icon = { Icon(Icons.Default.ShoppingCart, null) },
                                    label = { Text(if (isBn) "ক্যাশবক্স" else "Cash Box", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                                )
                                NavigationBarItem(
                                    selected = currentScreen == "REPORTS",
                                    onClick = { 
                                        viewModel.toggleRightMenuDrawer(false)
                                        viewModel.navigateTo("REPORTS") 
                                    },
                                    icon = { Icon(Icons.Default.Star, null) },
                                    label = { Text(if (isBn) "অন্তর হিসাব" else "ONTAR Hisab", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                                )
                                NavigationBarItem(
                                    selected = showRightMenuDrawer,
                                    onClick = { viewModel.toggleRightMenuDrawer(!showRightMenuDrawer) },
                                    icon = { Icon(Icons.Default.Menu, null) },
                                    label = { Text(if (isBn) "মেনু বার" else "Menu Bar", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        val user = currentUser
                        val isAlwaysAdmin = user?.email?.trim()?.lowercase() == "mdanisujjamanontar@gmail.com"
                        val isBlocked = user?.isBlocked == true && !isAlwaysAdmin
                        val isDeviceBlocked = user?.let {
                            if (isAlwaysAdmin) {
                                false
                            } else {
                                val blockedList = try {
                                    val arr = org.json.JSONArray(it.blockedDevicesJson ?: "[]")
                                    List(arr.length()) { i -> arr.getString(i) }
                                } catch(e: Exception) { emptyList<String>() }
                                blockedList.contains(viewModel.getDeviceName())
                            }
                        } ?: false

                        val otaConfig by viewModel.otaConfig.collectAsState()
                        val isForceUpdateBypassed by viewModel.isForceUpdateBypassed.collectAsState()
                        val isUpdateRequired = otaConfig.latestVersionCode > 1
                        val forceUpdateActive = isUpdateRequired && otaConfig.forceUpdateEnabled && !isForceUpdateBypassed && currentUser?.email != "mdanisujjamanontar@gmail.com"

                        if (forceUpdateActive && currentScreen != "LOGIN") {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.Card(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    colors = androidx.compose.material3.CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    shape = RoundedCornerShape(24.dp),
                                    elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(28.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Build,
                                            contentDescription = "Update Required",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(72.dp)
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                        Text(
                                            text = if (isBn) "নতুন একটি আধুনিক আপডেট উপলভ্য!" else "New Modern Update Available!",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 22.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = if (isBn) {
                                                "দয়া করে অ্যাপ্লিকেশনটির সর্বশেষ এবং মসৃণ সংস্করণটিতে আপডেট করুন। নতুন সংস্করণগুলোতে কোনো প্রকার ল্যাগ বা বাগ ছাড়াই সকল প্রিমিয়াম ফিচার সচল রয়েছে!\n\nআপনার সংস্করণ: ১.০ (v1) | সর্বশেষ সংস্করণ: ${otaConfig.latestVersionName} (v${otaConfig.latestVersionCode})"
                                            } else {
                                                "Please update to the latest premium version of the app. All premium features are active in the latest builds with smooth lifetime backup support!\n\nYour Version: 1.0 (v1) | Latest Version: ${otaConfig.latestVersionName} (v${otaConfig.latestVersionCode})"
                                            },
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                        val dynamicMsg = if (isBn) otaConfig.bengaliMessage else otaConfig.englishMessage
                                        if (dynamicMsg.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(16.dp))
                                            androidx.compose.material3.Card(
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = dynamicMsg,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                    modifier = Modifier.padding(12.dp).fillMaxWidth()
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(24.dp))
                                        Button(
                                            onClick = {
                                                try {
                                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(otaConfig.updateDownloadUrl))
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Could not open download link", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (isBn) "সবচেয়ে লেটেস্ট সংস্করণটি ডাউনলোড করুন" else "Download Latest Version",
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        androidx.compose.material3.TextButton(
                                            onClick = {
                                                viewModel.bypassForceUpdate()
                                                Toast.makeText(
                                                    context,
                                                    if (isBn) "আগের সংস্করণটি সফলভাবে সচল রাখা হয়েছে!" else "Running the already installed version!",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        ) {
                                            Text(
                                                text = if (isBn) "আগের সংস্করণটি ব্যবহার করুন (যদি নতুনটি সাপোর্ট না করে)" else "Use Installed Version (if latest is unsupported)",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp,
                                                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                            )
                                        }
                                    }
                                }
                            }
                        } else if ((isBlocked || isDeviceBlocked) && currentScreen != "LOGIN") {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.Card(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    colors = androidx.compose.material3.CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Access Blocked",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(64.dp)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = if (isBn) {
                                                if (isDeviceBlocked) "এই ডিভাইসটি ব্লক করা হয়েছে!" else "আপনার অ্যাকাউন্টটি ব্লক করা হয়েছে!"
                                            } else {
                                                if (isDeviceBlocked) "This Device is Blocked!" else "Your Account is Blocked!"
                                            },
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 20.sp,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = if (isBn) {
                                                if (isDeviceBlocked) "প্রশাসক এই বিশেষ ডিভাইস থেকে আপনার অ্যাকাউন্ট অ্যাক্সেস করা সাময়িকভাবে নিষিদ্ধ করেছেন। অনুগ্রহ করে মূল অ্যাডমিনের সাথে যোগাযোগ করুন।" else "প্রশাসক আপনার অ্যাকাউন্টটি সাময়িকভাবে ব্লক করেছেন। অনুগ্রহ করে মূল অ্যাডমিনের সাথে যোগাযোগ করুন।"
                                            } else {
                                                if (isDeviceBlocked) "The administrator has temporarily suspended access from this device. Please contact administration." else "The administrator has suspended your account. Please contact administration."
                                            },
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(24.dp))
                                        Button(
                                            onClick = { viewModel.logout() },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                        ) {
                                            Text(
                                                text = if (isBn) "লগ আউট করুন" else "Log Out",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Display current active screen
                            when (currentScreen) {
                                "LOGIN" -> AuthScreen(viewModel)
                                "DASHBOARD" -> MainDashboard(viewModel)
                                "STOCK_MANAGER" -> StockManagerScreen(viewModel)
                                "SALES_BILLING" -> SalesBillingScreen(viewModel)
                                "CUSTOMER_LEDGER" -> CustomerLedgerScreen(viewModel)
                                "DEALER_LEDGER" -> DealerLedgerScreen(viewModel)
                                "REPORTS" -> ReportsScreen(viewModel)
                                "AI_COACH" -> AiCoachScreen(viewModel)
                                "SUPER_ADMIN" -> SuperAdminScreen(viewModel)
                                else -> AuthScreen(viewModel)
                            }
                        }

                        // Right Slide Drawer Overlay
                        AnimatedVisibility(
                            visible = showRightMenuDrawer && currentScreen != "LOGIN",
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .clickable { viewModel.toggleRightMenuDrawer(false) }
                            )
                        }

                        AnimatedVisibility(
                            visible = showRightMenuDrawer && currentScreen != "LOGIN",
                            enter = slideInHorizontally(initialOffsetX = { it }),
                            exit = slideOutHorizontally(targetOffsetX = { it })
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.TopEnd
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(310.dp)
                                        .clickable(enabled = false) {}, // prevent click-through
                                    color = colors.surface,
                                    tonalElevation = 16.dp,
                                    shadowElevation = 16.dp
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        // Header: App Logo / Title & Close
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Settings,
                                                    contentDescription = null,
                                                    tint = colors.primary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = if (isBn) "সেটিংস ও নিয়ন্ত্রণ" else "Settings Control",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.primary
                                                )
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                val isSyncing by viewModel.isCloudSyncing.collectAsState()
                                                IconButton(
                                                    onClick = { viewModel.triggerCloudSync() },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    if (isSyncing) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(16.dp),
                                                            strokeWidth = 2.dp,
                                                            color = colors.primary
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = Icons.Default.Refresh,
                                                            contentDescription = null,
                                                            tint = Color(0xFF0F9D58),
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(4.dp))
                                                IconButton(
                                                    onClick = { viewModel.toggleRightMenuDrawer(false) },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(Icons.Default.Close, null)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        // User Profile Section
                                        currentUser?.let { user ->
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(containerColor = colors.primary.copy(alpha = 0.08f)),
                                                shape = RoundedCornerShape(16.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    AsyncImage(
                                                        model = rememberImageModel(user.profilePicture ?: "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=200&q=80"),
                                                        contentDescription = null,
                                                        modifier = Modifier
                                                            .size(54.dp)
                                                            .clip(CircleShape)
                                                            .border(2.dp, colors.primary, CircleShape)
                                                    )
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = user.getLocalizedShopName(isBn),
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = colors.primary
                                                        )
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                        
                                                        val ownersList = com.example.data.OwnerParser.deserialize(user.getLocalizedOwnerName(isBn), user.phone, user.email)
                                                        ownersList.forEachIndexed { idx, owner ->
                                                            if (idx > 0) {
                                                                Spacer(modifier = Modifier.height(10.dp))
                                                                HorizontalDivider(color = colors.onSurface.copy(alpha = 0.08f))
                                                                Spacer(modifier = Modifier.height(6.dp))
                                                            }
                                                            if (owner.name.isNotBlank()) {
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    modifier = Modifier.padding(vertical = 2.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Person,
                                                                        contentDescription = null,
                                                                        tint = colors.primary.copy(alpha = 0.75f),
                                                                        modifier = Modifier.size(16.dp)
                                                                    )
                                                                    Spacer(modifier = Modifier.width(6.dp))
                                                                    Text(
                                                                        text = owner.name,
                                                                        style = MaterialTheme.typography.bodyMedium,
                                                                        color = colors.onSurface.copy(alpha = 0.9f),
                                                                        fontWeight = FontWeight.Bold
                                                                    )
                                                                }
                                                            }
                                                            if (owner.phone.isNotBlank()) {
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    modifier = Modifier.padding(vertical = 2.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Phone,
                                                                        contentDescription = null,
                                                                        tint = colors.primary.copy(alpha = 0.75f),
                                                                        modifier = Modifier.size(16.dp)
                                                                    )
                                                                    Spacer(modifier = Modifier.width(6.dp))
                                                                    Text(
                                                                        text = owner.phone,
                                                                        style = MaterialTheme.typography.bodyMedium,
                                                                        color = colors.onSurface.copy(alpha = 0.75f)
                                                                    )
                                                                }
                                                            }
                                                            if (owner.email.isNotBlank()) {
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    modifier = Modifier.padding(vertical = 2.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Email,
                                                                        contentDescription = null,
                                                                        tint = colors.primary.copy(alpha = 0.75f),
                                                                        modifier = Modifier.size(16.dp)
                                                                    )
                                                                    Spacer(modifier = Modifier.width(6.dp))
                                                                    Text(
                                                                        text = owner.email,
                                                                        style = MaterialTheme.typography.bodyMedium,
                                                                        color = colors.onSurface.copy(alpha = 0.75f)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        // Section 2: Manage Shops List (SWITCH SHops - MOVED TO TOP!)
                                        Text(
                                            text = if (isBn) "আমার পরিচালিত দোকানসমূহ (সর্বোচ্চ ৫)" else "Manage Shops (Max 5)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = colors.primary.copy(alpha = 0.8f),
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )

                                        // List all shops of the active root account
                                        userShops.forEach { shopUser ->
                                            val isActiveShop = shopUser.email == (currentUser?.email ?: "")
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(if (isActiveShop) colors.primary.copy(alpha = 0.08f) else Color.Transparent)
                                                    .border(
                                                        width = if (isActiveShop) 1.dp else 0.dp,
                                                        color = if (isActiveShop) colors.primary.copy(alpha = 0.3f) else Color.Transparent,
                                                        shape = RoundedCornerShape(10.dp)
                                                    )
                                                    .clickable {
                                                        viewModel.switchActiveShop(shopUser)
                                                    }
                                                    .padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                AsyncImage(
                                                    model = rememberImageModel(shopUser.shopPicture ?: "https://images.unsplash.com/photo-1578916171728-46686eac8d58?auto=format&fit=crop&w=300&q=80"),
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = shopUser.getLocalizedShopName(isBn),
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = colors.onSurface
                                                    )
                                                    Text(
                                                        text = com.example.data.OwnerParser.deserialize(shopUser.getLocalizedOwnerName(isBn), shopUser.phone, shopUser.email).map { o -> o.name }.filter { n -> n.isNotBlank() }.joinToString(", "),
                                                        fontSize = 11.sp,
                                                        color = colors.onSurface.copy(alpha = 0.6f)
                                                    )
                                                }
                                                if (isActiveShop) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = null,
                                                        tint = colors.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                        }

                                        if (userShops.size < 5) {
                                            OutlinedButton(
                                                onClick = { showAddShopInDrawer = true },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary)
                                            ) {
                                                Icon(Icons.Default.Add, null)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (isBn) "নতুন দোকান যুক্ত করুন" else "Add New Shop",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }

                                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                                        // Section 1: Dashboard / Shop Info
                                        Text(
                                            text = if (isBn) "দোকান ও ব্যবসা পরিচালনা" else "Shop & Business",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = colors.primary.copy(alpha = 0.8f),
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )

                                        // Item: Home Dashboard navigation
                                        MenuDrawerListItem(
                                            icon = Icons.Default.Home,
                                            title = if (isBn) "হোম ড্যাশবোর্ড" else "Home Dashboard",
                                            onClick = {
                                                viewModel.toggleRightMenuDrawer(false)
                                                viewModel.navigateTo("DASHBOARD")
                                            }
                                        )

                                        // Item: Stock Manager
                                        MenuDrawerListItem(
                                            icon = Icons.Default.List,
                                            title = if (isBn) "স্টক ম্যানেজার ও পণ্য তালিকা" else "Stock Inventory",
                                            onClick = {
                                                viewModel.toggleRightMenuDrawer(false)
                                                viewModel.navigateTo("STOCK_MANAGER")
                                            }
                                        )

                                        // Item: Financial Reports / Proft P&L
                                        MenuDrawerListItem(
                                            icon = Icons.Default.Star,
                                            title = if (isBn) "লাভ-ক্ষতির আর্থিক রিপোর্ট" else "Profit-Loss Analytics",
                                            onClick = {
                                                viewModel.toggleRightMenuDrawer(false)
                                                viewModel.navigateTo("REPORTS")
                                            }
                                        )

                                        // Item: Dealers list
                                        MenuDrawerListItem(
                                            icon = Icons.Default.Person,
                                            title = if (isBn) "ডিলার ও পাওনাদার হিসাব" else "Dealers Ledger",
                                            onClick = {
                                                viewModel.toggleRightMenuDrawer(false)
                                                viewModel.navigateTo("DEALER_LEDGER")
                                            }
                                        )

                                        // Item: AI business coach
                                        MenuDrawerListItem(
                                            icon = Icons.Default.Email,
                                            title = if (isBn) "স্মার্ট এআই বিজনেস কোচ" else "Smart AI Business Coach",
                                            onClick = {
                                                viewModel.toggleRightMenuDrawer(false)
                                                viewModel.navigateTo("AI_COACH")
                                            }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                                        // Section 3: Administrative Control
                                        Text(
                                            text = if (isBn) "নিরাপত্তা ও সেটিংস" else "Security & Settings",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = colors.primary.copy(alpha = 0.8f),
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )

                                        // Change password
                                        MenuDrawerListItem(
                                            icon = Icons.Default.Lock,
                                            title = if (isBn) "পিন কোড পরিবর্তন করুন" else "Change Account PIN",
                                            onClick = { showChangePinInDrawer = true }
                                        )

                                        // Data Recovery Support
                                        MenuDrawerListItem(
                                            icon = Icons.Default.Refresh,
                                            title = if (isBn) "ফায়ারবেস ক্লাউড রিকভারি" else "Firebase Cloud Recovery",
                                            onClick = {
                                                viewModel.toggleRightMenuDrawer(false)
                                                viewModel.recoverAllCloudData()
                                             }
                                         )

                                         // Firebase Cloud Connection Settings
                                         MenuDrawerListItem(
                                             icon = Icons.Default.Build,
                                             title = if (isBn) "ফায়ারবেস ক্লাউড কানেকশন" else "Firebase Cloud Connection",
                                             onClick = {
                                                 viewModel.toggleRightMenuDrawer(false)
                                                 showFirebaseSettingsDialog = true
                                             }
                                         )

                                        // Share link
                                        MenuDrawerListItem(
                                            icon = Icons.Default.Share,
                                            title = if (isBn) "আমার অ্যাপের রেফারেল লিঙ্ক" else "App Referral Invite Link",
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(
                                                        Intent.EXTRA_TEXT,
                                                        if (isBn) "অন্তর হিসাব অ্যাপটি ব্যবহার করে আপনার দোকানের হিসাব রাখুন সহজেই! ডাউনলোড লিঙ্ক: https://github.com/officialontar/ONTAR_Hisab/releases"
                                                        else "Keep track of your shop books easily with ONTAR Hisab App! Download link: https://github.com/officialontar/ONTAR_Hisab/releases"
                                                    )
                                                }
                                                context.startActivity(Intent.createChooser(intent, "Share invite via"))
                                            }
                                        )

                                        // Dark mode toggle
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Star,
                                                    contentDescription = null,
                                                    tint = colors.onSurface.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = if (isBn) "নাইট মোড / ডার্ক থিম" else "Night / Dark Mode",
                                                    fontSize = 14.sp,
                                                    color = colors.onSurface
                                                )
                                            }
                                            Switch(
                                                checked = isDarkUser,
                                                onCheckedChange = { viewModel.toggleDarkMode() }
                                            )
                                        }

                                        // Language Switch
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = null,
                                                    tint = colors.onSurface.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = if (isBn) "ভাষা (বাংলা)" else "Language (Bangla)",
                                                    fontSize = 14.sp,
                                                    color = colors.onSurface
                                                )
                                            }
                                            Switch(
                                                checked = isBn,
                                                onCheckedChange = { viewModel.toggleLanguage() }
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        if (currentUser?.email?.trim()?.lowercase() == "mdanisujjamanontar@gmail.com") {
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                                            Text(
                                                text = if (isBn) "বিশেষ প্রশাসনিক নিয়ন্ত্রণ" else "Super Admin Area",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = colors.error.copy(alpha = 0.8f),
                                                modifier = Modifier.padding(bottom = 8.dp)
                                            )
                                            MenuDrawerListItem(
                                                icon = Icons.Default.Settings,
                                                title = if (isBn) "সুপার এডমিন ড্যাশবোর্ড" else "Super Admin Panel",
                                                onClick = {
                                                    viewModel.toggleRightMenuDrawer(false)
                                                    viewModel.navigateTo("SUPER_ADMIN")
                                                }
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                        }

                                        // Logout Button
                                        Button(
                                            onClick = {
                                                viewModel.toggleRightMenuDrawer(false)
                                                viewModel.logout()
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = colors.error),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.ExitToApp, null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (isBn) "লগ আউট করুন" else "Log Out Account",
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(20.dp))

                                        // Bottom App Icon, Name and Android Version information row!
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                AsyncImage(
                                                    model = R.drawable.ic_ontar_logo_1780859929177,
                                                    contentDescription = "ONTAR_Hisab Logo",
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(RoundedCornerShape(8.dp)),
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "ONTAR_Hisab",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.primary
                                                )
                                            }
                                            val appVer = "7.0.1"
                                            val bnAppVer = "৭.০.১"
                                            Text(
                                                text = if (isBn) "ভার্সন $bnAppVer" else "Version $appVer",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.onSurface.copy(alpha = 0.6f)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(24.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // FIREBASE DATABASE CONNECTION SETTINGS DIALOG
                if (showFirebaseSettingsDialog) {
                    val currentDbUrlVal by viewModel.dbUrl.collectAsState()
                    var customDbUrlInput by remember { mutableStateOf(currentDbUrlVal) }

                    AlertDialog(
                        onDismissRequest = { showFirebaseSettingsDialog = false },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Build, null, tint = colors.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isBn) "ফায়ারবেস কানেকশন সেটিংস" else "Firebase Cloud Setup",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        text = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = if (isBn) "আপনার সুরক্ষার ও ডাটা হারানো রোধ করতে আপনার নিজস্ব ফায়ারবেস রিয়েলটাইম ডাটাবেজ লিংকটি নিচে সেট করুন।" 
                                           else "Configure your own Firebase Realtime Database URL to enable private cloud backups and automated recovery.",
                                    fontSize = 12.sp,
                                    color = colors.onSurface.copy(alpha = 0.7f)
                                )

                                OutlinedTextField(
                                    value = customDbUrlInput,
                                    onValueChange = { customDbUrlInput = it },
                                    label = { Text(if (isBn) "ফায়ারবেস ডাটাবেজ লিংক (URL)" else "Firebase RTDB URL") },
                                    placeholder = { Text("https://your-project-id.firebaseio.com/") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                )

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = colors.primary.copy(alpha = 0.05f)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = if (isBn) "কিভাবে নিজের ফায়ারবেস কানেক্ট করবেন:" else "How to Connect Your Firebase:",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = colors.primary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (isBn) {
                                                "১. ব্রাউজারে firebase.google.com-এ যান এবং গুগল দিয়ে লগইন করুন।\n" +
                                                "২. Go to Console-এ ক্লিক করে Add Project দিয়ে একটি প্রোজেক্ট ক্রিয়েট করুন।\n" +
                                                "৩. বা-পাশের মেন্যু Build মেন্যু থেকে Realtime Database-এ গিয়ে Create Database ক্লিক করুন।\n" +
                                                "৪. লোকেশন আপনার সুবিধা মতো দিয়ে Test Mode (Rules = true) সিলেক্ট করে ক্রিয়েট করুন।\n" +
                                                "৫. রুলস (Rules) ট্যাবে গিয়ে নিশ্চিত করুন যে \".read\": true এবং \".write\": true করা আছে।\n" +
                                                "৬. ডাটাবেজ স্ক্রিনের উপর থেকে লিংকটি (যা দেখতে https://...firebaseio.com/ এর মতো) কপি করুন এবং ওপরের বক্সে পেস্ট করে সেভ করুন।\n\n" +
                                                "💡 এখন অ্যাপস আনইন্সটল করার পরে নতুন করে ইনস্টল দিলেও এই লিংকটি পেস্ট করে দিয়ে লগইন করলে আপনার সব ডাটা ও ছবি সাথে সাথে পুনরায় ফিরে আসবে!"
                                            } else {
                                                "1. Go to firebase.google.com & log in with Gmail.\n" +
                                                "2. Click 'Go to Console' & select 'Add Project' to create a project.\n" +
                                                "3. Go to 'Realtime Database' (under Build) & click 'Create Database'.\n" +
                                                "4. Choose location and enable in Test Mode (Rules set to true).\n" +
                                                "5. Check Rules tab to ensure \".read\": true and \".write\": true are configured.\n" +
                                                "6. Copy the URL from database dashboard and paste here to save!"
                                            },
                                            fontSize = 11.sp,
                                            lineHeight = 16.sp,
                                            color = colors.onSurface.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TextButton(
                                    onClick = {
                                        viewModel.updateFirebaseUrl("https://ontar-hisab-eb1ea-default-rtdb.firebaseio.com/")
                                        showFirebaseSettingsDialog = false
                                    }
                                ) {
                                    Text(
                                        text = if (isBn) "ডিফল্ট লিংক" else "Reset Default",
                                        color = colors.onSurface.copy(alpha = 0.5f),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row {
                                    TextButton(onClick = { showFirebaseSettingsDialog = false }) {
                                        Text(text = if (isBn) "বাতিল" else "Cancel")
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Button(
                                        onClick = {
                                            viewModel.updateFirebaseUrl(customDbUrlInput)
                                            showFirebaseSettingsDialog = false
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(text = if (isBn) "সেভ করুন" else "Save Settings")
                                    }
                                }
                            }
                        }
                    )
                }

                // PASSWORD CHANGE DIALOG
                if (showChangePinInDrawer) {
                    AlertDialog(
                        onDismissRequest = { showChangePinInDrawer = false },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, null, tint = colors.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isBn) "পিন কোড পরিবর্তন করুন" else "Change Account PIN",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = if (isBn) "আপনার সুরক্ষার নিশ্চিত করতে পুরনো ৪ বা ৬ অংকের পিন ও নতুন পিন দিয়ে পরিবর্তন করুন।" else "Configure a secure 4 or 6 digit login PIN for security.",
                                    fontSize = 12.sp,
                                    color = colors.onSurface.copy(alpha = 0.6f)
                                )

                                OutlinedTextField(
                                    value = oldPinInput,
                                    onValueChange = { oldPinInput = it },
                                    label = { Text(if (isBn) "বর্তমান পিন (Old PIN)" else "Current Old PIN") },
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                )

                                OutlinedTextField(
                                    value = newPinInput,
                                    onValueChange = { newPinInput = it },
                                    label = { Text(if (isBn) "নতুন চার বা ছয় সংখ্যার পিন" else "New PIN") },
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                )

                                OutlinedTextField(
                                    value = confirmPinInput,
                                    onValueChange = { confirmPinInput = it },
                                    label = { Text(if (isBn) "নতুন পিন নিশ্চিত করুন" else "Confirm New PIN") },
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (oldPinInput.isBlank() || newPinInput.isBlank() || confirmPinInput.isBlank()) {
                                        Toast.makeText(context, if (isBn) "দয়া করে সব ঘর পূরণ করুন" else "Please fill up all inputs", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (newPinInput != confirmPinInput) {
                                        Toast.makeText(context, if (isBn) "নতুন পিন মেলেনি" else "Confirm PIN mismatch", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    viewModel.changeUserPassword(oldPinInput, newPinInput, confirmPinInput)
                                    showChangePinInDrawer = false
                                    oldPinInput = ""
                                    newPinInput = ""
                                    confirmPinInput = ""
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(if (isBn) "পরিবর্তন করুন" else "Change Now")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showChangePinInDrawer = false }) {
                                Text(if (isBn) "বাতিল" else "Cancel")
                            }
                        },
                        shape = RoundedCornerShape(20.dp)
                    )
                }

                // NEW SHOP REGISTRATION/CREATION DIALOG
                if (showAddShopInDrawer) {
                    AlertDialog(
                        onDismissRequest = { showAddShopInDrawer = false },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Add, null, tint = colors.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isBn) "নতুন দোকান যুক্ত করুন" else "Add New Shop",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        text = {
                            val dialogScroll = rememberScrollState()
                            Column(
                                modifier = Modifier.verticalScroll(dialogScroll),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = newShopNameInput,
                                    onValueChange = { newShopNameInput = it },
                                    label = { Text(if (isBn) "দোকানের সম্পূর্ণ নাম" else "Shop Business Name") },
                                    singleLine = true,
                                    leadingIcon = { Icon(Icons.Default.Home, null) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                )

                                OutlinedTextField(
                                    value = newOwnerNameInput,
                                    onValueChange = { newOwnerNameInput = it },
                                    label = { Text(if (isBn) "মালিক বা দোকানদারের নাম" else "Owner Full Name") },
                                    singleLine = true,
                                    leadingIcon = { Icon(Icons.Default.Person, null) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                )

                                OutlinedTextField(
                                    value = newPhoneInput,
                                    onValueChange = { newPhoneInput = it },
                                    label = { Text(if (isBn) "মোবাইল নাম্বার" else "Mobile Phone Number") },
                                    singleLine = true,
                                    leadingIcon = { Icon(Icons.Default.Phone, null) },
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                )

                                // Preset selection for Shop image
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant.copy(alpha = 0.35f)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = if (isBn) "দোকানের ছবি নির্বাচন করুন" else "Select Shop Design",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.primary,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            val shopSelectionOptions = listOf(
                                                "https://images.unsplash.com/photo-1578916171728-46686eac8d58?auto=format&fit=crop&w=300&q=80" to (if (isBn) "মুদি" else "Grocery"),
                                                "https://images.unsplash.com/photo-1604719312566-8912e9227c6a?auto=format&fit=crop&w=300&q=80" to (if (isBn) "সুপার" else "Mart"),
                                                "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?auto=format&fit=crop&w=300&q=80" to (if (isBn) "টেলিকম" else "Telecom"),
                                                "https://images.unsplash.com/photo-1441986300917-64674bd600d8?auto=format&fit=crop&w=300&q=80" to (if (isBn) "অন্যান্য" else "Other")
                                            )
                                            shopSelectionOptions.forEach { (url, label) ->
                                                val isSel = newShopPicPreset == url
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (isSel) colors.primary.copy(alpha = 0.2f) else Color.Transparent)
                                                        .border(
                                                            width = if (isSel) 2.dp else 0.dp,
                                                            color = if (isSel) colors.primary else Color.Transparent,
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                        .clickable { newShopPicPreset = url }
                                                        .padding(2.dp)
                                                ) {
                                                    AsyncImage(
                                                        model = url,
                                                        contentDescription = null,
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .clip(RoundedCornerShape(4.dp))
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(text = label, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                         ) {
                                             OutlinedButton(
                                                 onClick = { newShopPicLauncher.launch("image/*") },
                                                 modifier = Modifier.weight(1f),
                                                 shape = RoundedCornerShape(10.dp),
                                                 contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                             ) {
                                                 Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                                 Spacer(modifier = Modifier.width(4.dp))
                                                 Text(text = if (isBn) "গ্যালারি ছবি" else "Gallery Picture", fontSize = 11.sp)
                                             }

                                             OutlinedButton(
                                                 onClick = { newShopCameraLauncher.launch(null) },
                                                 modifier = Modifier.weight(1f),
                                                 shape = RoundedCornerShape(10.dp),
                                                 contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                             ) {
                                                 Box(
                                                     modifier = Modifier.size(16.dp),
                                                     contentAlignment = Alignment.Center
                                                 ) {
                                                     Box(
                                                         modifier = Modifier
                                                             .size(15.dp, 10.dp)
                                                             .border(1.2.dp, colors.primary, RoundedCornerShape(1.5.dp))
                                                     )
                                                     Box(
                                                         modifier = Modifier
                                                             .size(5.5.dp)
                                                             .border(1.2.dp, colors.primary, CircleShape)
                                                     )
                                                     Box(
                                                         modifier = Modifier
                                                             .size(4.dp, 1.2.dp)
                                                             .align(Alignment.TopCenter)
                                                             .background(colors.primary, RoundedCornerShape(topStart = 0.5.dp, topEnd = 0.5.dp))
                                                     )
                                                 }
                                                 Spacer(modifier = Modifier.width(4.dp))
                                                 Text(text = if (isBn) "ক্যামেরা ছবি" else "Camera Picture", fontSize = 11.sp)
                                             }
                                         }

                                        val isCustomShop = !listOf(
                                            "https://images.unsplash.com/photo-1578916171728-46686eac8d58?auto=format&fit=crop&w=300&q=80",
                                            "https://images.unsplash.com/photo-1604719312566-8912e9227c6a?auto=format&fit=crop&w=300&q=80",
                                            "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?auto=format&fit=crop&w=300&q=80",
                                            "https://images.unsplash.com/photo-1441986300917-64674bd600d8?auto=format&fit=crop&w=300&q=80"
                                        ).contains(newShopPicPreset) && !newShopPicPreset.isBlank()

                                        if (isCustomShop) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(colors.primary.copy(alpha = 0.1f))
                                                    .padding(4.dp)
                                            ) {
                                                AsyncImage(
                                                    model = rememberImageModel(newShopPicPreset),
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(30.dp)
                                                        .clip(RoundedCornerShape(4.dp)),
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = if (isBn) "গ্যালারি ছবি সফলভাবে যুক্ত হয়েছে!" else "Gallery photo added!",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.primary
                                                )
                                            }
                                        }
                                    }
                                }

                                // Preset selection for Owner profile image
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant.copy(alpha = 0.35f)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = if (isBn) "দোকানদারের প্রোফাইল ছবি" else "Select Shopkeeper Profile",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.primary,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            val ownerSelectionOptions = listOf(
                                                "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=200&q=80" to (if (isBn) "মালিক ১" else "Owner 1"),
                                                "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=200&q=80" to (if (isBn) "মালিক ২" else "Owner 2"),
                                                "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=200&q=80" to (if (isBn) "মালিক ৩" else "Owner 3"),
                                                "https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?auto=format&fit=crop&w=200&q=80" to (if (isBn) "মালিক ৪" else "Owner 4")
                                            )
                                            ownerSelectionOptions.forEach { (url, label) ->
                                                val isSel = newOwnerPicPreset == url
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (isSel) colors.primary.copy(alpha = 0.2f) else Color.Transparent)
                                                        .border(
                                                            width = if (isSel) 2.dp else 0.dp,
                                                            color = if (isSel) colors.primary else Color.Transparent,
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                        .clickable { newOwnerPicPreset = url }
                                                        .padding(2.dp)
                                                ) {
                                                    AsyncImage(
                                                        model = url,
                                                        contentDescription = null,
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .clip(CircleShape)
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(text = label, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                         ) {
                                             OutlinedButton(
                                                 onClick = { newOwnerPicLauncher.launch("image/*") },
                                                 modifier = Modifier.weight(1f),
                                                 shape = RoundedCornerShape(10.dp),
                                                 contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                             ) {
                                                 Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                                 Spacer(modifier = Modifier.width(4.dp))
                                                 Text(text = if (isBn) "গ্যালারি ছবি" else "Gallery Profile", fontSize = 11.sp)
                                             }

                                             OutlinedButton(
                                                 onClick = { newOwnerCameraLauncher.launch(null) },
                                                 modifier = Modifier.weight(1f),
                                                 shape = RoundedCornerShape(10.dp),
                                                 contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                             ) {
                                                 Box(
                                                     modifier = Modifier.size(16.dp),
                                                     contentAlignment = Alignment.Center
                                                 ) {
                                                     Box(
                                                         modifier = Modifier
                                                             .size(15.dp, 10.dp)
                                                             .border(1.2.dp, colors.primary, RoundedCornerShape(1.5.dp))
                                                     )
                                                     Box(
                                                         modifier = Modifier
                                                             .size(5.5.dp)
                                                             .border(1.2.dp, colors.primary, CircleShape)
                                                     )
                                                     Box(
                                                         modifier = Modifier
                                                             .size(4.dp, 1.2.dp)
                                                             .align(Alignment.TopCenter)
                                                             .background(colors.primary, RoundedCornerShape(topStart = 0.5.dp, topEnd = 0.5.dp))
                                                     )
                                                 }
                                                 Spacer(modifier = Modifier.width(4.dp))
                                                 Text(text = if (isBn) "ক্যামেরা ছবি" else "Camera Profile", fontSize = 11.sp)
                                             }
                                         }

                                        val isCustomOwner = !listOf(
                                            "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=200&q=80",
                                            "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=200&q=80",
                                            "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=200&q=80",
                                            "https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?auto=format&fit=crop&w=200&q=80"
                                        ).contains(newOwnerPicPreset) && !newOwnerPicPreset.isBlank()

                                        if (isCustomOwner) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(colors.primary.copy(alpha = 0.1f))
                                                    .padding(4.dp)
                                            ) {
                                                AsyncImage(
                                                    model = rememberImageModel(newOwnerPicPreset),
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(30.dp)
                                                        .clip(CircleShape),
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = if (isBn) "গ্যালারি ছবি সফলভাবে যুক্ত হয়েছে!" else "Gallery photo added!",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (newShopNameInput.isBlank() || newOwnerNameInput.isBlank() || newPhoneInput.isBlank()) {
                                        Toast.makeText(context, if (isBn) "দয়া করে সব ঘর পূরণ করুন" else "Please complete all inputs", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    viewModel.addNewShop(
                                        shopName = newShopNameInput,
                                        ownerName = newOwnerNameInput,
                                        phone = newPhoneInput,
                                        shopPic = newShopPicPreset,
                                        profilePic = newOwnerPicPreset
                                    )
                                    showAddShopInDrawer = false
                                    newShopNameInput = ""
                                    newOwnerNameInput = ""
                                    newPhoneInput = ""
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(if (isBn) "দোকান যুক্ত করুন" else "Add Shop")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showAddShopInDrawer = false }) {
                                Text(if (isBn) "বাতিল" else "Cancel")
                            }
                        },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MenuDrawerListItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
