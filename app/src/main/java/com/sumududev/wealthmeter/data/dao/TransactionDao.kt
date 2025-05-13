package com.sumududev.wealthmeter.data.dao

import androidx.room.*
import com.sumududev.wealthmeter.data.entity.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Query("SELECT * FROM transactions WHERE userEmail = :userEmail ORDER BY date DESC")
    fun getTransactionsByUser(userEmail: String): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): Transaction?

    @Query("SELECT SUM(amount) FROM transactions WHERE userEmail = :userEmail AND type = :type")
    suspend fun getTotalAmountByType(userEmail: String, type: String): Double?

    @Query("SELECT * FROM transactions WHERE userEmail = :userEmail AND date BETWEEN :startDate AND :endDate")
    fun getTransactionsByDateRange(userEmail: String, startDate: String, endDate: String): Flow<List<Transaction>>
} 