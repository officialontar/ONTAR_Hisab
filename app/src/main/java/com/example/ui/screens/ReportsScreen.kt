package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.AppViewModel
import com.example.data.TransactionRecord
import java.util.Calendar
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: AppViewModel) {
    val isBn by viewModel.isBengali.collectAsState()
    val transactionsList by viewModel.transactions.collectAsState()
    val customersList by viewModel.customers.collectAsState()
    val dealersList by viewModel.dealers.collectAsState()
    val colors = MaterialTheme.colorScheme

    // Report Period: "DAILY", "WEEKLY", "MONTHLY", "YEARLY"
    var selectedPeriod by remember { mutableStateOf("DAILY") }

    // Manual Expense Dialog State
    var showExpenseDialog by remember { mutableStateOf(false) }
    var expenseName by remember { mutableStateOf("") }
    var expenseAmountText by remember { mutableStateOf("") }
    var expenseNote by remember { mutableStateOf("") }

    // Filter transaction lists based on the selected time boundary
    val calendar = Calendar.getInstance()
    val nowMillis = System.currentTimeMillis()

    val filteredTxs = transactionsList.filter { tx ->
        val diffMillis = nowMillis - tx.timestamp
        when (selectedPeriod) {
            "DAILY" -> diffMillis <= (24 * 3600 * 1000) // last 24 hours
            "WEEKLY" -> diffMillis <= (7 * 24 * 3600 * 1000) // last 7 days
            "MONTHLY" -> diffMillis <= (30L * 24 * 3600 * 1000) // last 30 days
            "YEARLY" -> diffMillis <= (365L * 24 * 3600 * 1000) // last 365 days
            else -> true
        }
    }

    // Performance Index Computations
    val salesVal = filteredTxs.filter { it.type == "SALE" }.sumOf { it.amount }
    val expenseVal = filteredTxs.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    val depositsVal = filteredTxs.filter { it.type == "CUSTOMER_PAYMENT" }.sumOf { it.amount }
    val profitVal = filteredTxs.sumOf { it.profit }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Translator.get("reports", isBn), fontWeight = FontWeight.Bold) },
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
        ) {
            // Selectable period tabs Row
            Row(
                modifier = Modifier
                    .fillPaddingAndBackground()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.onBackground.copy(alpha = 0.05f))
                    .padding(4.dp)
            ) {
                listOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY").forEach { period ->
                    val isSelected = selectedPeriod == period
                    val label = when (period) {
                        "DAILY" -> Translator.get("daily", isBn)
                        "WEEKLY" -> Translator.get("weekly", isBn)
                        "MONTHLY" -> Translator.get("monthly", isBn)
                        else -> Translator.get("yearly", isBn)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) colors.primary else Color.Transparent)
                            .clickable { selectedPeriod = period }
                            .padding(vertical = 10.dp)
                            .testTag("tab_period_$period"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) colors.onPrimary else colors.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Large Metrics Slip Card for calculations
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        shape = RoundedCornerShape(20.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DateRange, null, tint = colors.primary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = Translator.get("net_health", isBn),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = colors.primary
                                )
                            }

                            Divider(modifier = Modifier.padding(vertical = 12.dp), color = colors.onBackground.copy(alpha = 0.05f))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = Translator.get("sales_summary", isBn), color = colors.onBackground.copy(alpha = 0.6f))
                                Text(text = "৳ $salesVal", fontWeight = FontWeight.Bold, color = colors.primary)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = Translator.get("expense_summary", isBn), color = colors.onBackground.copy(alpha = 0.6f))
                                Text(text = "৳ $expenseVal", fontWeight = FontWeight.Bold, color = colors.error)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = if (isBn) "বাকি জমা রিসিভ" else "Dues Collected", color = colors.onBackground.copy(alpha = 0.6f))
                                Text(text = "৳ $depositsVal", fontWeight = FontWeight.Bold, color = Color(0xFF0F9D58))
                            }

                            Divider(modifier = Modifier.padding(vertical = 12.dp), color = colors.onBackground.copy(alpha = 0.08f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = Translator.get("profit_summary", isBn),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "৳ $profitVal",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = if (profitVal >= 0) colors.primary else colors.error,
                                    modifier = Modifier.testTag("report_net_profit")
                                )
                            }
                        }
                    }
                }

                // Circular visual dashboard graph of this period's cash and dues
                item {
                    val newDuesVal = filteredTxs.filter { it.type == "CUSTOMER_DUE" }.sumOf { it.amount }
                    FinancialDonutChart(
                        sales = salesVal,
                        expenses = expenseVal,
                        duesCollected = depositsVal,
                        newDues = newDuesVal,
                        isBn = isBn,
                        colors = colors
                    )
                }

                // Add manual expense button
                item {
                    Button(
                        onClick = {
                            expenseName = ""
                            expenseAmountText = ""
                            expenseNote = ""
                            showExpenseDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_log_manual_expense"),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.error.copy(alpha = 0.1f), contentColor = colors.error),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = Translator.get("expense_btn", isBn),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Header for itemized statements
                item {
                    Text(
                        text = if (isBn) "এই সময়কালের লেনদেনসমূহ (${filteredTxs.size} টি)" else "Period Itemized Statements (${filteredTxs.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (filteredTxs.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.surface.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = if (isBn) "এই পরিধির ভেতরে কোনো হিসাব-নিকাশ পাওয়া যায়নি!" else "No grocery statements found in this range!",
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

                items(filteredTxs) { tx ->
                    ActivityLogItem(tx = tx, isBn = isBn, colors = colors, customersList = customersList, dealersList = dealersList)
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }

        // EXPENSE LOGGING MODAL DIALOG
        if (showExpenseDialog) {
            AlertDialog(
                onDismissRequest = { showExpenseDialog = false },
                title = { Text(Translator.get("expense_btn", isBn), fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = expenseName,
                            onValueChange = { expenseName = it },
                            label = { Text(Translator.get("expense_title", isBn)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .testTag("input_expense_title")
                        )

                        OutlinedTextField(
                            value = expenseAmountText,
                            onValueChange = { expenseAmountText = it },
                            label = { Text(Translator.get("buy_price", isBn)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .testTag("input_expense_amount")
                        )

                        OutlinedTextField(
                            value = expenseNote,
                            onValueChange = { expenseNote = it },
                            label = { Text(if (isBn) "অতিরিক্ত নোট (ঐচ্ছিক)" else "Notes / description (optional)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val amt = expenseAmountText.toDoubleOrNull() ?: 0.0
                            if (expenseName.isNotBlank() && amt > 0) {
                                viewModel.recordExpense(expenseName.trim(), amt, expenseNote.trim())
                            }
                            showExpenseDialog = false
                        },
                        modifier = Modifier.testTag("btn_save_manual_expense")
                    ) {
                        Text(Translator.get("save_btn", isBn))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExpenseDialog = false }) {
                        Text(if (isBn) "বাতিল" else "Cancel")
                    }
                }
            )
        }
    }
}

// Inline extension to avoid clean formatting warnings
fun Modifier.fillPaddingAndBackground() = this.fillMaxWidth()


