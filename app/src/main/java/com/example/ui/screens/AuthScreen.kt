package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.material.icons.filled.Add
import com.example.viewmodel.AppViewModel

@Composable
fun AuthScreen(viewModel: AppViewModel) {
    val isBn by viewModel.isBengali.collectAsState()
    val authMsg by viewModel.authStateMessage.collectAsState()
    var isRegisterTab by remember { mutableStateOf(false) }

    // Password reset state observables
    val forgetStep by viewModel.forgetPasswordStep.collectAsState()
    val simulateOtp by viewModel.resetOtp.collectAsState()

    // Form inputs
    var shopName by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var pinCode by remember { mutableStateOf("") }
    var shopPicture by remember { mutableStateOf("https://images.unsplash.com/photo-1578916171728-46686eac8d58?auto=format&fit=crop&w=300&q=80") }
    var ownerPicture by remember { mutableStateOf("https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=200&q=80") }

    // Multi-owner states
    var ownershipType by remember { mutableStateOf("single") } // "single" or "joint"
    var jointCount by remember { mutableStateOf(2) } // 2 or 3
    var jointName1 by remember { mutableStateOf("") }
    var jointPhone1 by remember { mutableStateOf("") }
    var jointEmail1 by remember { mutableStateOf("") }
    var jointName2 by remember { mutableStateOf("") }
    var jointPhone2 by remember { mutableStateOf("") }
    var jointEmail2 by remember { mutableStateOf("") }
    var jointName3 by remember { mutableStateOf("") }
    var jointPhone3 by remember { mutableStateOf("") }
    var jointEmail3 by remember { mutableStateOf("") }

    val context = androidx.compose.ui.platform.LocalContext.current

    val shopLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { 
            viewModel.uriToBase64(context, it)?.let { base64 ->
                shopPicture = base64
            }
        }
    }

    val ownerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { 
            viewModel.uriToBase64(context, it)?.let { base64 ->
                ownerPicture = base64
            }
        }
    }

    // Reset Flow inputs
    var resetIdentifier by remember { mutableStateOf("") }
    var otpCodeText by remember { mutableStateOf("") }
    var newResetPin by remember { mutableStateOf("") }

    // Colors
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.primary.copy(alpha = 0.08f),
                        colors.background
                    )
                )
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .verticalScroll(scrollState)
                .navigationBarsPadding()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header language Toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = { viewModel.toggleLanguage() },
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.primary.copy(alpha = 0.1f))
                ) {
                    Text(
                        text = if (isBn) "EN" else "বাং",
                        color = colors.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { viewModel.toggleDarkMode() },
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.primary.copy(alpha = 0.1f))
                ) {
                    Text(
                        text = "🌓",
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App Logo Icon
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier
                    .size(72.dp)
                    .background(colors.primary.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                    .padding(14.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Title
            Text(
                text = Translator.get("app_title", isBn),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = colors.primary,
                textAlign = TextAlign.Center
            )
            Text(
                text = Translator.get("app_subtitle", isBn),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // Auth Dialog Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (forgetStep == 0) {
                        // Custom tab selector row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.onBackground.copy(alpha = 0.05f))
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (!isRegisterTab) colors.primary else Color.Transparent)
                                    .clickable {
                                        isRegisterTab = false
                                        viewModel.clearAuthMessage()
                                    }
                                    .padding(vertical = 10.dp)
                                    .testTag("login_tab_btn"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = Translator.get("login_tab", isBn),
                                    color = if (!isRegisterTab) colors.onPrimary else colors.onBackground,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isRegisterTab) colors.primary else Color.Transparent)
                                    .clickable {
                                        isRegisterTab = true
                                        viewModel.clearAuthMessage()
                                    }
                                    .padding(vertical = 10.dp)
                                    .testTag("register_tab_btn"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = Translator.get("register_tab", isBn),
                                    color = if (isRegisterTab) colors.onPrimary else colors.onBackground,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    } else {
                        // Reset PIN flow Title
                        Text(
                            text = Translator.get("reset_pin_title", isBn),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }

                    // Error Alert block
                    if (authMsg != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colors.errorContainer),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = authMsg ?: "",
                                color = colors.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Dynamically render inputs based on step
                    if (forgetStep == 0) {
                        if (isRegisterTab) {
                            // Shop Name
                            OutlinedTextField(
                                value = shopName,
                                onValueChange = { shopName = it },
                                label = { Text(Translator.get("shop_name", isBn)) },
                                leadingIcon = { Icon(Icons.Default.Home, null) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            )

                            // Ownership Type Selection Row
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
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
                                            .background(if (ownershipType == "single") colors.primary else colors.surfaceVariant.copy(alpha = 0.5f))
                                            .clickable { ownershipType = "single" }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (isBn) "একক মালিক (১)" else "Single Owner (1)",
                                            color = if (ownershipType == "single") colors.onPrimary else colors.onSurface,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (ownershipType == "joint") colors.primary else colors.surfaceVariant.copy(alpha = 0.5f))
                                            .clickable { ownershipType = "joint" }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (isBn) "যৌথ মালিক (>১)" else "Joint Owners (>1)",
                                            color = if (ownershipType == "joint") colors.onPrimary else colors.onSurface,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            if (ownershipType == "joint") {
                                // Joint Owner count selector
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
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
                                                .background(if (jointCount == 2) colors.secondary else colors.surfaceVariant.copy(alpha = 0.3f))
                                                .clickable { jointCount = 2 }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (isBn) "২ জন অংশীদার" else "2 Partners",
                                                color = if (jointCount == 2) colors.onSecondary else colors.onSurface,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (jointCount == 3) colors.secondary else colors.surfaceVariant.copy(alpha = 0.3f))
                                                .clickable { jointCount = 3 }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (isBn) "৩ জন অংশীদার" else "3 Partners",
                                                color = if (jointCount == 3) colors.onSecondary else colors.onSurface,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }

                                // Render Joint Owner 1 Card info
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                                    border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
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
                                            value = jointName1,
                                            onValueChange = { jointName1 = it },
                                            label = { Text(if (isBn) "পূর্ণ নাম" else "Full Name") },
                                            leadingIcon = { Icon(Icons.Default.Person, null, modifier = Modifier.size(16.dp)) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                        )
                                        OutlinedTextField(
                                            value = jointPhone1,
                                            onValueChange = { jointPhone1 = it },
                                            label = { Text(if (isBn) "মোবাইল নাম্বার" else "Mobile Number") },
                                            leadingIcon = { Icon(Icons.Default.Phone, null, modifier = Modifier.size(16.dp)) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                        )
                                        OutlinedTextField(
                                            value = jointEmail1,
                                            onValueChange = { jointEmail1 = it },
                                            label = { Text(if (isBn) "ইমেইল অ্যাড্রেস" else "Email Address") },
                                            leadingIcon = { Icon(Icons.Default.Email, null, modifier = Modifier.size(16.dp)) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }

                                // Render Joint Owner 2 Card info
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                                    border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
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
                                            value = jointName2,
                                            onValueChange = { jointName2 = it },
                                            label = { Text(if (isBn) "পূর্ণ নাম" else "Full Name") },
                                            leadingIcon = { Icon(Icons.Default.Person, null, modifier = Modifier.size(16.dp)) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                        )
                                        OutlinedTextField(
                                            value = jointPhone2,
                                            onValueChange = { jointPhone2 = it },
                                            label = { Text(if (isBn) "মোবাইল নাম্বার" else "Mobile Number") },
                                            leadingIcon = { Icon(Icons.Default.Phone, null, modifier = Modifier.size(16.dp)) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                        )
                                        OutlinedTextField(
                                            value = jointEmail2,
                                            onValueChange = { jointEmail2 = it },
                                            label = { Text(if (isBn) "ইমেইল অ্যাড্রেস" else "Email Address") },
                                            leadingIcon = { Icon(Icons.Default.Email, null, modifier = Modifier.size(16.dp)) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }

                                if (jointCount == 3) {
                                    // Render Joint Owner 3 Card info
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                                        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.3f)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
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
                                                value = jointName3,
                                                onValueChange = { jointName3 = it },
                                                label = { Text(if (isBn) "পূর্ণ নাম" else "Full Name") },
                                                leadingIcon = { Icon(Icons.Default.Person, null, modifier = Modifier.size(16.dp)) },
                                                singleLine = true,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                            )
                                            OutlinedTextField(
                                                value = jointPhone3,
                                                onValueChange = { jointPhone3 = it },
                                                label = { Text(if (isBn) "মোবাইল নাম্বার" else "Mobile Number") },
                                                leadingIcon = { Icon(Icons.Default.Phone, null, modifier = Modifier.size(16.dp)) },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                            )
                                            OutlinedTextField(
                                                value = jointEmail3,
                                                onValueChange = { jointEmail3 = it },
                                                label = { Text(if (isBn) "ইমেইল অ্যাড্রেস" else "Email Address") },
                                                leadingIcon = { Icon(Icons.Default.Email, null, modifier = Modifier.size(16.dp)) },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Shop Owner Name
                                OutlinedTextField(
                                    value = ownerName,
                                    onValueChange = { ownerName = it },
                                    label = { Text(Translator.get("owner_name", isBn)) },
                                    leadingIcon = { Icon(Icons.Default.Person, null) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                )

                                // Mobile number
                                OutlinedTextField(
                                    value = phone,
                                    onValueChange = { phone = it },
                                    label = { Text(Translator.get("phone_number", isBn)) },
                                    leadingIcon = { Icon(Icons.Default.Phone, null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                )
                            }


                            // Preset Shop Picture picker (Zero URL Input!)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant.copy(alpha = 0.35f)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = if (isBn) "দোকানের ছবি নির্বাচন করুন" else "Select Shop Front Style",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.primary,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        val shopPresets = listOf(
                                            "https://images.unsplash.com/photo-1578916171728-46686eac8d58?auto=format&fit=crop&w=300&q=80" to (if (isBn) "মুদি দোকান" else "Grocery"),
                                            "https://images.unsplash.com/photo-1604719312566-8912e9227c6a?auto=format&fit=crop&w=300&q=80" to (if (isBn) "সুপারশপ" else "Mart"),
                                            "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?auto=format&fit=crop&w=300&q=80" to (if (isBn) "টেলিকম" else "Telecom"),
                                            "https://images.unsplash.com/photo-1441986300917-64674bd600d8?auto=format&fit=crop&w=300&q=80" to (if (isBn) "অন্যান্য" else "Other")
                                        )

                                        shopPresets.forEach { (url, label) ->
                                            val isSelected = shopPicture == url
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
                                                    .clickable { shopPicture = url }
                                                    .padding(4.dp)
                                            ) {
                                                AsyncImage(
                                                    model = url,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(45.dp)
                                                        .clip(RoundedCornerShape(6.dp)),
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
                                    OutlinedButton(
                                        onClick = { shopLauncher.launch("image/*") },
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
                                    ).contains(shopPicture) && shopPicture.isNotBlank()

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
                                                model = shopPicture,
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

                            // Preset Owner Profile Picture picker (Zero URL Input!)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant.copy(alpha = 0.35f)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = if (isBn) "দোকানদারের প্রোফাইল ছবি" else "Select Shopkeeper Profile",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.primary,
                                        modifier = Modifier.padding(bottom = 8.dp)
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
                                            val isSelected = ownerPicture == url
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
                                                    .clickable { ownerPicture = url }
                                                    .padding(4.dp)
                                            ) {
                                                AsyncImage(
                                                    model = url,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(45.dp)
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
                                    OutlinedButton(
                                        onClick = { ownerLauncher.launch("image/*") },
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
                                    ).contains(ownerPicture) && ownerPicture.isNotBlank()

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
                                                model = ownerPicture,
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

                        // Email Address
                        if (!isRegisterTab || ownershipType == "single") {
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text(Translator.get(if (isRegisterTab) "register_email" else "login_email", isBn)) },
                                leadingIcon = { Icon(Icons.Default.Email, null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            )
                        }

                        // 4-Digit secure PIN Code
                        OutlinedTextField(
                            value = pinCode,
                            onValueChange = { if (it.length <= 6) pinCode = it },
                            label = { Text(Translator.get(if (isRegisterTab) "register_pin" else "login_pin", isBn)) },
                            leadingIcon = { Icon(Icons.Default.Lock, null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        )

                        // Forgot PIN Option
                        if (!isRegisterTab) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Text(
                                    text = Translator.get("forget_pin", isBn),
                                    color = colors.primary,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .clickable {
                                            viewModel.clearAuthMessage()
                                            viewModel.setForgetPasswordStep(1)
                                        }
                                        .padding(4.dp)
                                        .testTag("forgot_pin_link")
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Unified Submit Button
                        Button(
                            onClick = {
                                if (isRegisterTab) {
                                    val finalOwnerName: String
                                    val finalPhone: String
                                    val finalEmail: String
                                    
                                    if (ownershipType == "single") {
                                        finalOwnerName = ownerName
                                        finalPhone = phone
                                        finalEmail = email.trim()
                                    } else {
                                        // Validation: Check if joint owner fields are empty
                                        if (jointName1.isBlank() || jointPhone1.isBlank() || jointName2.isBlank() || jointPhone2.isBlank() || (jointCount == 3 && (jointName3.isBlank() || jointPhone3.isBlank()))) {
                                            viewModel.showToast(if (isBn) "দয়া করে অংশীদারদের নাম এবং মোবাইল নাম্বার সবগুলো পূরণ করুন" else "Please fill name and mobile numbers of all partners")
                                            return@Button
                                        }
                                        
                                        val list = mutableListOf<com.example.data.OwnerInfo>()
                                        list.add(com.example.data.OwnerInfo(jointName1.trim(), jointPhone1.trim(), jointEmail1.trim()))
                                        list.add(com.example.data.OwnerInfo(jointName2.trim(), jointPhone2.trim(), jointEmail2.trim()))
                                        if (jointCount == 3) {
                                            list.add(com.example.data.OwnerInfo(jointName3.trim(), jointPhone3.trim(), jointEmail3.trim()))
                                        }
                                        finalOwnerName = com.example.data.OwnerParser.serialize(list)
                                        finalPhone = list.map { it.phone }.filter { it.isNotBlank() }.joinToString(", ")
                                        finalEmail = if (jointEmail1.trim().isNotBlank()) jointEmail1.trim() else "joint_${jointPhone1.trim()}@shop.com"
                                    }

                                    viewModel.registerNewUser(
                                        shopName = shopName,
                                        ownerName = finalOwnerName,
                                        email = finalEmail,
                                        phone = finalPhone,
                                        pin = pinCode,
                                        profilePic = ownerPicture.ifBlank { null },
                                        shopPic = shopPicture.ifBlank { null }
                                    )
                                } else {
                                    viewModel.loginUser(email, pinCode)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("auth_submit_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                        ) {
                            Text(
                                text = if (isRegisterTab) Translator.get("register_btn", isBn) else Translator.get("login_btn", isBn),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (forgetStep == 1) {
                        // Enter email/phone to receive simulation code
                        Text(
                            text = if (isBn) "পিন বা পাসওয়ার্ড হারিয়েছেন? পুনরায় উদ্ধার করতে আপনার নিবন্ধিত ইমেইল অথবা মোবাইল নাম্বারটি দিন।" else "Lost your PIN identifier? Securely input your registered account email or mobile phone to proceed.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onBackground.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 16.dp),
                            textAlign = TextAlign.Start
                        )

                        OutlinedTextField(
                            value = resetIdentifier,
                            onValueChange = { resetIdentifier = it },
                            label = { Text(Translator.get("login_email", isBn)) },
                            leadingIcon = { Icon(Icons.Default.Email, null) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 20.dp)
                        )

                        Button(
                            onClick = { viewModel.sendResetOtp(resetIdentifier) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("send_otp_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (isBn) "ওটিপি কোড পাঠান" else "Request Reset OTP",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        TextButton(
                            onClick = { viewModel.setForgetPasswordStep(0) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isBn) "লগইন এ ফিরে যান" else "Return to Login Screen",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else if (forgetStep == 2) {
                        // Enter OTP and new secure PIN
                        
                        // Simulations OTP visual block
                        val resetUser = viewModel.resetUser.collectAsState().value
                        if (resetUser != null) {
                            val owners = com.example.data.OwnerParser.deserialize(resetUser.ownerName, resetUser.phone, resetUser.email)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = colors.primaryContainer),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    ) {
                                        Text(text = "📲  ", fontSize = 20.sp)
                                        Text(
                                            text = if (isBn) "একক/অংশীদারী অ্যাকাউন্ট ওটিপি ডিস্ট্রিবিউশন" else "Unified Account OTP Distribution",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.primary
                                        )
                                    }
                                    
                                    simulateOtp?.let { otp ->
                                        Text(
                                            text = if (isBn) 
                                                "নিরাপদ ওটিপি কোড: $otp" 
                                            else 
                                                "Secure Recovery OTP: $otp",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 16.sp,
                                            color = colors.onPrimaryContainer,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                    }

                                    Text(
                                        text = if (isBn) 
                                            "এই একই ওটিপি নিরাপত্তা স্বার্থে সকল অংশীদারদের ফোন ও ইমেইলে পাঠানো হয়েছে:" 
                                        else 
                                            "For high security, the same OTP has been sent to all registered contacts:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onPrimaryContainer.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    
                                    owners.forEachIndexed { i, owner ->
                                        val oName = owner.name.ifBlank { if (isBn) "মালিক ${i+1}" else "Owner ${i+1}" }
                                        val oPhone = owner.phone.ifBlank { if (isBn) "সংরক্ষিত নেই" else "N/A" }
                                        val oEmail = owner.email.ifBlank { if (isBn) "সংরক্ষিত নেই" else "N/A" }
                                        
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "• $oName",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = colors.onPrimaryContainer
                                            )
                                            Text(
                                                text = if (isBn) "  মোবাইল: $oPhone | ইমেইল: $oEmail" else "  Phone: $oPhone | Email: $oEmail",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onPrimaryContainer.copy(alpha = 0.7f),
                                                modifier = Modifier.padding(start = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = otpCodeText,
                            onValueChange = { if (it.length <= 6) otpCodeText = it },
                            label = { Text(if (isBn) "৪ সংখ্যার ওটিপি কোড" else "4-Digit OTP Code") },
                            leadingIcon = { Icon(Icons.Default.Phone, null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        )

                        OutlinedTextField(
                            value = newResetPin,
                            onValueChange = { if (it.length <= 6) newResetPin = it },
                            label = { Text(Translator.get("new_pin_label", isBn)) },
                            leadingIcon = { Icon(Icons.Default.Lock, null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 20.dp)
                        )

                        Button(
                            onClick = { viewModel.verifyOtpAndResetPin(otpCodeText, newResetPin) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("verify_otp_and_reset_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = Translator.get("verify_otp", isBn),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        TextButton(
                            onClick = { viewModel.setForgetPasswordStep(0) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isBn) "বাতিল ও পুনরায় চেষ্টা করুন" else "Cancel & Start Over",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Demo trial trigger panel
            Text(
                text = Translator.get("or_divider", isBn),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            OutlinedButton(
                onClick = { viewModel.seedDemoData() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("trial_signin_btn"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(text = "⚡  ")
                    Text(
                        text = Translator.get("trial_btn", isBn),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
