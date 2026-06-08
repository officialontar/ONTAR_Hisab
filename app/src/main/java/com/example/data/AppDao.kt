package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // --- USER QUERIES ---
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUser(email: String): User?

    @Query("SELECT * FROM users WHERE email = :identifier OR phone = :identifier LIMIT 1")
    suspend fun getUserByIdentifier(identifier: String): User?

    @Query("SELECT * FROM users WHERE email = :rootEmail OR email LIKE :rootEmail || '#%'")
    suspend fun getAllShopsOfUser(rootEmail: String): List<User>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun registerUser(user: User)

    @Update
    suspend fun updateUser(user: User)

    @Query("UPDATE stock_items SET userEmail = :newEmail WHERE userEmail = :oldEmail")
    suspend fun updateStockItemEmail(oldEmail: String, newEmail: String)

    @Query("UPDATE customers SET userEmail = :newEmail WHERE userEmail = :oldEmail")
    suspend fun updateCustomerEmail(oldEmail: String, newEmail: String)

    @Query("UPDATE dealers SET userEmail = :newEmail WHERE userEmail = :oldEmail")
    suspend fun updateDealerEmail(oldEmail: String, newEmail: String)

    @Query("UPDATE transactions SET userEmail = :newEmail WHERE userEmail = :oldEmail")
    suspend fun updateTransactionEmail(oldEmail: String, newEmail: String)

    @Query("DELETE FROM users WHERE email = :oldEmail")
    suspend fun deleteUserByEmail(oldEmail: String)

    // --- STOCK QUERIES ---
    @Query("SELECT * FROM stock_items WHERE userEmail = :userEmail ORDER BY name ASC")
    fun getStockItemsOfUser(userEmail: String): Flow<List<StockItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockItem(item: StockItem)

    @Update
    suspend fun updateStockItem(item: StockItem)

    @Delete
    suspend fun deleteStockItem(item: StockItem)

    // --- CUSTOMER QUERIES ---
    @Query("SELECT * FROM customers WHERE userEmail = :userEmail ORDER BY name ASC")
    fun getCustomersOfUser(userEmail: String): Flow<List<Customer>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: Customer)

    @Update
    suspend fun updateCustomer(customer: Customer)

    @Delete
    suspend fun deleteCustomer(customer: Customer)

    // --- DEALER QUERIES ---
    @Query("SELECT * FROM dealers WHERE userEmail = :userEmail ORDER BY name ASC")
    fun getDealersOfUser(userEmail: String): Flow<List<Dealer>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDealer(dealer: Dealer)

    @Update
    suspend fun updateDealer(dealer: Dealer)

    @Delete
    suspend fun deleteDealer(dealer: Dealer)

    // --- TRANSACTION QUERIES ---
    @Query("SELECT * FROM transactions WHERE userEmail = :userEmail ORDER BY timestamp DESC")
    fun getTransactionsOfUser(userEmail: String): Flow<List<TransactionRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionRecord)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionRecord)
}
