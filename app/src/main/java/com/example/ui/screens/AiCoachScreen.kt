package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.AppViewModel
import com.example.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCoachScreen(viewModel: AppViewModel) {
    val isBn by viewModel.isBengali.collectAsState()
    val aiResponse by viewModel.aiReportText.collectAsState()
    val isLoading by viewModel.isAiLoading.collectAsState()
    val customApiKey by viewModel.customGeminiApiKeyState.collectAsState()
    var apiKeyInput by remember(customApiKey) { mutableStateOf(customApiKey) }
    var showKeySettings by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Translator.get("ai_coach", isBn), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo("DASHBOARD") }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // API Key Custom Configuration Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (customApiKey.isBlank() && (try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" } ?: "").let { it.isBlank() || it == "MY_GEMINI_API_KEY" }) 
                        colors.errorContainer.copy(alpha = 0.2f) 
                    else 
                        colors.surfaceVariant.copy(alpha = 0.35f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showKeySettings = !showKeySettings },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (customApiKey.isNotBlank()) Icons.Default.CheckCircle else Icons.Default.Settings,
                                contentDescription = null,
                                tint = if (customApiKey.isNotBlank()) Color(0xFF2E7D32) else colors.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBn) "জেমিনি এপিআই কী (API Key) সেটিংস" else "Gemini AI API Key Settings",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface
                            )
                        }
                        Text(
                            text = if (showKeySettings) (if (isBn) "লুকান ▲" else "Hide ▲") else (if (isBn) "দেখান ▼" else "Show ▼"),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary
                        )
                    }

                    if (showKeySettings || customApiKey.isBlank()) {
                        Divider(color = colors.onSurface.copy(alpha = 0.08f))
                        Text(
                            text = if (isBn) 
                                "গিটহাব বা অ্যান্ড্রয়েড স্টুডিও থেকে নিজের এপিকে বিল্ড করলে গুগল এআই ক্লাউড কী ছাড়া এআই কাজ করবে না। নিচে আপনার ব্যক্তিগত জেমিনি এপিআই কী দিয়ে সেভ করুন। গুগল এআই স্টুডিও (aistudio.google.com) থেকে একদম ফ্রিতে লাইভ কি তৈরি করা যায়।"
                            else 
                                "If you build your own APK from GitHub or Android Studio, Gemini AI won't work without a key. Please enter your own Gemini API Key below. You can get a free API Key from Google AI Studio (aistudio.google.com).",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurface.copy(alpha = 0.7f),
                            lineHeight = 16.sp
                        )

                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            label = { Text(if (isBn) "জেমিনি এপিআই কী (GEMINI_API_KEY)" else "Gemini API Key") },
                            placeholder = { Text("AIzaSy...") },
                            modifier = Modifier.fillMaxWidth().testTag("input_gemini_api_key_field"),
                            shape = RoundedCornerShape(10.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                            singleLine = true
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (customApiKey.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        viewModel.updateCustomGeminiApiKey("")
                                        apiKeyInput = ""
                                    },
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text(if (isBn) "মুছে ফেলুন" else "Reset Key", color = colors.error)
                                }
                            }
                            Button(
                                onClick = {
                                    viewModel.updateCustomGeminiApiKey(apiKeyInput.trim())
                                    viewModel.showToast(if (isBn) "এপিআই কী সফলভাবে সংরক্ষিত হয়েছে!" else "API Key saved successfully!")
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                            ) {
                                Text(if (isBn) "সেভ করুন" else "Save Key", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Premium AI Banner Row
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    colors.primary.copy(alpha = 0.08f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.primary.copy(alpha = 0.15f))
                                .padding(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = if (isBn) "জেমিনি এআই বিজনেস অ্যাসিস্ট্যান্ট" else "Gemini AI Store Analyst",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.primary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = Translator.get("ai_coach_desc", isBn),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onBackground.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Trigger Button
                        Button(
                            onClick = { viewModel.askGeminiForBusinessHealth() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_trigger_ai_report"),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isLoading
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isBn) "দোকানের আর্থিক স্বাস্থ্য রিপোর্ট লিখুন" else "Analyze & Generate Report",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // Results space
            if (isLoading) {
                Spacer(modifier = Modifier.height(24.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(color = colors.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isBn) "জেমিনি এআই মোট বিক্রি, লাভ এবং স্টক বিশ্লেষণ করছে। দয়া করে অপেক্ষা করুন..."
                        else "Gemini is auditing stock counts, totals and due balance. Please wait...",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onBackground.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                }
            } else if (aiResponse != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("ai_result_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(Icons.Default.Star, null, tint = colors.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isBn) "এআই ব্যবসায়িক গাইড ও পর্যবেক্ষণ" else "Gemini Intelligent Advisory",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary
                            )
                        }

                        Text(
                            text = aiResponse ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onBackground,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
