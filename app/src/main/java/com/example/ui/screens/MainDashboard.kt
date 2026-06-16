package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import com.example.viewmodel.AppViewModel
import com.example.data.TransactionRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.Canvas
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap

@Composable
fun MainDashboard(viewModel: AppViewModel) {
    val isBn by viewModel.isBengali.collectAsState()
    val isDark by viewModel.isDarkMode.collectAsState()
    val user by viewModel.currentUser.collectAsState()

    // PIN change dialogue variables
    var showChangePinDialog by remember { mutableStateOf(false) }
    var currentOldPinInput by remember { mutableStateOf("") }
    var currentNewPinInput by remember { mutableStateOf("") }
    var currentConfirmPinInput by remember { mutableStateOf("") }

    // Profile Update Dialogue Variables
    var showProfileSettingsDialog by remember { mutableStateOf(false) }
    var editShopName by remember(user) { mutableStateOf(user?.shopName ?: "") }
    var editOwnerName by remember(user) { mutableStateOf(user?.ownerName ?: "") }
    var editPhone by remember(user) { mutableStateOf(user?.phone ?: "") }
    var editEmail by remember(user) { mutableStateOf(user?.email ?: "") }
    var editPin by remember(user) { mutableStateOf(user?.passwordHash ?: "") }
    var editShopPicture by remember(user) { mutableStateOf(user?.shopPicture ?: "") }
    var editOwnerPicture by remember(user) { mutableStateOf(user?.profilePicture ?: "") }

    var editOwnershipType by remember(user) {
        val nameStr = user?.ownerName ?: ""
        mutableStateOf(if (nameStr.trim().startsWith("[")) "joint" else "single")
    }
    
    val initialOwners = remember(user) {
        com.example.data.OwnerParser.deserialize(user?.ownerName, user?.phone ?: "", user?.email ?: "")
    }

    var editJointCount by remember(initialOwners) {
        mutableStateOf(if (initialOwners.size >= 3) 3 else 2)
    }

    var editJointName1 by remember(initialOwners) { mutableStateOf(initialOwners.getOrNull(0)?.name ?: "") }
    var editJointPhone1 by remember(initialOwners) { mutableStateOf(initialOwners.getOrNull(0)?.phone ?: "") }
    var editJointEmail1 by remember(initialOwners) { mutableStateOf(initialOwners.getOrNull(0)?.email ?: "") }

    var editJointName2 by remember(initialOwners) { mutableStateOf(initialOwners.getOrNull(1)?.name ?: "") }
    var editJointPhone2 by remember(initialOwners) { mutableStateOf(initialOwners.getOrNull(1)?.phone ?: "") }
    var editJointEmail2 by remember(initialOwners) { mutableStateOf(initialOwners.getOrNull(1)?.email ?: "") }

    var editJointName3 by remember(initialOwners) { mutableStateOf(initialOwners.getOrNull(2)?.name ?: "") }
    var editJointPhone3 by remember(initialOwners) { mutableStateOf(initialOwners.getOrNull(2)?.phone ?: "") }
    var editJointEmail3 by remember(initialOwners) { mutableStateOf(initialOwners.getOrNull(2)?.email ?: "") }


    val context = androidx.compose.ui.platform.LocalContext.current

    val editShopLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { 
            viewModel.uriToBase64(context, it)?.let { base64 ->
                editShopPicture = base64
            }
        }
    }

    val editOwnerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { 
            viewModel.uriToBase64(context, it)?.let { base64 ->
                editOwnerPicture = base64
            }
        }
    }

    val editShopCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            viewModel.bitmapToBase64(bitmap)?.let { base64 ->
                editShopPicture = base64
            }
        }
    }

    val editOwnerCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            viewModel.bitmapToBase64(bitmap)?.let { base64 ->
                editOwnerPicture = base64
            }
        }
    }

    // Financial Metrics Flows
    LaunchedEffect(showProfileSettingsDialog, user) {
        if (showProfileSettingsDialog && user != null) {
            editShopName = user?.getLocalizedShopName(isBn) ?: ""
            editOwnerName = user?.getLocalizedOwnerName(isBn) ?: ""
            editPhone = user?.phone ?: ""
            editEmail = user?.email ?: ""
            editPin = user?.passwordHash ?: ""
            editShopPicture = user?.shopPicture ?: ""
            editOwnerPicture = user?.profilePicture ?: ""
            
            val currentOwners = com.example.data.OwnerParser.deserialize(user?.getLocalizedOwnerName(isBn), user?.phone ?: "", user?.email ?: "")
            editOwnershipType = if (user?.ownerName?.trim()?.startsWith("[") == true) "joint" else "single"
            editJointCount = if (currentOwners.size >= 3) 3 else 2
            
            editJointName1 = currentOwners.getOrNull(0)?.name ?: ""
            editJointPhone1 = currentOwners.getOrNull(0)?.phone ?: ""
            editJointEmail1 = currentOwners.getOrNull(0)?.email ?: ""
            
            editJointName2 = currentOwners.getOrNull(1)?.name ?: ""
            editJointPhone2 = currentOwners.getOrNull(1)?.phone ?: ""
            editJointEmail2 = currentOwners.getOrNull(1)?.email ?: ""
            
            editJointName3 = currentOwners.getOrNull(2)?.name ?: ""
            editJointPhone3 = currentOwners.getOrNull(2)?.phone ?: ""
            editJointEmail3 = currentOwners.getOrNull(2)?.email ?: ""
        }
    }

    val itemsFlow by viewModel.stockItems.collectAsState()
    val customersFlow by viewModel.customers.collectAsState()
    val dealersFlow by viewModel.dealers.collectAsState()
    val transactionsFlow by viewModel.transactions.collectAsState()
    val isSyncing by viewModel.isCloudSyncing.collectAsState()

    val colors = MaterialTheme.colorScheme

    // Security Dialogue Card
    if (showChangePinDialog) {
        AlertDialog(
            onDismissRequest = { showChangePinDialog = false },
            title = {
                Text(
                    text = Translator.get("security_settings", isBn),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isBn) "নিরাপদ লেনদেনের জন্য ওল্ড পিন, নিউ পিন এবং কনফার্ম পিন সরবরাহ করে আপনার পিন কোড পরিবর্তন করুন।" else "Provide old PIN, new PIN, and confirmation secure PIN to update your security settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurface.copy(alpha = 0.6f)
                    )
                    
                    OutlinedTextField(
                        value = currentOldPinInput,
                        onValueChange = { if (it.length <= 6) currentOldPinInput = it },
                        label = { Text(Translator.get("old_pin_label", isBn)) },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = currentNewPinInput,
                        onValueChange = { if (it.length <= 6) currentNewPinInput = it },
                        label = { Text(Translator.get("new_pin_label", isBn)) },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = currentConfirmPinInput,
                        onValueChange = { if (it.length <= 6) currentConfirmPinInput = it },
                        label = { Text(Translator.get("confirm_pin_label", isBn)) },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.changeUserPassword(
                            currentOldPinInput,
                            currentNewPinInput,
                            currentConfirmPinInput
                        )
                        showChangePinDialog = false
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = Translator.get("change_pin_btn", isBn),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showChangePinDialog = false
                    }
                ) {
                    Text(text = if (isBn) "বাতিল" else "Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = colors.surface
        )
    }

    // Profile settings edit Dialogue Card
    if (showProfileSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showProfileSettingsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Translator.get("edit_profile_dialog", isBn),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                }
            },
            text = {
                val dialogScrollState = rememberScrollState()
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(dialogScrollState)
                ) {
                    // Quick Visual Previews
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Owner avatar preview
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isBn) "মালিকের ছবি" else "Owner Preview",
                                fontSize = 11.sp,
                                color = colors.onSurface.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            if (!editOwnerPicture.isNullOrBlank()) {
                                AsyncImage(
                                    model = rememberImageModel(editOwnerPicture),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, colors.primary, CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(colors.primary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = colors.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }

                        // Shop cover preview
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isBn) "দোকানের ছবি" else "Shop Preview",
                                fontSize = 11.sp,
                                color = colors.onSurface.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            if (!editShopPicture.isNullOrBlank()) {
                                AsyncImage(
                                    model = rememberImageModel(editShopPicture),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(2.dp, colors.primary, RoundedCornerShape(8.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.primary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Home,
                                        contentDescription = null,
                                        tint = colors.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Shop Name Input
                    OutlinedTextField(
                        value = editShopName,
                        onValueChange = { editShopName = it },
                        label = { Text(Translator.get("shop_name", isBn)) },
                        leadingIcon = { Icon(Icons.Default.Home, null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
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

                        // Render Joint Owner 1 Card info
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colors.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.primary.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = if (isBn) "প্রথম মালিকের তথ্য (Owner 1):" else "Owner 1 Details:",
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

                        // Render Joint Owner 2 Card info
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colors.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.primary.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = if (isBn) "দ্বিতীয় মালিকের তথ্য (Owner 2):" else "Owner 2 Details:",
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
                            // Render Joint Owner 3 Card info
                            Card(
                                colors = CardDefaults.cardColors(containerColor = colors.surface),
                                border = androidx.compose.foundation.BorderStroke(1.dp, colors.primary.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = if (isBn) "তৃতীয় মালিকের তথ্য (Owner 3):" else "Owner 3 Details:",
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
                    } else {
                        // Owner Name Input
                        OutlinedTextField(
                            value = editOwnerName,
                            onValueChange = { editOwnerName = it },
                            label = { Text(Translator.get("owner_name", isBn)) },
                            leadingIcon = { Icon(Icons.Default.Person, null) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Phone Number Input
                        OutlinedTextField(
                            value = editPhone,
                            onValueChange = { editPhone = it },
                            label = { Text(Translator.get("phone_number", isBn)) },
                            leadingIcon = { Icon(Icons.Default.Phone, null) },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Primary Email Input (Always visible and editable for both Single and Joint Owners!)
                    OutlinedTextField(
                        value = editEmail,
                        onValueChange = { editEmail = it },
                        label = { Text(Translator.get("login_email", isBn)) },
                        leadingIcon = { Icon(Icons.Default.Email, null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Security PIN Input (Always visible and editable for both Single and Joint Owners!)
                    OutlinedTextField(
                        value = editPin,
                        onValueChange = { if (it.length <= 6) editPin = it },
                        label = { Text(Translator.get("register_pin", isBn)) },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Preset Shop Picture picker inside Profile Details (Zero URL Input!)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = if (isBn) "দোকানের ছবি নির্বাচন করুন" else "Select Shop Front Style",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val shopPresets = listOf(
                                    "https://images.unsplash.com/photo-1578916171728-46686eac8d58?auto=format&fit=crop&w=300&q=80" to (if (isBn) "মুদি" else "Grocery"),
                                    "https://images.unsplash.com/photo-1604719312566-8912e9227c6a?auto=format&fit=crop&w=300&q=80" to (if (isBn) "সুপার" else "Mart"),
                                    "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?auto=format&fit=crop&w=300&q=80" to (if (isBn) "টেলিকম" else "Telecom"),
                                    "https://images.unsplash.com/photo-1441986300917-64674bd600d8?auto=format&fit=crop&w=300&q=80" to (if (isBn) "অন্যান্য" else "Other")
                                )

                                shopPresets.forEach { (url, label) ->
                                    val isSelected = editShopPicture == url
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) colors.primary.copy(alpha = 0.2f) else Color.Transparent)
                                            .border(
                                                width = if (isSelected) 2.dp else 0.dp,
                                                color = if (isSelected) colors.primary else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { editShopPicture = url }
                                            .padding(2.dp)
                                    ) {
                                        AsyncImage(
                                            model = url,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = label,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) colors.primary else colors.onSurface
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = { editShopLauncher.launch("image/*") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = if (isBn) "গ্যালারি ছবি" else "Gallery Picture", fontSize = 11.sp)
                                }

                                OutlinedButton(
                                    onClick = { editShopCameraLauncher.launch(null) },
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
                            ).contains(editShopPicture) && !editShopPicture.isNullOrBlank()

                            if (isCustomShop) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(colors.primary.copy(alpha = 0.1f))
                                        .padding(6.dp)
                                ) {
                                    AsyncImage(
                                        model = rememberImageModel(editShopPicture),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(35.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isBn) "গ্যালারি ইমেজ সফলভাবে যুক্ত হয়েছে!" else "Gallery image successfully added!",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.primary
                                    )
                                }
                            }
                        }
                    }

                    // Preset Owner Picture picker inside Profile Details (Zero URL Input!)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(12.dp),
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
                                val ownerPresets = listOf(
                                    "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=200&q=80" to (if (isBn) "মালিক ১" else "Owner 1"),
                                    "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=200&q=80" to (if (isBn) "মালিক ২" else "Owner 2"),
                                    "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=200&q=80" to (if (isBn) "মালিক ৩" else "Owner 3"),
                                    "https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?auto=format&fit=crop&w=200&q=80" to (if (isBn) "মালিক ৪" else "Owner 4")
                                )

                                ownerPresets.forEach { (url, label) ->
                                    val isSelected = editOwnerPicture == url
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) colors.primary.copy(alpha = 0.2f) else Color.Transparent)
                                            .border(
                                                width = if (isSelected) 2.dp else 0.dp,
                                                color = if (isSelected) colors.primary else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { editOwnerPicture = url }
                                            .padding(2.dp)
                                    ) {
                                        AsyncImage(
                                            model = url,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = label,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) colors.primary else colors.onSurface
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = { editOwnerLauncher.launch("image/*") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = if (isBn) "গ্যালারি ছবি" else "Gallery Profile", fontSize = 11.sp)
                                }

                                OutlinedButton(
                                    onClick = { editOwnerCameraLauncher.launch(null) },
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
                            ).contains(editOwnerPicture) && !editOwnerPicture.isNullOrBlank()

                            if (isCustomOwner) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(colors.primary.copy(alpha = 0.1f))
                                        .padding(6.dp)
                                ) {
                                    AsyncImage(
                                        model = rememberImageModel(editOwnerPicture),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(35.dp)
                                            .clip(CircleShape),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isBn) "গ্যালারি ইমেজ সফলভাবে যুক্ত হয়েছে!" else "Gallery image successfully added!",
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
                        val finalOwnerName: String
                        val finalPhone: String

                        if (editOwnershipType == "single") {
                            finalOwnerName = editOwnerName
                            finalPhone = editPhone
                        } else {
                            if (editJointName1.isBlank() || editJointPhone1.isBlank() || editJointName2.isBlank() || editJointPhone2.isBlank() || (editJointCount == 3 && (editJointName3.isBlank() || editJointPhone3.isBlank()))) {
                                viewModel.showToast(if (isBn) "দয়া করে অংশীদারদের নাম এবং মোবাইল নাম্বার সবগুলো পূরণ করুন" else "Please fill name and mobile numbers of all partners")
                                return@Button
                            }

                            val list = mutableListOf<com.example.data.OwnerInfo>()
                            list.add(com.example.data.OwnerInfo(editJointName1.trim(), editJointPhone1.trim(), editJointEmail1.trim()))
                            list.add(com.example.data.OwnerInfo(editJointName2.trim(), editJointPhone2.trim(), editJointEmail2.trim()))
                            if (editJointCount == 3) {
                                list.add(com.example.data.OwnerInfo(editJointName3.trim(), editJointPhone3.trim(), editJointEmail3.trim()))
                            }
                            finalOwnerName = com.example.data.OwnerParser.serialize(list)
                            finalPhone = list.map { it.phone }.filter { it.isNotBlank() }.joinToString(", ")
                        }

                        viewModel.updateUserProfile(
                            shopName = editShopName,
                            ownerName = finalOwnerName,
                            email = editEmail.trim(),
                            phone = finalPhone,
                            pin = editPin,
                            profilePic = editOwnerPicture.ifBlank { null },
                            shopPic = editShopPicture.ifBlank { null }
                        )
                        // showProfileSettingsDialog = false // stay on the dialog as requested to review updated info
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = Translator.get("update_btn", isBn),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showProfileSettingsDialog = false
                    }
                ) {
                    Text(text = if (isBn) "বন্ধ করুন" else "Close")
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = colors.surface
        )
    }

    // Calculations
    val totalSales = transactionsFlow.filter { it.type == "SALE" }.sumOf { it.amount }
    val totalExpenses = transactionsFlow.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    val netProfit = transactionsFlow.sumOf { it.profit }
    val totalDues = customersFlow.sumOf { it.totalDue }
    val totalOwed = dealersFlow.sumOf { it.totalOwed }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Interactive Profile Area
                    Row(
                        modifier = Modifier
                            .weight(1.3f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showProfileSettingsDialog = true
                            }
                            .padding(4.dp)
                            .testTag("profile_header_block"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Profile Image / Icon
                        val ownerPic = user?.profilePicture
                        if (!ownerPic.isNullOrBlank()) {
                            AsyncImage(
                                model = rememberImageModel(ownerPic),
                                contentDescription = "Profile Pic",
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, colors.primary, CircleShape)
                            )
                        } else {
                            // Default beautiful placeholder
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(colors.primary.copy(alpha = 0.15f))
                                    .border(1.5.dp, colors.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (com.example.data.OwnerParser.getFirstOwnerName(user?.ownerName, "U").take(1).ifBlank { "U" }).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primary,
                                    fontSize = 16.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column {
                            val shopTitle = user?.getLocalizedShopName(isBn) ?: Translator.get("app_title", isBn)
                            val parsedOwners = com.example.data.OwnerParser.deserialize(user?.getLocalizedOwnerName(isBn), user?.phone ?: "", user?.email ?: "")
                            val ownerTitle = parsedOwners.map { it.name }.filter { it.isNotBlank() }.joinToString(" + ").ifBlank { if (isBn) "দোকানের মালিক" else "Shop Owner" }
                            Text(
                                text = ownerTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = shopTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.primary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Headers action
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Cloud synchronizer
                        IconButton(
                            onClick = { viewModel.triggerCloudSync() },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.primary.copy(alpha = 0.1f))
                                .testTag("cloud_sync_icon")
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

                        Spacer(modifier = Modifier.width(6.dp))

                        // Quit
                        IconButton(
                            onClick = { viewModel.logout() },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.errorContainer.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExitToApp,
                                contentDescription = null,
                                tint = colors.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))

                // Beautiful Shop Picture Header Banner Card
                val shopPic = user?.shopPicture
                if (!shopPic.isNullOrBlank()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = rememberImageModel(shopPic),
                                contentDescription = "Shop Front Banner",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                            // A subtle premium gradient overlay to make text pop
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.65f)
                                            )
                                        )
                                    )
                            )
                            // Text on banner
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = user?.getLocalizedShopName(isBn) ?: "",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = user?.phone ?: "",
                                    color = Color.White.copy(alpha = 0.82f),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Premium Financial Gradient Summary Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        colors.primary.copy(alpha = 0.05f),
                                        colors.secondary.copy(alpha = 0.09f)
                                    )
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = Translator.get("profit_summary", isBn),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = colors.onBackground.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = "৳ ${String.format(Locale.getDefault(), "%,.1f", netProfit)}",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (netProfit >= 0) colors.primary else colors.error,
                                        modifier = Modifier.testTag("net_profit_text")
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(
                                            if (netProfit >= 0) colors.primary.copy(alpha = 0.15f)
                                            else colors.error.copy(alpha = 0.15f)
                                        )
                                        .padding(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (netProfit >= 0) Icons.Default.Check else Icons.Default.Clear,
                                        contentDescription = null,
                                        tint = if (netProfit >= 0) colors.primary else colors.error,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Divider(modifier = Modifier.padding(vertical = 14.dp), color = colors.onBackground.copy(alpha = 0.08f))

                            // Grid summary items
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = Translator.get("sales_summary", isBn),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onBackground.copy(alpha = 0.5f),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "৳ ${String.format(Locale.getDefault(), "%,.1f", totalSales)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.onBackground
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = Translator.get("expense_summary", isBn),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onBackground.copy(alpha = 0.5f),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "৳ ${String.format(Locale.getDefault(), "%,.1f", totalExpenses)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.error
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = Translator.get("due_summary", isBn),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onBackground.copy(alpha = 0.5f),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "৳ ${String.format(Locale.getDefault(), "%,.1f", totalDues)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFC5A000)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = Translator.get("owed_summary", isBn),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onBackground.copy(alpha = 0.5f),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "৳ ${String.format(Locale.getDefault(), "%,.1f", totalOwed)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.secondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))

                // Section title
                Text(
                    text = if (isBn) "বিশেষ কুইক ফিচারসমূহ" else "Quick Business Actions",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // High-precision Grid for 6 major screens of the Store Tally
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        DashboardItemCard(
                            title = Translator.get("stock_manager", isBn),
                            subtitle = if (isBn) "পণ্যের সংখ্যা ও বিক্রয়মূল্য" else "Manage store stock list",
                            icon = Icons.Default.List,
                            cardColor = colors.primary,
                            testTag = "btn_stock_manager",
                            modifier = Modifier.weight(1f)
                        ) {
                            viewModel.navigateTo("STOCK_MANAGER")
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        DashboardItemCard(
                            title = Translator.get("sales_billing", isBn),
                            subtitle = if (isBn) "দৈনিক বিক্রি বা অন্তর হিসাব" else "Daily Sales or ONTAR Hisab",
                            icon = Icons.Default.ShoppingCart,
                            cardColor = colors.secondary,
                            testTag = "btn_sales_billing",
                            modifier = Modifier.weight(1f)
                        ) {
                            viewModel.navigateTo("SALES_BILLING")
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        DashboardItemCard(
                            title = Translator.get("customer_ledger", isBn),
                            subtitle = if (isBn) "কার কাছে কত টাকা বাকি আছে" else "Dues reminders & payments",
                            icon = Icons.Default.AccountCircle,
                            cardColor = Color(0xFFC5A000),
                            testTag = "btn_customer_ledger",
                            modifier = Modifier.weight(1f)
                        ) {
                            viewModel.navigateTo("CUSTOMER_LEDGER")
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        DashboardItemCard(
                            title = Translator.get("dealer_ledger", isBn),
                            subtitle = if (isBn) "ডিলারের পাওনা হিসাব খাতা" else "Dealer lists & payouts",
                            icon = Icons.Default.Home,
                            cardColor = Color(0xFF8E24AA),
                            testTag = "btn_dealer_ledger",
                            modifier = Modifier.weight(1f)
                        ) {
                            viewModel.navigateTo("DEALER_LEDGER")
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        DashboardItemCard(
                            title = Translator.get("reports", isBn),
                            subtitle = if (isBn) "দৈনিক, মাসিক, বার্ষিক হিসেব" else "Sales, Profit & Loss summaries",
                            icon = Icons.Default.DateRange,
                            cardColor = Color(0xFF0288D1),
                            testTag = "btn_reports",
                            modifier = Modifier.weight(1f)
                        ) {
                            viewModel.navigateTo("REPORTS")
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        DashboardItemCard(
                            title = Translator.get("ai_coach", isBn),
                            subtitle = if (isBn) "এআই দোকান পর্যবেক্ষণ ও নির্দেশনা" else "Generative business advisory",
                            icon = Icons.Default.Star,
                            cardColor = colors.primary,
                            testTag = "btn_ai_coach",
                            modifier = Modifier.weight(1f)
                        ) {
                            viewModel.navigateTo("AI_COACH")
                        }
                    }
                }
            }

            // Overall Financial visual dashboard donut chart
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (isBn) "মোট টাকার হিসাব খতিয়ান ও অনুপাত চার্ট" else "Total Financial Proportion Graph",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val overallSales = transactionsFlow.filter { it.type == "SALE" }.sumOf { it.amount }
                val overallExpenses = transactionsFlow.filter { it.type == "EXPENSE" }.sumOf { it.amount }
                val overallDuesCollected = transactionsFlow.filter { it.type == "CUSTOMER_PAYMENT" }.sumOf { it.amount }
                val overallNewDues = transactionsFlow.filter { it.type == "CUSTOMER_DUE" }.sumOf { it.amount }

                FinancialDonutChart(
                    sales = overallSales,
                    expenses = overallExpenses,
                    duesCollected = overallDuesCollected,
                    newDues = overallNewDues,
                    isBn = isBn,
                    colors = colors
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                // Store transactional status history log
                Text(
                    text = if (isBn) "সাম্প্রতিক বিবরণী লগ" else "Recent Activity Log",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (transactionsFlow.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = if (isBn) "এখনও কোনো কর্মকাণ্ড বা বিক্রি করা হয়নি!" else "No transactions recorded yet!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onBackground.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        )
                    }
                }
            }

            // Shows the last max 5 activities
            items(transactionsFlow.take(5)) { tx ->
                ActivityLogItem(tx, isBn, colors, customersFlow, dealersFlow)
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun DashboardItemCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    cardColor: Color,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Card(
        modifier = modifier
            .height(115.dp)
            .clickable { onClick() }
            .testTag(testTag),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(cardColor.copy(alpha = 0.12f))
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = cardColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onBackground.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ActivityLogItem(
    tx: TransactionRecord,
    isBn: Boolean,
    colors: ColorScheme,
    customersList: List<com.example.data.Customer>,
    dealersList: List<com.example.data.Dealer>
) {
    val sdf = remember { SimpleDateFormat("hh:mm a | dd-MMM", Locale.getDefault()) }
    val formattedDate = sdf.format(Date(tx.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                val matchedCustomer = remember(tx.customerId, customersList) {
                    customersList.find { it.id == tx.customerId }
                }
                val matchedDealer = remember(tx.dealerId, dealersList) {
                    dealersList.find { it.id == tx.dealerId }
                }
                val photoUri = matchedCustomer?.photoUri ?: matchedDealer?.photoUri

                if (!photoUri.isNullOrEmpty()) {
                    coil.compose.AsyncImage(
                        model = rememberImageModel(photoUri),
                        contentDescription = null,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                when (tx.type) {
                                    "SALE" -> colors.primary.copy(alpha = 0.1f)
                                    "EXPENSE" -> colors.error.copy(alpha = 0.1f)
                                    "CUSTOMER_PAYMENT" -> Color(0xFF0F9D58).copy(alpha = 0.1f)
                                    else -> colors.onBackground.copy(alpha = 0.05f)
                                }
                            )
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = when (tx.type) {
                                "SALE" -> Icons.Default.Check
                                "EXPENSE" -> Icons.Default.Clear
                                "CUSTOMER_PAYMENT" -> Icons.Default.ShoppingCart
                                else -> Icons.Default.ShoppingCart
                            },
                            contentDescription = null,
                            tint = when (tx.type) {
                                "SALE" -> colors.primary
                                "EXPENSE" -> colors.error
                                "CUSTOMER_PAYMENT" -> Color(0xFF0F9D58)
                                else -> colors.onBackground.copy(alpha = 0.6f)
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = tx.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onBackground.copy(alpha = 0.4f)
                    )
                }
            }

            Text(
                text = "${if (tx.type == "EXPENSE") "-" else "+"} ৳${tx.amount}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (tx.type == "EXPENSE") colors.error else colors.primary
            )
        }
    }
}


