package com.example.data

import kotlinx.coroutines.flow.Flow

class AppRepository(private val appDao: AppDao) {

    suspend fun getUser(email: String): User? = appDao.getUser(email)

    suspend fun getUserByIdentifier(identifier: String): User? = appDao.getUserByIdentifier(identifier)

    suspend fun getAllShopsOfUser(rootEmail: String): List<User> = appDao.getAllShopsOfUser(rootEmail)

    suspend fun registerUser(user: User) = appDao.registerUser(user)

    suspend fun updateUser(user: User) = appDao.updateUser(user)

    suspend fun updateStockItemEmail(oldEmail: String, newEmail: String) = appDao.updateStockItemEmail(oldEmail, newEmail)

    suspend fun updateCustomerEmail(oldEmail: String, newEmail: String) = appDao.updateCustomerEmail(oldEmail, newEmail)

    suspend fun updateDealerEmail(oldEmail: String, newEmail: String) = appDao.updateDealerEmail(oldEmail, newEmail)

    suspend fun updateTransactionEmail(oldEmail: String, newEmail: String) = appDao.updateTransactionEmail(oldEmail, newEmail)

    suspend fun deleteUserByEmail(oldEmail: String) = appDao.deleteUserByEmail(oldEmail)

    fun getStockItems(userEmail: String): Flow<List<StockItem>> = appDao.getStockItemsOfUser(userEmail)

    suspend fun insertStockItem(item: StockItem) = appDao.insertStockItem(item)

    suspend fun updateStockItem(item: StockItem) = appDao.updateStockItem(item)

    suspend fun deleteStockItem(item: StockItem) = appDao.deleteStockItem(item)

    fun getCustomers(userEmail: String): Flow<List<Customer>> = appDao.getCustomersOfUser(userEmail)

    suspend fun insertCustomer(customer: Customer) = appDao.insertCustomer(customer)

    suspend fun updateCustomer(customer: Customer) = appDao.updateCustomer(customer)

    suspend fun deleteCustomer(customer: Customer) = appDao.deleteCustomer(customer)

    fun getDealers(userEmail: String): Flow<List<Dealer>> = appDao.getDealersOfUser(userEmail)

    suspend fun insertDealer(dealer: Dealer) = appDao.insertDealer(dealer)

    suspend fun updateDealer(dealer: Dealer) = appDao.updateDealer(dealer)

    suspend fun deleteDealer(dealer: Dealer) = appDao.deleteDealer(dealer)

    fun getTransactions(userEmail: String): Flow<List<TransactionRecord>> = appDao.getTransactionsOfUser(userEmail)

    suspend fun insertTransaction(transaction: TransactionRecord) = appDao.insertTransaction(transaction)

    suspend fun deleteTransaction(transaction: TransactionRecord) = appDao.deleteTransaction(transaction)
}
