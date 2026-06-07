package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.example.viewmodel.AppViewModel

@Composable
fun AuthScreen(viewModel: AppViewModel) {
    val isBn by viewModel.isBengali.collectAsState()
    val authMsg by viewModel.authStateMessage.collectAsState()
    var isRegisterTab by remember { mutableStateOf(false) }

    // Form inputs
    var shopName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var pinCode by remember { mutableStateOf("") }

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

                    // Dynamically render inputs
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

                    // Email Address
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
                            .padding(bottom = 20.dp)
                    )

                    // Unified Submit Button
                    Button(
                        onClick = {
                            if (isRegisterTab) {
                                viewModel.registerNewUser(shopName, email, phone, pinCode)
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
