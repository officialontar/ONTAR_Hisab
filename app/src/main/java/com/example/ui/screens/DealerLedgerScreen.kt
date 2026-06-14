package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.AppViewModel
import com.example.data.Dealer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.content.Intent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealerLedgerScreen(viewModel: AppViewModel) {
    val isBn by viewModel.isBengali.collectAsState()
    val dealersList by viewModel.dealers.collectAsState()
    val sortedDealers = remember(dealersList) {
        dealersList.sortedBy { it.id }
    }
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val isDrafting by viewModel.isMsgDrafting.collectAsState()
    val draftedMsg by viewModel.draftedDueMsg.collectAsState()
    var activeDealerForSmsDraft by remember { mutableStateOf<Dealer?>(null) }

    // Form states
    var showAddDealerDialog by remember { mutableStateOf(false) }
    var dealerName by remember { mutableStateOf("") }
    var dealerPhone by remember { mutableStateOf("") }
    var companyName by remember { mutableStateOf("") }
    var dealerPhotoUri by remember { mutableStateOf("") }
    var initialOwedAmountText by remember { mutableStateOf("") }
    var initialOwedStatusIsDebt by remember { mutableStateOf(true) } // true: দেনা / debt, false: অগ্রিম / advance

    // Search query state
    var searchQuery by remember { mutableStateOf("") }

    // Edit dealer profile state
    var dealerToEdit by remember { mutableStateOf<Dealer?>(null) }
    var dealerToDelete by remember { mutableStateOf<Dealer?>(null) }
    var editDealerName by remember { mutableStateOf("") }
    var editDealerPhone by remember { mutableStateOf("") }
    var editCompanyName by remember { mutableStateOf("") }
    var editDealerPhotoUri by remember { mutableStateOf("") }

    // Filter dealers dynamically
    val filteredDealers = remember(dealersList, searchQuery) {
        if (searchQuery.isBlank()) {
            dealersList
        } else {
            val q = searchQuery.trim().lowercase()
            dealersList.filter { dealer ->
                dealer.name.lowercase().contains(q) || 
                dealer.phone.contains(q) || 
                (dealer.company?.lowercase()?.contains(q) ?: false)
            }
        }
    }

    val dealerPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.uriToBase64(context, uri)?.let { base64 ->
                dealerPhotoUri = base64
            }
        }
    }

    val editDealerPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.uriToBase64(context, uri)?.let { base64 ->
                editDealerPhotoUri = base64
            }
        }
    }

    var contactPickTarget by remember { mutableStateOf<String?>(null) } // "ADD" or "EDIT"

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val cr = context.contentResolver
                var name = ""
                var phone = ""
                cr.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.ContactsContract.Contacts.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            name = cursor.getString(nameIndex) ?: ""
                        }
                        val hasPhoneIndex = cursor.getColumnIndex(android.provider.ContactsContract.Contacts.HAS_PHONE_NUMBER)
                        val hasPhone = if (hasPhoneIndex != -1) cursor.getInt(hasPhoneIndex) > 0 else false
                        if (hasPhone) {
                            val idIndex = cursor.getColumnIndex(android.provider.ContactsContract.Contacts._ID)
                            if (idIndex != -1) {
                                val idStr = cursor.getString(idIndex)
                                cr.query(
                                    android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                    null,
                                    android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                                    arrayOf(idStr),
                                    null
                                )?.use { phoneCursor ->
                                    if (phoneCursor.moveToFirst()) {
                                        val pIndex = phoneCursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                                        if (pIndex != -1) {
                                            phone = phoneCursor.getString(pIndex) ?: ""
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                val cleanedPhone = phone.replace(Regex("[\\s\\-\\(\\)]"), "")
                if (contactPickTarget == "ADD") {
                    if (name.isNotEmpty()) {
                        dealerName = name
                    }
                    if (cleanedPhone.isNotEmpty()) {
                        dealerPhone = cleanedPhone
                    }
                } else if (contactPickTarget == "EDIT") {
                    if (name.isNotEmpty()) {
                        editDealerName = name
                    }
                    if (cleanedPhone.isNotEmpty()) {
                        editDealerPhone = cleanedPhone
                    }
                }
            } catch (e: Exception) {
                viewModel.showToast(if (isBn) "কন্টাক্ট রিড করতে সমস্যা হয়েছে! পারমিশন দিন।" else "Error reading contacts!")
            }
        }
    }

    val contactPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Launch picker regardless
        contactPickerLauncher.launch(null)
    }

    // Dues interaction triggers
    var selectedDealerForPayout by remember { mutableStateOf<Dealer?>(null) }
    var payoutAmountText by remember { mutableStateOf("") }

    var selectedDealerForPurchase by remember { mutableStateOf<Dealer?>(null) }
    var purchaseAmountText by remember { mutableStateOf("") }
    var purchaseNotesText by remember { mutableStateOf("") }

    var selectedDealerForHistory by remember { mutableStateOf<Dealer?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Translator.get("dealer_ledger", isBn), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo("DASHBOARD") }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    dealerName = ""
                    dealerPhone = ""
                    companyName = ""
                    dealerPhotoUri = ""
                    initialOwedAmountText = ""
                    initialOwedStatusIsDebt = true
                    showAddDealerDialog = true
                },
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                modifier = Modifier.testTag("fab_add_dealer")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Supplier")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Search bar for Filtering Dealers
                if (dealersList.isNotEmpty()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(if (isBn) "ডিলারের নাম বা মোবাইল দিয়ে খুঁজুন..." else "Search dealer by name or phone...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = colors.primary) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("dealer_search_bar")
                    )

                    // Dynamic dealer statistics cards
                    val totalDealersCount = dealersList.size
                    val phoneDealersCount = dealersList.count { it.phone.trim().isNotEmpty() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = colors.primaryContainer.copy(alpha = 0.4f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = colors.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (isBn) "মোট ডিলার ও পাওনাদার" else "Total Dealers/Suppliers",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isBn) "$totalDealersCount জন" else "$totalDealersCount Person(s)",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primary
                                )
                            }
                        }

                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = colors.secondaryContainer.copy(alpha = 0.4f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = colors.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (isBn) "মোবাইল নম্বরসহ" else "With Phone Number",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isBn) "$phoneDealersCount জন" else "$phoneDealersCount Person(s)",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.secondary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (dealersList.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint = colors.onBackground.copy(alpha = 0.3f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isBn) "ডিলার বা পাওনদার তালিকায় কেউ নেই!" else "No suppliers registered!",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onBackground.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isBn) "নিচের (+) বাটনে চাপ দিয়ে যেকোনো কোম্পানি ডিস্ট্রিবিউটর বা ডিলারের নাম, পাওনা টাকা লিখে খতিয়ান তৈরি করুন।" else "Tap (+) to add trade vendors, suppliers, and manage trade payable dues.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onBackground.copy(alpha = 0.4f),
                            modifier = Modifier.padding(top = 4.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                } else if (filteredDealers.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = colors.onBackground.copy(alpha = 0.3f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isBn) "কোনো ম্যাচিং ডিলার পাওয়া যায়নি!" else "No matching dealers found!",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onBackground.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        items(filteredDealers) { dealer ->
                            val serialNumber = sortedDealers.indexOfFirst { it.id == dealer.id } + 1
                            DealerRecordCard(
                                dealer = dealer,
                                serialNumber = serialNumber,
                                isBn = isBn,
                                colors = colors,
                                onPayoutClick = {
                                    selectedDealerForPayout = dealer
                                    payoutAmountText = ""
                                },
                                onPurchaseClick = {
                                    selectedDealerForPurchase = dealer
                                    purchaseAmountText = ""
                                    purchaseNotesText = ""
                                },
                                onHistoryClick = {
                                    selectedDealerForHistory = dealer
                                },
                                onRemindClick = {
                                    activeDealerForSmsDraft = dealer
                                    viewModel.generateAiDealerMessage(dealer.name, dealer.totalOwed, dealer.company)
                                },
                                onEditClick = {
                                    dealerToEdit = dealer
                                    editDealerName = dealer.name
                                    editDealerPhone = dealer.phone
                                    editCompanyName = dealer.company ?: ""
                                    editDealerPhotoUri = dealer.photoUri ?: ""
                                },
                                onDeleteClick = {
                                    dealerToDelete = dealer
                                }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }

            // ADD DEALER SUPPLIER DIALOG
            if (showAddDealerDialog) {
                AlertDialog(
                    onDismissRequest = { showAddDealerDialog = false },
                    title = { Text(Translator.get("add_dealer", isBn), fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Button(
                                onClick = {
                                    contactPickTarget = "ADD"
                                    contactPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primary.copy(alpha = 0.08f),
                                    contentColor = colors.primary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBox,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = colors.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isBn) "ফোনবুক/কন্টাক্ট থেকে সিলেক্ট করুন" else "Select from Phonebook/Contacts",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            OutlinedTextField(
                                value = dealerName,
                                onValueChange = { dealerName = it },
                                label = { Text(if (isBn) "ডিলারের নাম" else "Dealer Contact Name") },
                                leadingIcon = { Icon(Icons.Default.Person, null) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("input_dealer_name")
                            )

                            OutlinedTextField(
                                value = dealerPhone,
                                onValueChange = { dealerPhone = it },
                                label = { Text(Translator.get("phone_number", isBn)) },
                                leadingIcon = { Icon(Icons.Default.Phone, null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("input_dealer_phone")
                            )

                            OutlinedTextField(
                                value = companyName,
                                onValueChange = { companyName = it },
                                label = { Text(Translator.get("company", isBn)) },
                                leadingIcon = { Icon(Icons.Default.Home, null) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("input_dealer_company")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Initial Owed state selector
                            Text(
                                text = if (isBn) "শুরুর অবস্থা (দেনা নাকি অগ্রিম জমা)" else "Initial Status (Owed or Advance)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.onBackground.copy(alpha = 0.6f)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = initialOwedStatusIsDebt,
                                    onClick = { initialOwedStatusIsDebt = true },
                                    label = { Text(if (isBn) "দেনা (Owed Debt)" else "We Owe") },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = !initialOwedStatusIsDebt,
                                    onClick = { initialOwedStatusIsDebt = false },
                                    label = { Text(if (isBn) "অগ্রিম (Advance Paid)" else "Advance Paid") },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            OutlinedTextField(
                                value = initialOwedAmountText,
                                onValueChange = { initialOwedAmountText = it },
                                label = { Text(if (isBn) "টাকার পরিমাণ" else "Starting Amount") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("input_dealer_initial_owed")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Photo Selector for Suppliers
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.primary.copy(alpha = 0.05f))
                                    .padding(10.dp)
                            ) {
                                if (dealerPhotoUri.isNotEmpty()) {
                                    coil.compose.AsyncImage(
                                        model = dealerPhotoUri,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colors.secondary.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Person, null, tint = colors.secondary)
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isBn) "ডিলারের ছবি" else "Supplier Photo",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (dealerPhotoUri.isEmpty()) {
                                            (if (isBn) "ছবি সিলেক্ট করা হয়নি (ঐচ্ছিক)" else "No selected image (optional)")
                                        } else {
                                            (if (isBn) "ছবি সিলেক্ট করা হয়েছে!" else "Image selected successfully!")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onBackground.copy(alpha = 0.5f)
                                    )
                                }

                                IconButton(
                                    onClick = { dealerPhotoLauncher.launch("image/*") },
                                    modifier = Modifier.background(colors.primary.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Choose Photo", tint = colors.primary)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val photoVal = if (dealerPhotoUri.isBlank()) null else dealerPhotoUri
                                val balanceVal = initialOwedAmountText.toDoubleOrNull() ?: 0.0
                                val finalInitialOwed = if (initialOwedStatusIsDebt) balanceVal else -balanceVal
                                viewModel.addDealer(dealerName.trim(), dealerPhone.trim(), companyName.trim(), photoVal, finalInitialOwed)
                                showAddDealerDialog = false
                            },
                            modifier = Modifier.testTag("btn_save_dealer")
                        ) {
                            Text(Translator.get("add_btn", isBn))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddDealerDialog = false }) {
                            Text(if (isBn) "বাতিল" else "Cancel")
                        }
                    }
                )
            }

            // EDIT DEALER Profile DIALOG
            if (dealerToEdit != null) {
                AlertDialog(
                    onDismissRequest = { dealerToEdit = null },
                    title = { Text(if (isBn) "ডিলারের প্রোফাইল সম্পাদন" else "Edit Supplier Profile", fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Button(
                                onClick = {
                                    contactPickTarget = "EDIT"
                                    contactPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primary.copy(alpha = 0.08f),
                                    contentColor = colors.primary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBox,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = colors.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isBn) "ফোনবুক/কন্টাক্ট থেকে সিলেক্ট করুন" else "Select from Phonebook/Contacts",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            OutlinedTextField(
                                value = editDealerName,
                                onValueChange = { editDealerName = it },
                                label = { Text(if (isBn) "ডিলারের নাম" else "Supplier Name") },
                                leadingIcon = { Icon(Icons.Default.Person, null) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("edit_dealer_name")
                            )

                            OutlinedTextField(
                                value = editDealerPhone,
                                onValueChange = { editDealerPhone = it },
                                label = { Text(if (isBn) "মোবাইল নাম্বার" else "Phone Number") },
                                leadingIcon = { Icon(Icons.Default.Phone, null) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("edit_dealer_phone")
                            )

                            OutlinedTextField(
                                value = editCompanyName,
                                onValueChange = { editCompanyName = it },
                                label = { Text(if (isBn) "কোম্পানি বা ব্র্যান্ড" else "Company / Brand Name") },
                                leadingIcon = { Icon(Icons.Default.Home, null) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("edit_dealer_company")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (editDealerPhotoUri.isNotEmpty()) {
                                    coil.compose.AsyncImage(
                                        model = editDealerPhotoUri,
                                        contentDescription = "Edit Dealer Photo",
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colors.secondary.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Person, null, tint = colors.secondary)
                                    }
                                }

                                Button(
                                    onClick = { editDealerPhotoLauncher.launch("image/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.secondaryContainer, contentColor = colors.onSecondaryContainer),
                                    modifier = Modifier.testTag("btn_edit_dealer_photo")
                                ) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isBn) "ছবি পরিবর্তন" else "Change Photo", fontSize = 11.sp)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val current = dealerToEdit
                                if (current != null && editDealerName.isNotBlank() && editDealerPhone.isNotBlank()) {
                                    viewModel.updateDealerProfile(
                                        dealer = current,
                                        name = editDealerName.trim(),
                                        phone = editDealerPhone.trim(),
                                        company = editCompanyName.trim(),
                                        photoUri = if (editDealerPhotoUri.isBlank()) null else editDealerPhotoUri
                                    )
                                    dealerToEdit = null
                                } else {
                                    viewModel.showToast(if (isBn) "নাম এবং মোবাইল আবশ্যক!" else "Name and Phone are required!")
                                }
                            },
                            modifier = Modifier.testTag("btn_save_edit_dealer")
                        ) {
                            Text(if (isBn) "সংরক্ষণ করুন" else "Save Changes")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { dealerToEdit = null }) {
                            Text(if (isBn) "বাতিল" else "Cancel")
                        }
                    }
                )
            }

            // DEALER PAYOUT DIALOG
            if (selectedDealerForPayout != null) {
                AlertDialog(
                    onDismissRequest = { selectedDealerForPayout = null },
                    title = { Text(Translator.get("dealer_payout", isBn), fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            val owed = selectedDealerForPayout?.totalOwed ?: 0.0
                            val isAdv = owed < 0
                            Text(
                                text = "${selectedDealerForPayout?.name} - " + if (isAdv) {
                                    (if (isBn) "অগ্রিম পরিশোধিত: ৳${java.lang.Math.abs(owed)}" else "Advance Balance: ৳${java.lang.Math.abs(owed)}")
                                } else {
                                    (if (isBn) "মোট পাওনা দেনা: ৳$owed" else "Owed Debt: ৳$owed")
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isAdv) Color(0xFF2E7D32) else colors.secondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = payoutAmountText,
                                onValueChange = { payoutAmountText = it },
                                label = { Text(Translator.get("payout_amount", isBn)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_payout_amount")
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val d = selectedDealerForPayout
                                val amt = payoutAmountText.toDoubleOrNull() ?: 0.0
                                if (d != null && amt > 0) {
                                    viewModel.recordDealerPayment(d, amt)
                                }
                                selectedDealerForPayout = null
                            },
                            modifier = Modifier.testTag("btn_save_payout")
                        ) {
                            Text(Translator.get("save_btn", isBn))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { selectedDealerForPayout = null }) {
                            Text(if (isBn) "বাতিল" else "Cancel")
                        }
                    }
                )
            }

            // RECORD PURCHASE ON CREDIT FROM DEALER
            if (selectedDealerForPurchase != null) {
                AlertDialog(
                    onDismissRequest = { selectedDealerForPurchase = null },
                    title = { Text(Translator.get("dealer_purchase", isBn), fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text(
                                text = "${if (isBn) "ডেসক্রিপশন কোম্পানি ক্রয়" else "Credit purchase"}: ${selectedDealerForPurchase?.name}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = purchaseAmountText,
                                onValueChange = { purchaseAmountText = it },
                                label = { Text(Translator.get("buy_price", isBn)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .testTag("input_dealer_purchase_amount")
                            )

                            OutlinedTextField(
                                value = purchaseNotesText,
                                onValueChange = { purchaseNotesText = it },
                                label = { Text(Translator.get("purchased_goods", isBn)) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_dealer_purchase_note")
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val d = selectedDealerForPurchase
                                val amtOwed = purchaseAmountText.toDoubleOrNull() ?: 0.0
                                if (d != null && amtOwed > 0) {
                                    viewModel.recordDealerPurchase(d, amtOwed, purchaseNotesText.trim())
                                }
                                selectedDealerForPurchase = null
                            },
                            modifier = Modifier.testTag("btn_save_dealer_purchase")
                        ) {
                            Text(Translator.get("save_btn", isBn))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { selectedDealerForPurchase = null }) {
                            Text(if (isBn) "বাতিল" else "Cancel")
                        }
                    }
                )
            }

            // DEALER TRANSACTION HISTORY DIALOG
            if (selectedDealerForHistory != null) {
                val dealer = selectedDealerForHistory!!
                val allTransactions by viewModel.transactions.collectAsState()
                val dealerTransactions = remember(allTransactions, dealer.id) {
                    allTransactions.filter { it.dealerId == dealer.id }.sortedByDescending { it.timestamp }
                }

                AlertDialog(
                    onDismissRequest = { selectedDealerForHistory = null },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.List, null, tint = colors.secondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBn) "লেনদেন বিবরণী (স্টেটমেন্ট)" else "Transaction Statement",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)) {
                            // Quick Summary Info inside Dialog
                            val adv = dealer.totalOwed < 0
                            val amt = if (adv) java.lang.Math.abs(dealer.totalOwed) else dealer.totalOwed
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (adv) Color(0xFFE8F5E9) else if (dealer.totalOwed > 0) Color(0xFFFFEBEE) else colors.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isBn) "বর্তমান মোট স্থিতি:" else "Current Balance:",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = if (adv) {
                                            (if (isBn) "অগ্রিম জমা: ৳$amt" else "Advance deposit: ৳$amt")
                                        } else {
                                            (if (isBn) "বকেয়া দেনা: ৳$amt" else "Outstanding due: ৳$amt")
                                        },
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (adv) Color(0xFF2E7D32) else if (dealer.totalOwed > 0) Color(0xFFC62828) else colors.onSurface
                                    )
                                }
                            }

                            if (dealerTransactions.isEmpty()) {
                                Text(
                                    text = if (isBn) "এই ডিলারের কোনো লেনদেন বিবরণী নেই।" else "No transactions recorded.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onBackground.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(dealerTransactions) { tx ->
                                        val sdf = remember { java.text.SimpleDateFormat("dd-MMM-yyyy | hh:mm a", java.util.Locale.getDefault()) }
                                        val formattedDate = sdf.format(java.util.Date(tx.timestamp))
                                        val isPayment = tx.type == "DEALER_PAYMENT"

                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isPayment) Color(0xFFE8F5E9) else Color(0xFFFFF1F0)
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = if (isPayment) (if (isBn) "✓ টাকা পরিশোধ/অগ্রিম" else "✓ Paid / Advance Sent") else (if (isBn) "⚡ মালামাল ক্রয়" else "⚡ Stock Purchase"),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isPayment) Color(0xFF2E7D32) else Color(0xFFC2410C)
                                                    )
                                                    Text(
                                                        text = tx.description ?: tx.title,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = colors.onBackground.copy(alpha = 0.7f)
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = formattedDate,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = colors.onBackground.copy(alpha = 0.5f)
                                                    )
                                                }
                                                Text(
                                                    text = "৳${tx.amount}",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (isPayment) Color(0xFF2E7D32) else Color(0xFFC2410C)
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
                            onClick = { selectedDealerForHistory = null }
                        ) {
                            Text(if (isBn) "বন্ধ করুন" else "Close")
                        }
                    }
                )
            }

            // GEMINI AI REMINDER DRAFT PREVIEW DIALOG FOR SUPPLIER/DEALER
            if (activeDealerForSmsDraft != null) {
                AlertDialog(
                    onDismissRequest = {
                        activeDealerForSmsDraft = null
                        viewModel.clearDraftedMsg()
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, tint = colors.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBn) "ডিলারের জন্য এআই মেসেজ" else "Supplier AI Message",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    },
                    text = {
                        Column {
                            if (isDrafting) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(color = colors.primary)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = if (isBn) "জেমিনি এআই দিয়ে প্রিমিয়াম বার্তা লেখা হচ্ছে..." else "Gemini writing trade message...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onBackground.copy(alpha = 0.6f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                Text(
                                    text = draftedMsg ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onBackground,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.onBackground.copy(alpha = 0.04f))
                                        .padding(16.dp)
                                        .testTag("gemini_sms_draft_text_dealer")
                                )

                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (isBn) "💡 মেসেজটি কপি করে ডিলারকে সরাসরি এসএমএস বা হোয়াটসঅ্যাপে পাঠাতে পারেন।"
                                    else "💡 Copy this drafted message to send to the supplier via SMS or WhatsApp.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!isDrafting && draftedMsg != null) {
                                    // Send direct SMS button using native Intent
                                    Button(
                                        onClick = {
                                            val smsBody = draftedMsg ?: ""
                                            val phone = activeDealerForSmsDraft?.phone ?: ""
                                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                                data = Uri.parse("smsto:$phone")
                                                putExtra("sms_body", smsBody)
                                            }
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                                                    type = "vnd.android-dir/mms-sms"
                                                    putExtra("address", phone)
                                                    putExtra("sms_body", smsBody)
                                                }
                                                try {
                                                    context.startActivity(fallbackIntent)
                                                } catch (ex: Exception) {
                                                    viewModel.showToast(if (isBn) "মেসেঞ্জার অ্যাপ পাওয়া যায়নি" else "No SMS messenger found")
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32), contentColor = Color.White),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.height(38.dp).testTag("btn_send_sms_direct_dealer")
                                    ) {
                                        Icon(Icons.Default.Send, null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(if (isBn) "এসএমএস" else "SMS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // Send via WhatsApp
                                    Button(
                                        onClick = {
                                            val smsBody = draftedMsg ?: ""
                                            val phone = activeDealerForSmsDraft?.phone ?: ""
                                            var cleanPhone = phone.replace("+", "").replace(" ", "").replace("-", "")
                                            if (!cleanPhone.startsWith("88") && cleanPhone.length == 11) {
                                                cleanPhone = "88$cleanPhone"
                                            }
                                            val url = "https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(smsBody)}"
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                data = Uri.parse(url)
                                            }
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                viewModel.showToast(if (isBn) "হোয়াটসঅ্যাপ অ্যাপ পাওয়া যায়নি" else "WhatsApp not installed")
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366), contentColor = Color.White),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.height(38.dp).testTag("btn_send_whatsapp_dealer")
                                    ) {
                                        Icon(Icons.Default.Phone, null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(if (isBn) "হোয়াটসঅ্যাপ" else "WhatsApp", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // Copy Draft button
                                    Button(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(draftedMsg ?: ""))
                                            viewModel.showToast(if (isBn) "বার্তা ক্লিপবোর্ডে কপি করা হয়েছে!" else "Copied text to clipboard!")
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary.copy(alpha = 0.1f), contentColor = colors.primary),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.height(38.dp).testTag("btn_copy_draft_dealer")
                                    ) {
                                        Icon(Icons.Default.Share, null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(if (isBn) "কপি" else "Copy", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        activeDealerForSmsDraft = null
                                        viewModel.clearDraftedMsg()
                                    }
                                ) {
                                    Text(if (isBn) "বন্ধ করুন" else "Close")
                                }
                            }
                        }
                    }
                )
            }

            // DELETE DEALER CONFIRMATION DIALOG
            if (dealerToDelete != null) {
                AlertDialog(
                    onDismissRequest = { dealerToDelete = null },
                    icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = colors.error) },
                    title = {
                        Text(
                            text = if (isBn) "ডিলার মুছে ফেলবেন?" else "Delete Supplier?",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    text = {
                        Text(
                            text = if (isBn) "আপনি কি নিশ্চিত যে আপনি '${dealerToDelete?.name}' এবং তার সম্পূর্ণ ক্রয় ও পরিশোধের বিবরণ মুছে ফেলতে চান? এই অ্যাকশনটি পূর্বাবস্থায় ফিরিয়ে আনা সম্ভব নয়।"
                            else "Are you sure you want to delete '${dealerToDelete?.name}' and all of their purchase and payment histories? This action cannot be undone.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                dealerToDelete?.let {
                                    viewModel.deleteDealer(it)
                                }
                                dealerToDelete = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError)
                        ) {
                            Text(if (isBn) "হ্যাঁ, মুছুন" else "Yes, Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { dealerToDelete = null }
                        ) {
                            Text(if (isBn) "না, বাতিল" else "No, Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun DealerRecordCard(
    dealer: Dealer,
    serialNumber: Int,
    isBn: Boolean,
    colors: ColorScheme,
    onPayoutClick: () -> Unit,
    onPurchaseClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onRemindClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onHistoryClick() }
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1.8f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = serialNumber.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.primary.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        if (!dealer.photoUri.isNullOrEmpty()) {
                            coil.compose.AsyncImage(
                                model = dealer.photoUri,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.secondary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.ShoppingCart, null, tint = colors.secondary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = dealer.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Black,
                            color = colors.onBackground,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            text = dealer.phone,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onBackground.copy(alpha = 0.5f)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Balance owed
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.weight(1.1f)
                ) {
                    val isAdvance = dealer.totalOwed < 0
                    Text(
                        text = if (isAdvance) {
                            (if (isBn) "অগ্রিম পরিশোধ (পাওনা)" else "Advance Paid")
                        } else {
                            (if (isBn) "ডিলার পাওনা (দেনা)" else "We Owe Supplier")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onBackground.copy(alpha = 0.4f)
                    )
                    Text(
                        text = "৳ ${if (isAdvance) java.lang.Math.abs(dealer.totalOwed) else dealer.totalOwed}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isAdvance) Color(0xFF2E7D32) else if (dealer.totalOwed > 0) Color(0xFFD32F2F) else Color(0xFF2E7D32)
                    )
                }
            }

            if (dealer.company != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = colors.onBackground.copy(alpha = 0.3f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = dealer.company ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onBackground.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = colors.secondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isBn) "💡 লেনদেনের তারিখ ও বিস্তারিত স্টেটমেন্ট দেখতে এখানে চাপুন" else "💡 Click to view detailed statement history with exact dates",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.secondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp), color = colors.onBackground.copy(alpha = 0.05f))

            // Dealer actions row 1: Core transactions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pay off debt
                Button(
                    onClick = onPayoutClick,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.secondary.copy(alpha = 0.12f), contentColor = colors.secondary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                ) {
                    Text(
                        text = if (isBn) "৳ পরিশোধ/অগ্রিম" else "৳ Pay / Advance",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Record new purchase on credit
                Button(
                    onClick = onPurchaseClick,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceVariant, contentColor = colors.onSurface),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                ) {
                    Text(
                        text = if (isBn) "+ ক্রয় লিখুন" else "+ Buy Owed",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Dealer actions row 2: AI utilities & profiling (Edit/Delete)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val hasSms = dealer.totalOwed != 0.0 && dealer.phone.isNotBlank()
                
                // 1. AI SMS Option (Takes proportional weight if available)
                if (hasSms) {
                    Button(
                        onClick = onRemindClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary.copy(alpha = 0.08f),
                            contentColor = colors.primary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .weight(1.2f)
                            .testTag("btn_trigger_ai_sms_dealer_${dealer.name.replace(" ", "_")}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isBn) "এআই এসএমএস" else "AI SMS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 2. Edit Profile Option (Always in the middle point)
                Button(
                    onClick = onEditClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary.copy(alpha = 0.08f),
                        contentColor = colors.primary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .weight(1f)
                        .testTag("btn_edit_dealer_${dealer.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Profile",
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isBn) "এডিট" else "Edit",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 3. Delete Option (Placed on the right end)
                Button(
                    onClick = onDeleteClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.error.copy(alpha = 0.08f),
                        contentColor = colors.error
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .weight(1f)
                        .testTag("btn_delete_dealer_${dealer.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Supplier",
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isBn) "ডিলিট" else "Delete",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
