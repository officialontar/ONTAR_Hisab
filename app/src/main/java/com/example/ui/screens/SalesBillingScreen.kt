package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.AppViewModel
import com.example.data.StockItem
import com.example.data.Customer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesBillingScreen(viewModel: AppViewModel) {
    val isBn by viewModel.isBengali.collectAsState()
    val stockList by viewModel.stockItems.collectAsState()
    val customerList by viewModel.customers.collectAsState()
    val colors = MaterialTheme.colorScheme

    // Selected States
    var selectedItem by remember { mutableStateOf<StockItem?>(null) }
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    var quantityText by remember { mutableStateOf("1") }

    // Dropdown open control states
    var itemDropdownExpanded by remember { mutableStateOf(false) }
    var customerDropdownExpanded by remember { mutableStateOf(false) }

    // Computations
    val quantity = quantityText.toIntOrNull() ?: 1
    val unitPrice = selectedItem?.salesPrice ?: 0.0
    val totalPrice = unitPrice * quantity

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Translator.get("sales_billing", isBn), fontWeight = FontWeight.Bold) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = Translator.get("billing", isBn),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = colors.primary
            )

            // PRODUCT SELECTION DROPDOWN
            Column {
                Text(
                    text = Translator.get("select_item", isBn),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onBackground.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Box {
                    Button(
                        onClick = { itemDropdownExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(55.dp)
                            .testTag("item_dropdown_trigger"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.surface, contentColor = colors.onSurface),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedItem?.let { "${it.name} (৳${it.salesPrice})" } ?: (if (isBn) "-- পণ্য সিলেক্ট করুন --" else "-- Select Stock Item --"),
                                fontWeight = FontWeight.Bold,
                                color = if (selectedItem == null) colors.onSurface.copy(alpha = 0.5f) else colors.onSurface
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }

                    DropdownMenu(
                        expanded = itemDropdownExpanded,
                        onDismissRequest = { itemDropdownExpanded = false },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .background(colors.surface)
                    ) {
                        if (stockList.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(if (isBn) "কোনো পণ্য স্টকে নেই" else "No items in stock") },
                                onClick = {}
                            )
                        } else {
                            stockList.forEach { item ->
                                val active = item.stockCount > 0
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(text = item.name, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = if (active) "৳${item.salesPrice} (${item.stockCount} ${if (isBn) "টি স্টকে" else "pcs"})"
                                                else (if (isBn) "স্টক খালি!" else "Out of stock!"),
                                                color = if (active) colors.primary else Color.Red,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    },
                                    onClick = {
                                        if (active) {
                                            selectedItem = item
                                        } else {
                                            viewModel.showToast(if (isBn) "পণ্যটি স্টকে নেই!" else "Zero stock available!")
                                        }
                                        itemDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // CUSTOMER SELECTION (OPTIONAL DROP DOWN FOR BAKI FLOW)
            Column {
                Text(
                    text = Translator.get("select_customer", isBn),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onBackground.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Box {
                    Button(
                        onClick = { customerDropdownExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(55.dp)
                            .testTag("customer_dropdown_trigger"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.surface, contentColor = colors.onSurface),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedCustomer?.name ?: Translator.get("cash_sale", isBn),
                                fontWeight = FontWeight.Bold,
                                color = if (selectedCustomer == null) colors.primary else colors.onSurface
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }

                    DropdownMenu(
                        expanded = customerDropdownExpanded,
                        onDismissRequest = { customerDropdownExpanded = false },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .background(colors.surface)
                    ) {
                        DropdownMenuItem(
                            text = { Text(Translator.get("cash_sale", isBn), fontWeight = FontWeight.Bold, color = colors.primary) },
                            onClick = {
                                selectedCustomer = null
                                customerDropdownExpanded = false
                            }
                        )

                        if (customerList.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(if (isBn) "বাকি খতিয়ানে কোনো কাস্টমার নেই" else "No customers registered yet") },
                                onClick = {}
                            )
                        } else {
                            customerList.forEach { cust ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(text = cust.name, fontWeight = FontWeight.Bold)
                                            Text(text = "${if (isBn) "বাকি" else "Due"}: ৳${cust.totalDue}", color = Color(0xFFC5A000))
                                        }
                                    },
                                    onClick = {
                                        selectedCustomer = cust
                                        customerDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // QUANTITY INPUT
            Column {
                Text(
                    text = Translator.get("sell_quantity", isBn),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onBackground.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_sell_quantity")
                )
            }

            // DYNAMIC INVOICE SLIP CARD
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isBn) "টালি বিবরণী (ক্রয় স্লিপ)" else "Calculated Invoice",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = if (isBn) "মালামালের মূল্য:" else "Unit Sales Price:")
                        Text(text = "৳ $unitPrice", fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = if (isBn) "বিক্রয়ের সংখ্যা:" else "Quantity:")
                        Text(text = "x $quantity", fontWeight = FontWeight.Bold)
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = colors.onBackground.copy(alpha = 0.08f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isBn) "সর্বমোট বিল:" else "Total Bill Amount:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "৳ $totalPrice",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = colors.primary
                        )
                    }

                    if (selectedCustomer != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFFF9C4)) // Soft Warning background
                                .padding(8.dp)
                        ) {
                            Text(
                                text = if (isBn) "⚠️ কাস্টমার বাকি বা বকেয়া খাতা নির্বাচন করেছেন। এই বিক্রিটি কাস্টমারের বাকি খতিয়ানে যোগ হবে।"
                                else "⚠️ Customer selected. This transaction will add directly into the customer's due ledger.",
                                color = Color(0xFFF57F17),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // SELL ACTION BUTTON
            Button(
                onClick = {
                    val item = selectedItem
                    if (item == null) {
                        viewModel.showToast(if (isBn) "দয়া করে বিক্রি করতে পণ্য নির্বাচন করুন" else "Please select product first")
                        return@Button
                    }
                    if (quantity <= 0) {
                        viewModel.showToast(if (isBn) "সঠিক পরিমাণ প্রবেশ করুন" else "Invalid quantity input")
                        return@Button
                    }
                    viewModel.recordSale(item, quantity, selectedCustomer?.id)

                    // reset fields
                    selectedItem = null
                    selectedCustomer = null
                    quantityText = "1"
                    viewModel.navigateTo("DASHBOARD")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp)
                    .testTag("btn_sales_record_submit"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = Translator.get("sell_now", isBn),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
