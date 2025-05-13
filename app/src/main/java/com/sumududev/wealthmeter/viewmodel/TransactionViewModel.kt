package com.sumududev.wealthmeter.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sumududev.wealthmeter.data.AppDatabase
import com.sumududev.wealthmeter.data.entity.Transaction
import com.sumududev.wealthmeter.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class TransactionViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: TransactionRepository
    private var currentUserEmail: String? = null

    init {
        val transactionDao = AppDatabase.getDatabase(application).transactionDao()
        repository = TransactionRepository(transactionDao)
    }

    fun setCurrentUser(email: String) {
        currentUserEmail = email
    }

    fun getTransactions(): Flow<List<Transaction>>? {
        return currentUserEmail?.let { repository.getTransactionsByUser(it) }
    }

    fun insertTransaction(transaction: Transaction) = viewModelScope.launch {
        repository.insertTransaction(transaction)
    }

    fun updateTransaction(transaction: Transaction) = viewModelScope.launch {
        repository.updateTransaction(transaction)
    }

    fun deleteTransaction(transaction: Transaction) = viewModelScope.launch {
        repository.deleteTransaction(transaction)
    }

    suspend fun getTransactionById(id: Long): Transaction? {
        return repository.getTransactionById(id)
    }

    suspend fun getTotalAmountByType(type: String): Double? {
        return currentUserEmail?.let { repository.getTotalAmountByType(it, type) }
    }

    fun getTransactionsByDateRange(startDate: String, endDate: String): Flow<List<Transaction>>? {
        return currentUserEmail?.let { repository.getTransactionsByDateRange(it, startDate, endDate) }
    }
} 