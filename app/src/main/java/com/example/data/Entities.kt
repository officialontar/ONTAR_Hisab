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

data class OwnerInfo(
    val name: String,
    val phone: String,
    val email: String
) : Serializable

object OwnerParser {
    fun serialize(owners: List<OwnerInfo>): String {
        val array = org.json.JSONArray()
        owners.forEach { o ->
            val obj = org.json.JSONObject()
            obj.put("name", o.name)
            obj.put("phone", o.phone)
            obj.put("email", o.email)
            array.put(obj)
        }
        return array.toString()
    }

    fun deserialize(ownerNameStr: String?, defaultPhone: String = "", defaultEmail: String = ""): List<OwnerInfo> {
        if (ownerNameStr != null && ownerNameStr.trim().startsWith("[") && ownerNameStr.trim().endsWith("]")) {
            try {
                val list = mutableListOf<OwnerInfo>()
                val array = org.json.JSONArray(ownerNameStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(OwnerInfo(
                        name = obj.optString("name", ""),
                        phone = obj.optString("phone", ""),
                        email = obj.optString("email", "")
                    ))
                }
                if (list.isNotEmpty()) return list
            } catch (e: Exception) {
                // fallback
            }
        }
        val safeName = ownerNameStr ?: ""
        return listOf(OwnerInfo(name = safeName, phone = defaultPhone, email = defaultEmail))
    }

    fun getFirstOwnerName(ownerNameStr: String?, defaultVal: String = ""): String {
        val list = deserialize(ownerNameStr)
        return list.firstOrNull()?.name?.ifBlank { defaultVal } ?: defaultVal
    }

    fun getFirstOwnerPhone(ownerNameStr: String?, defaultPhone: String = ""): String {
        val list = deserialize(ownerNameStr, defaultPhone)
        return list.firstOrNull()?.phone?.ifBlank { defaultPhone } ?: defaultPhone
    }
}


