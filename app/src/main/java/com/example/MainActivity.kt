package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.ui.screens.*
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppViewModel
import com.example.viewmodel.AppViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Database, Repository, and ViewModel Factory
        val database = AppDatabase.getDatabase(this)
        val repository = AppRepository(database.appDao())
        val viewModelFactory = AppViewModelFactory(repository)
        val viewModel = ViewModelProvider(this, viewModelFactory)[AppViewModel::class.java]

        setContent {
            val isDarkUser by viewModel.isDarkMode.collectAsState()

            MyApplicationTheme(darkTheme = isDarkUser) {
                // Shared Toast Notification System
                val toastMsg by viewModel.toastMessage.collectAsState()
                LaunchedEffect(toastMsg) {
                    toastMsg?.let {
                        Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                        viewModel.clearToast()
                    }
                }

                // Lightweight Core Screen Navigator
                val currentScreen by viewModel.currentScreen.collectAsState()
                val isBn by viewModel.isBengali.collectAsState()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (currentScreen != "LOGIN") {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 8.dp
                            ) {
                                NavigationBarItem(
                                    selected = currentScreen == "CUSTOMER_LEDGER",
                                    onClick = { viewModel.navigateTo("CUSTOMER_LEDGER") },
                                    icon = { Icon(Icons.Default.AccountCircle, null) },
                                    label = { Text(if (isBn) "টালি" else "Tally", fontWeight = FontWeight.Bold) }
                                )
                                NavigationBarItem(
                                    selected = currentScreen == "SALES_BILLING",
                                    onClick = { viewModel.navigateTo("SALES_BILLING") },
                                    icon = { Icon(Icons.Default.ShoppingCart, null) },
                                    label = { Text(if (isBn) "ক্যাশবক্স" else "Cash Box", fontWeight = FontWeight.Bold) }
                                )
                                NavigationBarItem(
                                    selected = currentScreen == "DEALER_LEDGER",
                                    onClick = { viewModel.navigateTo("DEALER_LEDGER") },
                                    icon = { Icon(Icons.Default.Home, null) },
                                    label = { Text(if (isBn) "ওয়ালেট" else "Wallet", fontWeight = FontWeight.Bold) }
                                )
                                NavigationBarItem(
                                    selected = currentScreen == "DASHBOARD" || currentScreen == "REPORTS" || currentScreen == "STOCK_MANAGER" || currentScreen == "AI_COACH",
                                    onClick = { viewModel.navigateTo("DASHBOARD") },
                                    icon = { Icon(Icons.Default.Menu, null) },
                                    label = { Text(if (isBn) "মেনু বার" else "Menu Bar", fontWeight = FontWeight.Bold) }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        when (currentScreen) {
                            "LOGIN" -> AuthScreen(viewModel)
                            "DASHBOARD" -> MainDashboard(viewModel)
                            "STOCK_MANAGER" -> StockManagerScreen(viewModel)
                            "SALES_BILLING" -> SalesBillingScreen(viewModel)
                            "CUSTOMER_LEDGER" -> CustomerLedgerScreen(viewModel)
                            "DEALER_LEDGER" -> DealerLedgerScreen(viewModel)
                            "REPORTS" -> ReportsScreen(viewModel)
                            "AI_COACH" -> AiCoachScreen(viewModel)
                            else -> AuthScreen(viewModel)
                        }
                    }
                }
            }
        }
    }
}
