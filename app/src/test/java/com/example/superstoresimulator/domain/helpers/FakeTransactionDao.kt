package com.example.superstoresimulator.domain.helpers

import com.example.superstoresimulator.domain.Transactions.TransactionDao
import com.example.superstoresimulator.domain.Transactions.TransactionEntity
import com.example.superstoresimulator.domain.Transactions.TransactionLineEntity

class FakeTransactionDao : TransactionDao {
    val insertedTransactions = mutableListOf<TransactionEntity>()
    val insertedLines = mutableListOf<TransactionLineEntity>()

    override suspend fun insertTransactions(transactions: List<TransactionEntity>) {
        insertedTransactions.addAll(transactions)
    }

    override suspend fun insertLines(lines: List<TransactionLineEntity>) {
        insertedLines.addAll(lines)
    }

    override suspend fun getRecentTransactions(limit: Int): List<TransactionEntity> =
        insertedTransactions.sortedByDescending { it.id }.take(limit)

    override suspend fun getTransactionsForDay(dayNumber: Int): List<TransactionEntity> =
        insertedTransactions.filter { it.gameDayNumber == dayNumber }

    override suspend fun getLinesForTransaction(transactionId: Int): List<TransactionLineEntity> =
        insertedLines.filter { it.transactionId == transactionId }

    override suspend fun getLinesForTransactions(transactionIds: List<Int>): List<TransactionLineEntity> =
        insertedLines.filter { it.transactionId in transactionIds }

    override suspend fun getTransactionCount(): Int = insertedTransactions.size

    override suspend fun deleteAll() {
        insertedTransactions.clear()
        insertedLines.clear()
    }
}
