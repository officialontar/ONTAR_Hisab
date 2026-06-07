package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.AppViewModel
import com.example.data.StockItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockManagerScreen(viewModel: AppViewModel) {
    val isBn by viewModel.isBengali.collectAsState()
    val stockList by viewModel.stockItems.collectAsState()
    val colors = MaterialTheme.colorScheme

    // Forms
    var showAddDialog by remember { mutableStateOf(false) }
    var itemName by remember { mutableStateOf("") }
    var buyPriceText by remember { mutableStateOf("") }
    var sellPriceText by remember { mutableStateOf("") }
    var quantityText by remember { mutableStateOf("") }
    var categoryText by remember { mutableStateOf("") }
    var selectedUnitText by remember { mutableStateOf("পিস") }
    var stockPhotoUri by remember { mutableStateOf("") }

    // Search query state
    var searchQuery by remember { mutableStateOf("") }

    // Edit item profile state
    var itemToEditProfile by remember { mutableStateOf<StockItem?>(null) }
    var editItemName by remember { mutableStateOf("") }
    var editItemBuyPrice by remember { mutableStateOf("") }
    var editItemSellPrice by remember { mutableStateOf("") }
    var editItemQuantity by remember { mutableStateOf("") }
    var editItemCategory by remember { mutableStateOf("") }
    var editItemUnit by remember { mutableStateOf("পিস") }
    var editItemPhotoUri by remember { mutableStateOf("") }

    // Filter stock list dynamically
    val filteredStockList = remember(stockList, searchQuery) {
        if (searchQuery.isBlank()) {
            stockList
        } else {
            val q = searchQuery.trim().lowercase()
            stockList.filter { item ->
                item.name.lowercase().contains(q)
            }
        }
    }

    val stockPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            stockPhotoUri = uri.toString()
        }
    }

    val editStockPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            editItemPhotoUri = uri.toString()
        }
    }

    // Edit Item Count State
    var selectedItemForEditCount by remember { mutableStateOf<StockItem?>(null) }
    var editCountText by remember { mutableStateOf("") }

    val categories = listOf(
        if (isBn) "মুদি মাল" else "Grocery",
        if (isBn) "তেল ও ঘি" else "Oil & Ghee",
        if (isBn) "ডাল ও চাল" else "Dal & Rice",
        if (isBn) "পানীয় ও স্ন্যাক্স" else "Beverage & Snack",
        if (isBn) "কসমেটিকস" else "Cosmetics",
        if (isBn) "অন্যান্য" else "Others"
    )
    var selectedCategoryIndex by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Translator.get("stock_manager", isBn), fontWeight = FontWeight.Bold) },
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
                    itemName = ""
                    buyPriceText = ""
                    sellPriceText = ""
                    quantityText = ""
                    categoryText = ""
                    selectedUnitText = "পিস"
                    stockPhotoUri = ""
                    showAddDialog = true
                },
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                modifier = Modifier.testTag("fab_add_item")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Item")
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
                // Search bar for Filtering Stock
                if (stockList.isNotEmpty()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(if (isBn) "পণ্যের নাম দিয়ে খুঁজুন..." else "Search product by name...") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = colors.primary) }, // using Edit as simple search representation or Search icon
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Clear") // fallback close indicator
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("stock_search_bar")
                    )
                }

                if (stockList.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = colors.onBackground.copy(alpha = 0.3f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isBn) "খাতায় কোনো পণ্য স্টক করা নেই!" else "No products in stock!",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onBackground.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isBn) "নিচের (+) বাটনে চাপ দিয়ে ডেমো বা আসল পণ্য স্টক করুন" else "Tap (+) below to add dynamic products to your ledger",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onBackground.copy(alpha = 0.4f),
                            modifier = Modifier.padding(top = 4.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else if (filteredStockList.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = colors.onBackground.copy(alpha = 0.3f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isBn) "কোনো ম্যাচিং পণ্য পাওয়া যায়নি!" else "No matching products found!",
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

                        items(filteredStockList) { item ->
                            StockItemCard(
                                item = item,
                                isBn = isBn,
                                colors = colors,
                                onUpdateCountClick = {
                                    selectedItemForEditCount = item
                                    editCountText = item.stockCount.toString()
                                },
                                onEditProfileClick = {
                                    itemToEditProfile = item
                                    editItemName = item.name
                                    editItemBuyPrice = item.purchasePrice.toString()
                                    editItemSellPrice = item.salesPrice.toString()
                                    editItemQuantity = item.stockCount.toString()
                                    editItemCategory = item.category
                                    editItemUnit = item.unit
                                    editItemPhotoUri = item.imageResName ?: ""
                                },
                                onDeleteClick = {
                                    viewModel.deleteStockItem(item)
                                }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }

            // ADD STOCK DIALOG
            if (showAddDialog) {
                AlertDialog(
                    onDismissRequest = { showAddDialog = false },
                    title = { Text(Translator.get("add_item", isBn), fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = itemName,
                                onValueChange = { itemName = it },
                                label = { Text(Translator.get("item_name", isBn)) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("input_item_name")
                            )

                            OutlinedTextField(
                                value = buyPriceText,
                                onValueChange = { buyPriceText = it },
                                label = { Text(Translator.get("buy_price", isBn)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("input_buy_price")
                            )

                            OutlinedTextField(
                                value = sellPriceText,
                                onValueChange = { sellPriceText = it },
                                label = { Text(Translator.get("sell_price", isBn)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("input_sell_price")
                            )

                            OutlinedTextField(
                                value = quantityText,
                                onValueChange = { quantityText = it },
                                label = { Text(Translator.get("quantity", isBn)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("input_quantity")
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Unit of Measurement selector
                            val unitOptions = remember { listOf("পিস", "কেজি", "লিটার", "গ্রাম", "প্যাকেট", "কার্টুন", "অন্যান্য") }

                            Text(
                                text = if (isBn) "পরিমাপক ইউনিট" else "Unit of Measurement",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onBackground.copy(alpha = 0.5f),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                unitOptions.forEach { unit ->
                                    val isSelected = selectedUnitText == unit
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) colors.primary else colors.onBackground.copy(alpha = 0.05f))
                                             .clickable { selectedUnitText = unit }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = unit,
                                             color = if (isSelected) colors.onPrimary else colors.onBackground,
                                             fontSize = 11.sp,
                                             fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Category chips
                            Text(
                                text = Translator.get("category", isBn),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onBackground.copy(alpha = 0.5f),
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                ) {
                                    items(categories.chunked(2)) { pair ->
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            for (cat in pair) {
                                                val selected = categoryText == cat
                                                Box(
                                                    modifier = Modifier
                                                        .padding(2.dp)
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (selected) colors.primary else colors.onBackground.copy(alpha = 0.05f))
                                                        .clickable { categoryText = cat }
                                                        .padding(vertical = 8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = cat,
                                                        color = if (selected) colors.onPrimary else colors.onBackground,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Product photo selector block
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.primary.copy(alpha = 0.05f))
                                    .padding(8.dp)
                            ) {
                                if (stockPhotoUri.isNotEmpty()) {
                                    coil.compose.AsyncImage(
                                        model = stockPhotoUri,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colors.primary.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Add, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isBn) "পণ্যের ছবি" else "Product Image",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (stockPhotoUri.isEmpty()) {
                                            (if (isBn) "ছবি দিন (ঐচ্ছিক)" else "Add Photo")
                                        } else {
                                            (if (isBn) "ছবি সিলেক্ট করা হয়েছে" else "Selected")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onBackground.copy(alpha = 0.5f)
                                    )
                                }

                                IconButton(
                                    onClick = { stockPhotoLauncher.launch("image/*") },
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
                                val buy = buyPriceText.toDoubleOrNull() ?: 0.0
                                val sell = sellPriceText.toDoubleOrNull() ?: 0.0
                                val quant = quantityText.toIntOrNull() ?: 0
                                val photoVal = if (stockPhotoUri.isBlank()) null else stockPhotoUri
                                viewModel.addStockItem(
                                    name = itemName.trim(),
                                    buyPrice = buy,
                                    sellPrice = sell,
                                    quantity = quant,
                                    category = if (categoryText.isBlank()) categories[0] else categoryText,
                                    unit = selectedUnitText,
                                    photoUri = photoVal
                                )
                                showAddDialog = false
                            },
                            modifier = Modifier.testTag("btn_save_item")
                        ) {
                            Text(Translator.get("add_btn", isBn))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddDialog = false }) {
                            Text(if (isBn) "বাতিল" else "Cancel")
                        }
                    }
                )
            }

            // COUNT UPDATE DIALOG
            if (selectedItemForEditCount != null) {
                AlertDialog(
                    onDismissRequest = { selectedItemForEditCount = null },
                    title = { Text(if (isBn) "স্টক আপডেট" else "Update Stock Count", fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text(
                                text = selectedItemForEditCount?.name ?: "",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = editCountText,
                                onValueChange = { editCountText = it },
                                label = { Text(Translator.get("quantity", isBn)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_edit_count")
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val item = selectedItemForEditCount
                                val newCount = editCountText.toIntOrNull()
                                if (item != null && newCount != null) {
                                    viewModel.updateStockItemCount(item, newCount)
                                }
                                selectedItemForEditCount = null
                            },
                            modifier = Modifier.testTag("btn_confirm_edit_count")
                        ) {
                            Text(Translator.get("save_btn", isBn))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { selectedItemForEditCount = null }) {
                            Text(if (isBn) "বাতিল" else "Cancel")
                        }
                    }
                )
            }

            // FULL PROFILE EDIT STOCK ITEM DIALOG
            if (itemToEditProfile != null) {
                AlertDialog(
                    onDismissRequest = { itemToEditProfile = null },
                    title = { Text(if (isBn) "পণ্যের প্রোফাইল সম্পাদন" else "Edit Product Profile", fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = editItemName,
                                onValueChange = { editItemName = it },
                                label = { Text(if (isBn) "পণ্যের নাম" else "Product Name") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("edit_item_name")
                            )

                            OutlinedTextField(
                                value = editItemBuyPrice,
                                onValueChange = { editItemBuyPrice = it },
                                label = { Text(Translator.get("buy_price", isBn)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("edit_item_buy_price")
                            )

                            OutlinedTextField(
                                value = editItemSellPrice,
                                onValueChange = { editItemSellPrice = it },
                                label = { Text(Translator.get("sell_price", isBn)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("edit_item_sell_price")
                            )

                            OutlinedTextField(
                                value = editItemQuantity,
                                onValueChange = { editItemQuantity = it },
                                label = { Text(Translator.get("quantity", isBn)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("edit_item_quantity")
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Unit of Measurement selector inside Edit Dialog
                            val editUnitOptions = remember { listOf("পিস", "কেজি", "লিটার", "গ্রাম", "প্যাকেট", "কার্টুন", "অন্যান্য") }
                            Text(
                                text = if (isBn) "পরিমাপক ইউনিট" else "Unit of Measurement",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onBackground.copy(alpha = 0.5f),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                editUnitOptions.forEach { unit ->
                                    val isSelected = editItemUnit == unit
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) colors.primary else colors.onBackground.copy(alpha = 0.05f))
                                            .clickable { editItemUnit = unit }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = unit,
                                            color = if (isSelected) colors.onPrimary else colors.onBackground,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Category Option Selector inside Edit Dialog
                            Text(
                                text = Translator.get("category", isBn),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onBackground.copy(alpha = 0.5f),
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().height(100.dp)
                            ) {
                                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                    items(categories.chunked(2)) { pair ->
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            for (cat in pair) {
                                                val selected = editItemCategory == cat
                                                Box(
                                                    modifier = Modifier
                                                        .padding(2.dp)
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (selected) colors.primary else colors.onBackground.copy(alpha = 0.05f))
                                                        .clickable { editItemCategory = cat }
                                                        .padding(vertical = 8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = cat,
                                                        color = if (selected) colors.onPrimary else colors.onBackground,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Product Photo inside Edit
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.primary.copy(alpha = 0.05f))
                                    .padding(8.dp)
                            ) {
                                if (editItemPhotoUri.isNotEmpty()) {
                                    coil.compose.AsyncImage(
                                        model = editItemPhotoUri,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colors.primary.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Add, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isBn) "পণ্যের ছবি" else "Product Image",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (editItemPhotoUri.isEmpty()) "কোনো ছবি দেয়া নেই" else "ছবি সিলেক্ট করা হয়েছে",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onBackground.copy(alpha = 0.5f)
                                    )
                                }

                                IconButton(
                                    onClick = { editStockPhotoLauncher.launch("image/*") },
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
                                val current = itemToEditProfile
                                val buy = editItemBuyPrice.toDoubleOrNull() ?: 0.0
                                val sell = editItemSellPrice.toDoubleOrNull() ?: 0.0
                                val quant = editItemQuantity.toIntOrNull() ?: 0
                                if (current != null && editItemName.isNotBlank()) {
                                    viewModel.updateStockItemProfile(
                                        item = current,
                                        name = editItemName.trim(),
                                        buyPrice = buy,
                                        sellPrice = sell,
                                        quantity = quant,
                                        category = editItemCategory,
                                        unit = editItemUnit,
                                        photoUri = if (editItemPhotoUri.isBlank()) null else editItemPhotoUri
                                    )
                                    itemToEditProfile = null
                                } else {
                                    viewModel.showToast(if (isBn) "নাম ইনপুট করা আবশ্যক!" else "Name is required!")
                                }
                            },
                            modifier = Modifier.testTag("btn_save_edit_item")
                        ) {
                            Text(Translator.get("save_btn", isBn))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { itemToEditProfile = null }) {
                            Text(if (isBn) "বাতিল" else "Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun StockItemCard(
    item: StockItem,
    isBn: Boolean,
    colors: ColorScheme,
    onUpdateCountClick: () -> Unit,
    onEditProfileClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val isOutOfStock = item.stockCount <= 0
    val isLowStock = item.stockCount > 0 && item.stockCount < 5

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left - Thumbnail (if loaded)
            if (!item.imageResName.isNullOrEmpty()) {
                coil.compose.AsyncImage(
                    model = item.imageResName,
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .padding(end = 12.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.primary.copy(alpha = 0.08f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "${if (isBn) "বিক্রয় মূল্য" else "Sell"}: ৳${item.salesPrice}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onBackground.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                    Text(
                        text = "${if (isBn) "ক্রয় মূল্য" else "Buy"}: ৳${item.purchasePrice}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onBackground.copy(alpha = 0.4f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Stock details with customized alert color backgrounds
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val alertColor = when {
                        isOutOfStock -> Color(0xFFD32F2F)  // Danger Red
                        isLowStock -> Color(0xFFE65100)    // Alert Orange
                        else -> Color(0xFF2E7D32)          // Healthy Green
                    }
                    val alertText = when {
                        isOutOfStock -> Translator.get("out_of_stock", isBn)
                        isLowStock -> Translator.get("low_stock", isBn)
                        else -> Translator.get("in_stock", isBn)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(alertColor.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = alertText,
                            color = alertColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "${if (isBn) "স্টক:" else "Stock:"} ${item.stockCount} ${item.unit}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onBackground
                    )
                }
            }

            // Quick stocks actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Update full profile button
                IconButton(
                    onClick = onEditProfileClick,
                    modifier = Modifier.background(colors.secondaryContainer, RoundedCornerShape(8.dp)).testTag("edit_item_profile_btn_${item.id}")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Profile", tint = colors.onSecondaryContainer)
                }
                Spacer(modifier = Modifier.width(4.dp))
                // Quick count update button
                IconButton(
                    onClick = onUpdateCountClick,
                    modifier = Modifier.background(colors.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Quick stock counts adjustment", tint = colors.primary)
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.background(colors.errorContainer.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete item", tint = colors.error)
                }
            }
        }
    }
}
