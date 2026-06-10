package com.example.superstoresimulator.domain.Transactions

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction as RoomTransaction

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLines(lines: List<TransactionLineEntity>)

    @RoomTransaction
    suspend fun insertTransactionsWithLines(
        transactions: List<TransactionEntity>,
        lines: List<TransactionLineEntity>,
    ) {
        insertTransactions(transactions)
        insertLines(lines)
    }

    @Query("SELECT * FROM transactions ORDER BY id DESC LIMIT :limit")
    suspend fun getRecentTransactions(limit: Int = 100): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE gameDayNumber = :dayNumber ORDER BY id")
    suspend fun getTransactionsForDay(dayNumber: Int): List<TransactionEntity>

    @Query("SELECT * FROM transaction_lines WHERE transactionId = :transactionId")
    suspend fun getLinesForTransaction(transactionId: Int): List<TransactionLineEntity>

    @Query("SELECT * FROM transaction_lines WHERE transactionId IN (:transactionIds)")
    suspend fun getLinesForTransactions(transactionIds: List<Int>): List<TransactionLineEntity>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getTransactionCount(): Int

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}
