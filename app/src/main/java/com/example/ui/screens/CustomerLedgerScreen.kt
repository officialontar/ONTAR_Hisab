package com.example.ui.screens

import android.content.Context
import android.content.Intent
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerLedgerScreen(viewModel: AppViewModel) {
    val isBn by viewModel.isBengali.collectAsState()
    val customersList by viewModel.customers.collectAsState()
    val colors = MaterialTheme.colorScheme

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

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
                                    viewModel.deleteCustomer(customer)
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

            // Due actions bar holding transactions on left and edit/delete on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Deposit button
                    Button(
                        onClick = onDepositClick,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary.copy(alpha = 0.09f), contentColor = colors.primary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
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
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(
                            text = if (isBn) "+ বাকি লিখুন" else "+ Add Due",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Gemini AI SMS Draft
                    if (customer.totalDue != 0.0) {
                        Button(
                            onClick = onRemindClick,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceVariant, contentColor = colors.primary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("btn_trigger_ai_sms_${customer.name.replace(" ", "_")}")
                        ) {
                            Icon(Icons.Default.Info, null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "এআই এসএমএস",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Edit Profile & Delete customer options on right side
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Edit Profile option
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("btn_edit_customer_${customer.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit profile",
                            tint = colors.primary.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Delete customer option
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete customer",
                            tint = colors.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
