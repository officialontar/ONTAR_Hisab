package com.example.ui.screens

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.AppViewModel
import com.example.data.Customer
import android.net.Uri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerLedgerScreen(viewModel: AppViewModel) {
    val isBn by viewModel.isBengali.collectAsState()
    val customersList by viewModel.customers.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val colors = MaterialTheme.colorScheme

    var showBulkSmsDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val eligibleCustomersForSms = customersList.filter { it.totalDue > 0 && it.phone.isNotBlank() }
    val (smsTodayStr, smsNextStr) = getSmsDates(isBn)
    val smsShopName = currentUser?.shopName ?: if (isBn) "আমার দোকান" else "My Shop"
    val smsShopPhone = currentUser?.phone ?: ""
    val smsOwnerName = currentUser?.ownerName ?: ""

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            sendDirectBulkSms(
                context = context,
                customers = eligibleCustomersForSms,
                isBn = isBn,
                todayStr = smsTodayStr,
                nextStr = smsNextStr,
                shopName = smsShopName,
                shopPhone = smsShopPhone,
                ownerName = smsOwnerName,
                viewModel = viewModel
            )
        } else {
            viewModel.showToast(
                if (isBn) "সরাসরি সিম থেকে অটো এসএমএস পাঠানোর জন্য অনুমতি প্রয়োজন!" 
                else "Permission is required to send direct cellular SMS!"
            )
        }
    }

    // Dialog form triggers
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var customerAddress by remember { mutableStateOf("") }
    var customerPhotoUri by remember { mutableStateOf("") }
    var initialBalanceText by remember { mutableStateOf("") }
    var initialStatusIsDue by remember { mutableStateOf(true) } // true for Due / বাকি, false for Deposit / জমা

    // Search query state
    var searchQuery by remember { mutableStateOf("") }

    // Edit customer profile state
    var customerToEdit by remember { mutableStateOf<Customer?>(null) }
    var customerToDelete by remember { mutableStateOf<Customer?>(null) }
    var editCustomerName by remember { mutableStateOf("") }
    var editCustomerPhone by remember { mutableStateOf("") }
    var editCustomerAddress by remember { mutableStateOf("") }
    var editCustomerPhotoUri by remember { mutableStateOf("") }

    // Filter customers dynamically
    val filteredCustomers = remember(customersList, searchQuery) {
        if (searchQuery.isBlank()) {
            customersList
        } else {
            val q = searchQuery.trim().lowercase()
            customersList.filter { customer ->
                customer.name.lowercase().contains(q) || customer.phone.contains(q)
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            customerPhotoUri = uri.toString()
        }
    }

    val editPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            editCustomerPhotoUri = uri.toString()
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
                        customerName = name
                    }
                    if (cleanedPhone.isNotEmpty()) {
                        customerPhone = cleanedPhone
                    }
                } else if (contactPickTarget == "EDIT") {
                    if (name.isNotEmpty()) {
                        editCustomerName = name
                    }
                    if (cleanedPhone.isNotEmpty()) {
                        editCustomerPhone = cleanedPhone
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

    var selectedCustomerForHistory by remember { mutableStateOf<Customer?>(null) }

    // Dues interaction triggers
    var selectedCustomerForDeposit by remember { mutableStateOf<Customer?>(null) }
    var depositAmountText by remember { mutableStateOf("") }

    var selectedCustomerForNewDue by remember { mutableStateOf<Customer?>(null) }
    var newDueAmountText by remember { mutableStateOf("") }
    var customDueReasonText by remember { mutableStateOf("") }

    // Gemini AI SMS Dialog Trigger
    var activeCustomerForSmsDraft by remember { mutableStateOf<Customer?>(null) }
    val draftedMsg by viewModel.draftedDueMsg.collectAsState()
    val isDrafting by viewModel.isMsgDrafting.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Translator.get("customer_ledger", isBn), fontWeight = FontWeight.Bold) },
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
                    customerName = ""
                    customerPhone = ""
                    customerAddress = ""
                    customerPhotoUri = ""
                    showAddCustomerDialog = true
                },
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                modifier = Modifier.testTag("fab_add_customer")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Customer")
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
                // Search bar for Filtering Customers
                if (customersList.isNotEmpty()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(if (isBn) "কাস্টমার নাম বা মোবাইল নাম্বার দিয়ে খুঁজুন..." else "Search customer by name or phone...") },
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
                            .testTag("customer_search_bar")
                    )
                }

                if (customersList.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = colors.onBackground.copy(alpha = 0.3f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isBn) "বাকি খাতায় এখনো কোনো কাস্টমার নেই!" else "No customers in your ledger!",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onBackground.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isBn) "নিচের (+) বাটনে চাপ দিয়ে কাস্টমারের নাম, ফোন নাম্বার দিয়ে বাকি খাতা তৈরি করুন।" else "Tap (+) to add active customers and manage credit balance sheet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onBackground.copy(alpha = 0.4f),
                            modifier = Modifier.padding(top = 4.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                } else if (filteredCustomers.isEmpty()) {
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
                            text = if (isBn) "কোনো ম্যাচিং কাস্টমার পাওয়া যায়নি!" else "No matching customers found!",
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

                        item {
                            val eligibleCustomers = customersList.filter { it.totalDue > 0 && it.phone.isNotBlank() }
                            if (eligibleCustomers.isNotEmpty()) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .testTag("bulk_sms_banner_card"),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = colors.primary.copy(alpha = 0.06f)
                                    ),
                                    border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.15f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1.0f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(CircleShape)
                                                    .background(colors.primary.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Email,
                                                    contentDescription = null,
                                                    tint = colors.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = if (isBn) "স্মার্ট এআই বাল্ক মেসেজিং" else "Smart AI Bulk Reminder",
                                                    fontWeight = FontWeight.ExtraBold,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = colors.primary
                                                )
                                                Text(
                                                    text = if (isBn) "${eligibleCustomers.size} জন কাস্টমারকে বকেয়া তাগাদা দিন" else "Remind ${eligibleCustomers.size} customers at once",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = colors.onBackground.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                        
                                        Button(
                                            onClick = { showBulkSmsDialog = true },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = colors.primary,
                                                contentColor = colors.onPrimary
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                            modifier = Modifier.height(36.dp).testTag("btn_open_bulk_sms")
                                        ) {
                                            Icon(Icons.Default.Send, null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isBn) "তাগাদা দিন" else "Remind",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        items(filteredCustomers) { customer ->
                            CustomerRecordCard(
                                customer = customer,
                                isBn = isBn,
                                colors = colors,
                                onHistoryClick = {
                                    selectedCustomerForHistory = customer
                                },
                                onDepositClick = {
                                    selectedCustomerForDeposit = customer
                                    depositAmountText = ""
                                },
                                onNewDueClick = {
                                    selectedCustomerForNewDue = customer
                                    newDueAmountText = ""
                                    customDueReasonText = ""
                                },
                                onRemindClick = {
                                    activeCustomerForSmsDraft = customer
                                    viewModel.generateAiDueMessage(customer.name, customer.totalDue, customer.address)
                                },
                                onEditClick = {
                                    customerToEdit = customer
                                    editCustomerName = customer.name
                                    editCustomerPhone = customer.phone
                                    editCustomerAddress = customer.address ?: ""
                                    editCustomerPhotoUri = customer.photoUri ?: ""
                                },
                                onDeleteClick = {
                                    customerToDelete = customer
                                }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }

            // ADD CUSTOMER DIALOG
            if (showAddCustomerDialog) {
                AlertDialog(
                    onDismissRequest = { showAddCustomerDialog = false },
                    title = { Text(Translator.get("add_customer", isBn), fontWeight = FontWeight.Bold) },
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
                                value = customerName,
                                onValueChange = { customerName = it },
                                label = { Text(Translator.get("customer_name", isBn)) },
                                leadingIcon = { Icon(Icons.Default.Person, null) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("input_customer_name")
                            )

                            OutlinedTextField(
                                value = customerPhone,
                                onValueChange = { customerPhone = it },
                                label = { Text(Translator.get("phone_number", isBn)) },
                                leadingIcon = { Icon(Icons.Default.Phone, null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("input_customer_phone")
                            )

                            OutlinedTextField(
                                value = customerAddress,
                                onValueChange = { customerAddress = it },
                                label = { Text(Translator.get("address", isBn)) },
                                leadingIcon = { Icon(Icons.Default.LocationOn, null) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Initial Balance state selector
                            Text(
                                text = if (isBn) "শুরুর অবস্থা (বাকি নাকি জমা)" else "Initial Status (Due or Deposit)",
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
                                    selected = initialStatusIsDue,
                                    onClick = { initialStatusIsDue = true },
                                    label = { Text(if (isBn) "বাকি (Due)" else "Due") },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = !initialStatusIsDue,
                                    onClick = { initialStatusIsDue = false },
                                    label = { Text(if (isBn) "জমা (Deposit)" else "Deposit") },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            OutlinedTextField(
                                value = initialBalanceText,
                                onValueChange = { initialBalanceText = it },
                                label = { Text(if (isBn) "টাকার পরিমাণ" else "Starting Amount") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("input_initial_balance")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Photo selection block
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.primary.copy(alpha = 0.05f))
                                    .padding(10.dp)
                            ) {
                                if (customerPhotoUri.isNotEmpty()) {
                                    coil.compose.AsyncImage(
                                        model = customerPhotoUri,
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
                                            .background(colors.primary.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Person, null, tint = colors.primary)
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isBn) "কাস্টমার প্রোফাইল ছবি" else "Customer Profile Photo",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (customerPhotoUri.isEmpty()) {
                                            (if (isBn) "ছবি সিলেক্ট করা হয়নি (ঐচ্ছিক)" else "No selected image (optional)")
                                        } else {
                                             (if (isBn) "ছবি সিলেক্ট করা হয়েছে!" else "Image selected successfully!")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onBackground.copy(alpha = 0.5f)
                                    )
                                }

                                IconButton(
                                    onClick = { photoPickerLauncher.launch("image/*") },
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
                                val photoValue = if (customerPhotoUri.isBlank()) null else customerPhotoUri
                                val balanceAmount = initialBalanceText.toDoubleOrNull() ?: 0.0
                                val finalInitialDue = if (initialStatusIsDue) balanceAmount else -balanceAmount
                                viewModel.addCustomer(customerName.trim(), customerPhone.trim(), customerAddress.trim(), photoValue, finalInitialDue)
                                showAddCustomerDialog = false
                            },
                            modifier = Modifier.testTag("btn_save_customer")
                        ) {
                            Text(Translator.get("add_btn", isBn))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddCustomerDialog = false }) {
                            Text(if (isBn) "বাতিল" else "Cancel")
                        }
                    }
                )
            }

            // EDIT CUSTOMER DIALOG
            if (customerToEdit != null) {
                AlertDialog(
                    onDismissRequest = { customerToEdit = null },
                    title = { Text(if (isBn) "কাস্টমার প্রোফাইল সম্পাদন" else "Edit Customer Profile", fontWeight = FontWeight.Bold) },
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
                                value = editCustomerName,
                                onValueChange = { editCustomerName = it },
                                label = { Text(if (isBn) "কাস্টমারের নাম" else "Customer Name") },
                                leadingIcon = { Icon(Icons.Default.Person, null) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("edit_customer_name")
                            )

                            OutlinedTextField(
                                value = editCustomerPhone,
                                onValueChange = { editCustomerPhone = it },
                                label = { Text(if (isBn) "মোবাইল নাম্বার" else "Phone Number") },
                                leadingIcon = { Icon(Icons.Default.Phone, null) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("edit_customer_phone")
                            )

                            OutlinedTextField(
                                value = editCustomerAddress,
                                onValueChange = { editCustomerAddress = it },
                                label = { Text(if (isBn) "ঠিকানা" else "Address / Location") },
                                leadingIcon = { Icon(Icons.Default.LocationOn, null) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("edit_customer_address")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (editCustomerPhotoUri.isNotEmpty()) {
                                    coil.compose.AsyncImage(
                                        model = editCustomerPhotoUri,
                                        contentDescription = "Edit Customer image",
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
                                            .background(colors.primary.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Person, null, tint = colors.primary)
                                    }
                                }

                                Button(
                                    onClick = { editPhotoPickerLauncher.launch("image/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.secondaryContainer, contentColor = colors.onSecondaryContainer),
                                    modifier = Modifier.testTag("btn_edit_customer_photo")
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
                                val current = customerToEdit
                                if (current != null && editCustomerName.isNotBlank() && editCustomerPhone.isNotBlank()) {
                                    viewModel.updateCustomerProfile(
                                        customer = current,
                                        name = editCustomerName.trim(),
                                        phone = editCustomerPhone.trim(),
                                        address = editCustomerAddress.trim(),
                                        photoUri = if (editCustomerPhotoUri.isBlank()) null else editCustomerPhotoUri
                                    )
                                    customerToEdit = null
                                } else {
                                    viewModel.showToast(if (isBn) "নাম এবং মোবাইল আবশ্যক!" else "Name and Phone are required!")
                                }
                            },
                            modifier = Modifier.testTag("btn_save_edit_customer")
                        ) {
                            Text(if (isBn) "সংরক্ষণ করুন" else "Save Changes")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { customerToEdit = null }) {
                            Text(if (isBn) "বাতিল" else "Cancel")
                        }
                    }
                )
            }

            // RECORD PAYMENT (জমা আদায়) DIALOG
            if (selectedCustomerForDeposit != null) {
                AlertDialog(
                    onDismissRequest = { selectedCustomerForDeposit = null },
                    title = { Text(Translator.get("record_payment", isBn), fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            val custDue = selectedCustomerForDeposit?.totalDue ?: 0.0
                            val hasDeposit = custDue < 0
                            Text(
                                text = "${selectedCustomerForDeposit?.name} - " + if (hasDeposit) {
                                    (if (isBn) "পছন্দনীয় অবশিষ্ট জমা: ৳${java.lang.Math.abs(custDue)}" else "Available Deposit: ৳${java.lang.Math.abs(custDue)}")
                                } else {
                                    (if (isBn) "মোট বকেয়া বাকি: ৳$custDue" else "Total Due Debt: ৳$custDue")
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (hasDeposit) Color(0xFF2E7D32) else colors.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = depositAmountText,
                                onValueChange = { depositAmountText = it },
                                label = { Text(Translator.get("amount_deposit", isBn)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_deposit_amount")
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val cust = selectedCustomerForDeposit
                                val valDeposit = depositAmountText.toDoubleOrNull() ?: 0.0
                                if (cust != null && valDeposit > 0) {
                                    viewModel.recordCustomerPayment(cust, valDeposit)
                                }
                                selectedCustomerForDeposit = null
                            },
                            modifier = Modifier.testTag("btn_save_deposit")
                        ) {
                            Text(Translator.get("save_btn", isBn))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { selectedCustomerForDeposit = null }) {
                            Text(if (isBn) "বাতিল" else "Cancel")
                        }
                    }
                )
            }

            // HISTORICAL LEDGER DIALOG (হিসাব বিবরণী)
            if (selectedCustomerForHistory != null) {
                val customer = selectedCustomerForHistory!!
                val allTx by viewModel.transactions.collectAsState()
                val customerTransactions = remember(allTx, customer) {
                    allTx.filter { it.customerId == customer.id }
                }

                AlertDialog(
                    onDismissRequest = { selectedCustomerForHistory = null },
                    title = {
                        Column {
                            Text(
                                text = if (isBn) "${customer.name}-এর হিসাব বিবরণী" else "${customer.name}'s Statement",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val actualDue = customer.totalDue
                            val hasDep = actualDue < 0
                            Text(
                                text = if (hasDep) {
                                    (if (isBn) "মোট অগ্রিম জমা: ৳${java.lang.Math.abs(actualDue)}" else "Total Deposit: ৳${java.lang.Math.abs(actualDue)}")
                                } else {
                                    (if (isBn) "মোট বকেয়া: ৳$actualDue" else "Total Due: ৳$actualDue")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (hasDep) Color(0xFF2E7D32) else colors.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)) {
                            if (customerTransactions.isEmpty()) {
                                Text(
                                    text = if (isBn) "এই কাস্টমারের কোনো লেনদেন বিবরণী নেই।" else "No transactions recorded.",
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
                                    items(customerTransactions) { tx ->
                                        val sdf = remember { java.text.SimpleDateFormat("dd-MMM-yyyy | hh:mm a", java.util.Locale.getDefault()) }
                                        val formattedDate = sdf.format(java.util.Date(tx.timestamp))
                                        val isPayment = tx.type == "CUSTOMER_PAYMENT"

                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isPayment) Color(0xFFE8F5E9) else Color(0xFFFFFDE7)
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
                                                        text = if (isPayment) (if (isBn) "✓ জমা আদায়" else "✓ Payment Received") else (if (isBn) "⚡ বাকি নেওয়া হয়েছে" else "⚡ Due Charged"),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isPayment) Color(0xFF2E7D32) else Color(0xFFB59300)
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
                                                    color = if (isPayment) Color(0xFF2E7D32) else Color(0xFFB59300)
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
                            onClick = { selectedCustomerForHistory = null }
                        ) {
                            Text(if (isBn) "বন্ধ করুন" else "Close")
                        }
                    }
                )
            }

            // ADD CUSTOM DUE (নতুন বাকি লিখুন) DIALOG
            if (selectedCustomerForNewDue != null) {
                AlertDialog(
                    onDismissRequest = { selectedCustomerForNewDue = null },
                    title = { Text(Translator.get("add_custom_due", isBn), fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text(
                                text = "${if (isBn) "ক্রেডিট বাড়ান" else "Increase Credit due"}: ${selectedCustomerForNewDue?.name}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = newDueAmountText,
                                onValueChange = { newDueAmountText = it },
                                label = { Text(Translator.get("due_amount", isBn)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .testTag("input_custom_due_amount")
                            )

                            OutlinedTextField(
                                value = customDueReasonText,
                                onValueChange = { customDueReasonText = it },
                                label = { Text(Translator.get("reason", isBn)) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_custom_due_note")
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val cust = selectedCustomerForNewDue
                                val addedDue = newDueAmountText.toDoubleOrNull() ?: 0.0
                                if (cust != null && addedDue > 0) {
                                    viewModel.recordCustomerCustomDue(cust, addedDue, customDueReasonText.trim())
                                }
                                selectedCustomerForNewDue = null
                            },
                            modifier = Modifier.testTag("btn_save_custom_due")
                        ) {
                            Text(Translator.get("save_btn", isBn))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { selectedCustomerForNewDue = null }) {
                            Text(if (isBn) "বাতিল" else "Cancel")
                        }
                    }
                )
            }

            // GEMINI AI REMINDER DRAFT PREVIEW DIALOG
            if (activeCustomerForSmsDraft != null) {
                AlertDialog(
                    onDismissRequest = {
                        activeCustomerForSmsDraft = null
                        viewModel.clearDraftedMsg()
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, tint = colors.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = Translator.get("ai_sms_remind", isBn),
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
                                        text = if (isBn) "জেমিনি এআই দিয়ে প্রিমিয়াম বার্তা লেখা হচ্ছে..." else "Gemini write intelligent reminder message...",
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
                                        .testTag("gemini_sms_draft_text")
                                )

                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (isBn) "💡 মেসেজটি কপি করে কাস্টমারকে সরাসরি এসএমএস বা হোয়াটসঅ্যাপে পাঠাতে পারেন।"
                                    else "💡 Copy this drafted message to send via SMS or WhatsApp right away.",
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
                                            val phone = activeCustomerForSmsDraft?.phone ?: ""
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
                                        modifier = Modifier.height(38.dp).testTag("btn_send_sms_direct")
                                    ) {
                                        Icon(Icons.Default.Send, null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(if (isBn) "এসএমএস" else "SMS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // Send via WhatsApp
                                    Button(
                                        onClick = {
                                            val smsBody = draftedMsg ?: ""
                                            val phone = activeCustomerForSmsDraft?.phone ?: ""
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
                                        modifier = Modifier.height(38.dp).testTag("btn_send_whatsapp")
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
                                        modifier = Modifier.height(38.dp).testTag("btn_copy_draft")
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
                                        activeCustomerForSmsDraft = null
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

            // DELETE CUSTOMER CONFIRMATION DIALOG
            if (customerToDelete != null) {
                AlertDialog(
                    onDismissRequest = { customerToDelete = null },
                    icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = colors.error) },
                    title = {
                        Text(
                            text = if (isBn) "কাস্টমার মুছে ফেলবেন?" else "Delete Customer?",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    text = {
                        Text(
                            text = if (isBn) "আপনি কি নিশ্চিত যে আপনি '${customerToDelete?.name}' এবং তার সম্পূর্ণ লেনদেনের বিবরণ মুছে ফেলতে চান? এই অ্যাকশনটি পূর্বাবস্থায় ফিরিয়ে আনা সম্ভব নয়।"
                            else "Are you sure you want to delete '${customerToDelete?.name}' and all of their transaction histories? This action cannot be undone.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                customerToDelete?.let {
                                    viewModel.deleteCustomer(it)
                                }
                                customerToDelete = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError)
                        ) {
                            Text(if (isBn) "হ্যাঁ, মুছুন" else "Yes, Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { customerToDelete = null }
                        ) {
                            Text(if (isBn) "না, বাতিল" else "No, Cancel")
                        }
                    }
                )
            }

            // SMART BULK AI SMS MESSAGING CENTER DIALOG
            if (showBulkSmsDialog) {
                val eligibleCustomers = customersList.filter { it.totalDue > 0 && it.phone.isNotBlank() }
                val (todayStr, nextStr) = getSmsDates(isBn)
                val shopName = currentUser?.shopName ?: if (isBn) "আমার দোকান" else "My Shop"
                val shopPhone = currentUser?.phone ?: ""
                val ownerName = currentUser?.ownerName ?: ""

                AlertDialog(
                    onDismissRequest = { showBulkSmsDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Send, null, tint = colors.primary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBn) "এআই বাল্ক মেসেজিং সেন্টার" else "AI Bulk Messaging Center",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = if (isBn) "আজকের ও পরবর্তী ৭ দিনের তারিখ অনুযায়ী প্রত্যেক গ্রাহকের জন্য আলাদা আলাদা মিষ্টি তাগাদা তৈরি করা হয়েছে।" 
                                else "Unique polite debt reminders have been automatically drafted for each customer based on today and next 7-days window.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onBackground.copy(alpha = 0.6f),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.primary.copy(alpha = 0.05f))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = if (isBn) "বর্তমান তারিখ: $todayStr" else "Current Date: $todayStr",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.primary
                                    )
                                    Text(
                                        text = if (isBn) "পরিশোধের শেষ তারিখ: $nextStr" else "Deadline Date: $nextStr",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFD32F2F)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(colors.primary.copy(alpha = 0.12f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (isBn) "৭ দিন সময়" else "7 Days Period",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = colors.primary,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // ⚡ AUTOMATIC BROADCAST SIM CARRIER SMS BUTTON
                            Button(
                                onClick = {
                                    val hasSmsPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.SEND_SMS
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                                    if (hasSmsPermission) {
                                        sendDirectBulkSms(
                                            context = context,
                                            customers = eligibleCustomers,
                                            isBn = isBn,
                                            todayStr = todayStr,
                                            nextStr = nextStr,
                                            shopName = shopName,
                                            shopPhone = shopPhone,
                                            ownerName = ownerName,
                                            viewModel = viewModel
                                        )
                                    } else {
                                        smsPermissionLauncher.launch(android.Manifest.permission.SEND_SMS)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2E7D32),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("btn_broadcast_sim_sms")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isBn) "সরাসরি সিম থেকে অটোমেটিক এসএমএস পাঠান" else "Send Auto-SMS to All directly",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = if (isBn) "ℹ️ এটি আপনার ফোনের নিজস্ব সিম এবং স্বাভাবিক এসএমএস ব্যালেন্স ব্যবহার করে সরাসরি ১ সেকেন্ডে সবার ফোনে আলাদা আলাদা মিষ্টি বকেয়া তাগাদা মেসেজ পাঠিয়ে দেবে।"
                                else "ℹ️ This leverages your phone's own cellular SIM card's regular carrier SMS plan to send customized polite debt reminders in 1 second.",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onBackground.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            if (eligibleCustomers.isEmpty()) {
                                Text(
                                    text = if (isBn) "বাকি পরিশোধের তাগাদা পাঠানোর মতো কোনো সক্রিয় কাস্টমার নেই।" else "No active customers with unpaid dues to remind.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onBackground.copy(alpha = 0.4f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                                )
                            } else {
                                Box(modifier = Modifier.heightIn(max = 350.dp)) {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        itemsIndexed(eligibleCustomers) { idx, cust ->
                                            val draftedSmsText = generateDynamicTemplate(
                                                index = idx,
                                                customerName = cust.name,
                                                totalDue = cust.totalDue,
                                                address = cust.address,
                                                todayStr = todayStr,
                                                nextStr = nextStr,
                                                shopName = shopName,
                                                shopPhone = shopPhone,
                                                ownerName = ownerName,
                                                isBn = isBn
                                            )

                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant.copy(alpha = 0.5f)),
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, colors.onSurface.copy(alpha = 0.08f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column {
                                                            Text(
                                                                text = cust.name,
                                                                fontWeight = FontWeight.Black,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                color = colors.onSurface
                                                            )
                                                            Text(
                                                                text = cust.phone,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = colors.onSurface.copy(alpha = 0.5f)
                                                            )
                                                        }
                                                        Text(
                                                            text = "৳ ${if (isBn) toBengaliDigits(String.format("%.1f", cust.totalDue)) else String.format("%.1f", cust.totalDue)}",
                                                            fontWeight = FontWeight.ExtraBold,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = Color(0xFFD32F2F)
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.height(6.dp))

                                                    Text(
                                                        text = draftedSmsText,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontSize = 11.sp,
                                                        color = colors.onSurface.copy(alpha = 0.8f),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(colors.background)
                                                            .padding(8.dp)
                                                    )

                                                    Spacer(modifier = Modifier.height(8.dp))

                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        // Copy Button
                                                        Button(
                                                            onClick = {
                                                                clipboardManager.setText(AnnotatedString(draftedSmsText))
                                                                viewModel.showToast(
                                                                    if (isBn) "${cust.name}-এর বার্তা কপি হয়েছে!" else "Copied ${cust.name}'s message!"
                                                                )
                                                            },
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = colors.primary.copy(alpha = 0.08f),
                                                                contentColor = colors.primary
                                                            ),
                                                            shape = RoundedCornerShape(6.dp),
                                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                            modifier = Modifier.height(28.dp)
                                                        ) {
                                                            Text(if (isBn) "কপি" else "Copy", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        }

                                                        // WhatsApp Button
                                                        Button(
                                                            onClick = {
                                                                var cleanPhone = cust.phone.replace("+", "").replace(" ", "").replace("-", "")
                                                                if (!cleanPhone.startsWith("88") && cleanPhone.length == 11) {
                                                                    cleanPhone = "88$cleanPhone"
                                                                }
                                                                val url = "https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(draftedSmsText)}"
                                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                                    data = Uri.parse(url)
                                                                }
                                                                try {
                                                                    context.startActivity(intent)
                                                                } catch (e: Exception) {
                                                                    viewModel.showToast(if (isBn) "হোয়াটসঅ্যাপ অ্যাপ পাওয়া যায়নি" else "WhatsApp not installed")
                                                                }
                                                            },
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = Color(0xFF25D366),
                                                                contentColor = Color.White
                                                            ),
                                                            shape = RoundedCornerShape(6.dp),
                                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                            modifier = Modifier.height(28.dp)
                                                        ) {
                                                            Text(if (isBn) "হোয়াটসঅ্যাপ" else "WhatsApp", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        }

                                                        // SMS Button
                                                        Button(
                                                            onClick = {
                                                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                                                    data = Uri.parse("smsto:${cust.phone}")
                                                                    putExtra("sms_body", draftedSmsText)
                                                                }
                                                                try {
                                                                    context.startActivity(intent)
                                                                } catch (e: Exception) {
                                                                    val fallback = Intent(Intent.ACTION_VIEW).apply {
                                                                        type = "vnd.android-dir/mms-sms"
                                                                        putExtra("address", cust.phone)
                                                                        putExtra("sms_body", draftedSmsText)
                                                                    }
                                                                    try {
                                                                        context.startActivity(fallback)
                                                                    } catch (ex: Exception) {
                                                                        viewModel.showToast(if (isBn) "মেসেঞ্জার পাওয়া যায়নি" else "SMS messenger not found")
                                                                    }
                                                                }
                                                            },
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = Color(0xFF2E7D32),
                                                                contentColor = Color.White
                                                            ),
                                                            shape = RoundedCornerShape(6.dp),
                                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                            modifier = Modifier.height(28.dp)
                                                        ) {
                                                            Text(if (isBn) "এসএমএস" else "SMS", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { showBulkSmsDialog = false }
                        ) {
                            Text(if (isBn) "বন্ধ করুন" else "Close")
                        }
                    }
                )
            }
        }
    }
}

// BULK AI SMS DATE GENERATORS AND DYNAMIC TEMPLATE DISPATCHERS
fun sendDirectBulkSms(
    context: android.content.Context,
    customers: List<com.example.data.Customer>,
    isBn: Boolean,
    todayStr: String,
    nextStr: String,
    shopName: String,
    shopPhone: String,
    ownerName: String,
    viewModel: com.example.viewmodel.AppViewModel
) {
    try {
        val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            context.getSystemService(android.telephony.SmsManager::class.java)!!
        } else {
            @Suppress("DEPRECATION")
            android.telephony.SmsManager.getDefault()
        }

        var successCount = 0
        var failCount = 0

        customers.forEachIndexed { idx, cust ->
            val draftedSmsText = generateDynamicTemplate(
                index = idx,
                customerName = cust.name,
                totalDue = cust.totalDue,
                address = cust.address,
                todayStr = todayStr,
                nextStr = nextStr,
                shopName = shopName,
                shopPhone = shopPhone,
                ownerName = ownerName,
                isBn = isBn
            )

            val cleanPhone = cust.phone.replace(" ", "").replace("-", "")
            if (cleanPhone.isNotEmpty()) {
                try {
                    val parts = smsManager.divideMessage(draftedSmsText)
                    smsManager.sendMultipartTextMessage(cleanPhone, null, parts, null, null)
                    successCount++
                } catch (e: Exception) {
                    failCount++
                }
            } else {
                failCount++
            }
        }

        if (failCount == 0) {
            viewModel.showToast(
                if (isBn) "🎉 সফলভাবে সকল $successCount জন কাস্টমারকে সরাসরি এসএমএস পাঠানো হয়েছে!"
                else "🎉 Successfully sent direct cellular SMS to all $successCount customers!"
            )
        } else {
            viewModel.showToast(
                if (isBn) "সাফল্য: $successCount, ব্যর্থতা: $failCount। অনুগ্রহ করে চেক করুন।"
                else "Sent: $successCount, Failed: $failCount. Please inspect balances."
            )
        }
    } catch (e: Exception) {
        viewModel.showToast(
            if (isBn) "এসএমএস পাঠাতে সমস্যা তৈরি হয়েছে: ${e.localizedMessage}"
            else "SMS Send failed: ${e.localizedMessage}"
        )
    }
}

fun getSmsDates(isBn: Boolean): Pair<String, String> {
    val cal = java.util.Calendar.getInstance()
    val todayDate = cal.time
    cal.add(java.util.Calendar.DAY_OF_YEAR, 7)
    val nextSevenDaysDate = cal.time
    
    val format = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault())
    val todayStr = format.format(todayDate)
    val nextStr = format.format(nextSevenDaysDate)
    
    return if (isBn) {
        Pair(toBengaliDigits(todayStr), toBengaliDigits(nextStr))
    } else {
        Pair(todayStr, nextStr)
    }
}

fun toBengaliDigits(input: String): String {
    val englishDigits = listOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
    val bengaliDigits = listOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    var result = input
    englishDigits.forEachIndexed { index, char ->
        result = result.replace(char, bengaliDigits[index])
    }
    return result
}

fun generateDynamicTemplate(
    index: Int,
    customerName: String,
    totalDue: Double,
    address: String?,
    todayStr: String,
    nextStr: String,
    shopName: String,
    shopPhone: String,
    ownerName: String,
    isBn: Boolean
): String {
    val displayAmount = if (isBn) toBengaliDigits(String.format("%.1f", totalDue)) else String.format("%.1f", totalDue)
    val location = if (address.isNullOrBlank()) {
        if (isBn) "নির্দিষ্ট করা নেই" else "Not provided"
    } else address

    if (isBn) {
        return when (index % 3) {
            0 -> """
আসসালামু আলাইকুম, সম্মানিত $customerName ভাই/বোন।

$shopName থেকে আশা করি ভালো আছেন। আপনার বর্তমান বকেয়া বিলের পরিমাণ হচ্ছে ৳$displayAmount (ঠিকানা: $location)।

আজকের তারিখ: $todayStr। আপনার সুবিধার্থে আগামী $nextStr তারিখের মধ্যে এই বকেয়া টাকাটি পরিশোধ করার জন্য বিনীত অনুরোধ করছি। আপনার যেকোনো সমস্যায় আমরা পাশে আছি।

ধন্যবাদ ও শুভেচ্ছা সহ -
দোকানদারের নাম: $ownerName
মোবাইল: $shopPhone
            """.trimIndent()
            
            1 -> """
আসসালামু আলাইকুম, প্রিয় $customerName।

আপনার সার্বিক সুস্বাস্থ্য কামনা করছি। $shopName এ আপনার পূর্বের বকেয়া হিসাব ৳$displayAmount রয়েছে (এলাকা: $location)।

অনুরোধ সাপেক্ষে বকেয়াটি আগামী $nextStr তারিখের মধ্যে পরিশোধ করার জন্য বিনীতভাবে অনুরোধ করা হলো। আজ $todayStr থেকে আপনি পরিশোধের জন্য ৭ দিন সময় পাচ্ছেন।

শুভকামনায় -
দোকানদারের নাম: $ownerName
ফোন: $shopPhone
            """.trimIndent()
            
            else -> """
আসসালামু আলাইকুম, শ্রদ্ধেয় $customerName সাহেব।

ব্যবসায়িক সুসম্পর্ক বজায় রাখতে আমরা সদা সচেষ্ট। $shopName এ আপনার বর্তমান বকেয়া অ্যাকাউন্ট ব্যালেন্সটি হলো ৳$displayAmount (আপনার লোকেশন: $location)।

আগামী $nextStr তারিখের মধ্যে বকেয়া পরিশোধ করার জন্য আপনাকে বিশেষ অনুরোধ জানানো হচ্ছে। উল্লেখ্য যে, আজকের তারিখ $todayStr।

ধন্যবাদান্তে -
দোকানদারের নাম: $ownerName, $shopName
কন্টাক্ট: $shopPhone
            """.trimIndent()
        }
    } else {
        return when (index % 3) {
            0 -> """
Assalamu Alaikum, Dear $customerName.
                
Hope you are doing well from $shopName. Your current outstanding balance is ৳$displayAmount (Location: $location).
                
Today: $todayStr. We kindly request you to settle this balance by $nextStr for your convenience. We appreciate your continuation.
                
Best regards -
Shopkeeper: $ownerName
Phone: $shopPhone
            """.trimIndent()
            
            1 -> """
Assalamu Alaikum, Dear $customerName.
                
Wishing you good health. You have a pending credit ledger balance of ৳$displayAmount at $shopName (Area: $location).
                
You are kindly requested to clear the balance by $nextStr. You have 7 days starting from today, $todayStr.
                
Respectfully -
Shopkeeper: $ownerName
Contact: $shopPhone
            """.trimIndent()
            
            else -> """
Assalamu Alaikum, Dear $customerName.
                
We value our business relation. Your outstanding balance at $shopName is ৳$displayAmount (Location: $location).
                
Please clear this due by $nextStr. Note that today's date is $todayStr.
                
Thank you -
Shopkeeper: $ownerName, $shopName
Phone: $shopPhone
            """.trimIndent()
        }
    }
}

@Composable
fun CustomerRecordCard(
    customer: Customer,
    isBn: Boolean,
    colors: ColorScheme,
    onHistoryClick: () -> Unit,
    onDepositClick: () -> Unit,
    onNewDueClick: () -> Unit,
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
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (!customer.photoUri.isNullOrEmpty()) {
                        coil.compose.AsyncImage(
                            model = customer.photoUri,
                            contentDescription = "Customer Photo",
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = customer.name.firstOrNull()?.toString()?.uppercase() ?: "K",
                                color = colors.primary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = customer.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Black,
                            color = colors.onBackground
                        )
                        Text(
                            text = customer.phone,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onBackground.copy(alpha = 0.5f)
                        )
                    }
                }

                // Balance due
                Column(horizontalAlignment = Alignment.End) {
                    val isDeposit = customer.totalDue < 0
                    Text(
                        text = if (isDeposit) {
                            (if (isBn) "জমা (অগ্রিম)" else "Advance Deposit")
                        } else {
                            (if (isBn) "বাকি পরিমাণ" else "Unpaid Due")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onBackground.copy(alpha = 0.4f)
                    )
                    Text(
                        text = "৳ ${if (isDeposit) java.lang.Math.abs(customer.totalDue) else customer.totalDue}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isDeposit) Color(0xFF2E7D32) else if (customer.totalDue > 0) Color(0xFFD32F2F) else Color(0xFF2E7D32)
                    )
                }
            }

            if (customer.address != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = colors.onBackground.copy(alpha = 0.3f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = customer.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onBackground.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = colors.primary.copy(alpha = 0.5f),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isBn) "💡 লেনদেনের তারিখ ও বিস্তারিত স্টেটমেন্ট দেখতে এখানে চাপুন" else "💡 Click to view detailed statement history with exact dates",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp
                )
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp), color = colors.onBackground.copy(alpha = 0.05f))

            // Due actions row 1: Core transactions (Deposit and Due)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Deposit button
                Button(
                    onClick = onDepositClick,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary.copy(alpha = 0.09f), contentColor = colors.primary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                ) {
                    Text(
                        text = if (isBn) "+ জমা আদায়" else "+ Deposit",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Add baki button
                Button(
                    onClick = onNewDueClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFF9C4), contentColor = Color(0xFFAC8100)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                ) {
                    Text(
                        text = if (isBn) "+ বাকি লিখুন" else "+ Add Due",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Due actions row 2: AI utilities & profile management (AI SMS, Edit, Delete)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val hasSms = customer.totalDue != 0.0 && customer.phone.isNotBlank()
                
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
                            .testTag("btn_trigger_ai_sms_${customer.name.replace(" ", "_")}")
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
                        .testTag("btn_edit_customer_${customer.id}")
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
                        .testTag("btn_delete_customer_${customer.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete customer",
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
