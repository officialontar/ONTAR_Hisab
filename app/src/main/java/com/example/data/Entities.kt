package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "users")
data class User(
    @PrimaryKey val email: String,
    val shopName: String,
    val phone: String,
    val passwordHash: String,
    val profilePicture: String? = null,
    val ownerName: String? = null,
    val shopPicture: String? = null
) : Serializable

@Entity(tableName = "stock_items")
data class StockItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userEmail: String,
    val name: String,
    val purchasePrice: Double,
    val salesPrice: Double,
    val stockCount: Int,
    val category: String,
    val imageResName: String? = null,
    val unit: String = "পিস"
) : Serializable

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userEmail: String,
    val name: String,
    val phone: String,
    val address: String? = null,
    val totalDue: Double = 0.0,
    val photoUri: String? = null
) : Serializable

@Entity(tableName = "dealers")
data class Dealer(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userEmail: String,
    val name: String,
    val phone: String,
    val company: String? = null,
    val totalOwed: Double = 0.0,
    val photoUri: String? = null
) : Serializable

@Entity(tableName = "transactions")
data class TransactionRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userEmail: String,
    val type: String, // "SALE", "EXPENSE", "CUSTOMER_DUE", "CUSTOMER_PAYMENT", "DEALER_PAYMENT"
    val amount: Double,
    val profit: Double = 0.0,
    val title: String,
    val description: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val customerId: Int? = null,
    val dealerId: Int? = null
) : Serializable
