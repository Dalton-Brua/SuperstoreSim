package com.example.superstoresimulator.domain

// Represents a pending refund that must be processed manually
data class RefundLine(
    val itemId: Int,
    val quantity: Int, // positive number of items to refund
    val unitPrice: Money
)

data class RefundRequest(
    val id: Int,
    val timestamp: Long,
    val originalTransactionId: Int,
    val lines: List<RefundLine>,
    val subtotal: Money,
    val tax: Money
)

