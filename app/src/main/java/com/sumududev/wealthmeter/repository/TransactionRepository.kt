package com.sumududev.wealthmeter.repository

import com.sumududev.wealthmeter.data.dao.TransactionDao
import com.sumududev.wealthmeter.data.entity.Transaction
import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val transactionDao: TransactionDao) {
    suspend fun insertTransaction(transaction: Transaction) = transactionDao.insertTransaction(transaction)
    suspend fun updateTransaction(transaction: Transaction) = transactionDao.updateTransaction(transaction)
    suspend fun deleteTransaction(transaction: Transaction) = transactionDao.deleteTransaction(transaction)
    fun getTransactionsByUser(userEmail: String) = transactionDao.getTransactionsByUser(userEmail)
    suspend fun getTransactionById(id: Long) = transactionDao.getTransactionById(id)
    suspend fun getTotalAmountByType(userEmail: String, type: String) = transactionDao.getTotalAmountByType(userEmail, type)
    fun getTransactionsByDateRange(userEmail: String, startDate: String, endDate: String) = 
        transactionDao.getTransactionsByDateRange(userEmail, startDate, endDate)
} 