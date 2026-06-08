package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
        val viewModelFactory = AppViewModelFactory(repository)
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

                // Password change dialog state
                var showChangePinInDrawer by remember { mutableStateOf(false) }
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
                    uri?.let { newShopPicPreset = it.toString() }
                }

                val newOwnerPicLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri ->
                    uri?.let { newOwnerPicPreset = it.toString() }
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
                                    label = { Text(if (isBn) "অন্তর হিসাব" else "Antar Hisab", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
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
                            else -> AuthScreen(viewModel)
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
                                            IconButton(onClick = { viewModel.toggleRightMenuDrawer(false) }) {
                                                Icon(Icons.Default.Close, null)
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
                                                        model = user.profilePicture ?: "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=200&q=80",
                                                        contentDescription = null,
                                                        modifier = Modifier
                                                            .size(54.dp)
                                                            .clip(CircleShape)
                                                            .border(2.dp, colors.primary, CircleShape)
                                                    )
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column {
                                                        Text(
                                                            text = user.ownerName ?: "",
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            fontWeight = FontWeight.Bold,
                                                            color = colors.onSurface
                                                        )
                                                        Text(
                                                            text = user.shopName ?: "",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = colors.primary,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                        Text(
                                                            text = user.phone,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = colors.onSurface.copy(alpha = 0.6f)
                                                        )
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
                                                    model = shopUser.shopPicture ?: "https://images.unsplash.com/photo-1578916171728-46686eac8d58?auto=format&fit=crop&w=300&q=80",
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = shopUser.shopName ?: "",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = colors.onSurface
                                                    )
                                                    Text(
                                                        text = shopUser.ownerName ?: "",
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

                                        // Share link
                                        MenuDrawerListItem(
                                            icon = Icons.Default.Share,
                                            title = if (isBn) "আমার অ্যাপের রেফারেল লিঙ্ক" else "App Referral Invite Link",
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(
                                                        Intent.EXTRA_TEXT,
                                                        if (isBn) "অন্তর হিসাব অ্যাপটি ব্যবহার করে আপনার দোকানের হিসাব রাখুন সহজেই! ডাউনলোড লিঙ্ক: http://example.com/antarhisab_refer"
                                                        else "Keep track of your shop books easily with Antar Hisab App! Download link: http://example.com/antarhisab_refer"
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
                                            Text(
                                                text = if (isBn) "ভার্সন - ১.০" else "Version - 1.0",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
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
                                        OutlinedButton(
                                            onClick = { newShopPicLauncher.launch("image/*") },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = if (isBn) "গ্যালারি থেকে নিজের ছবি দিন" else "Choose Main Gallery Image", fontSize = 11.sp)
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
                                                    model = newShopPicPreset,
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
                                        OutlinedButton(
                                            onClick = { newOwnerPicLauncher.launch("image/*") },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = if (isBn) "গ্যালারি থেকে নিজের ছবি দিন" else "Choose Owner Profile Picture", fontSize = 11.sp)
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
                                                    model = newOwnerPicPreset,
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
