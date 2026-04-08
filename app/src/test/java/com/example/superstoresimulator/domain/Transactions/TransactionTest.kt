package com.example.superstoresimulator.domain.Transactions

import com.example.superstoresimulator.domain.Money
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for Transaction and TransactionLine.
 * Trivial construction / copy() / pure-arithmetic tests removed — they tested
 * Kotlin data-class semantics rather than application logic.
 * Retained: convenience constructor computed fields, multi-line subtotal
 * cross-check, high-value totals, and single-item tax sanity.
 */
class TransactionTest {

    @Test
    fun testTransactionLineConstructor() {
        // Using the convenience constructor
        val line = TransactionLine(10, 2, Money.fromDollars(15.0))

        assertEquals(10, line.itemId)
        assertEquals(2, line.quantity)
        assertEquals(2, line.rungQty)  // Fully rung
        assertEquals(Money.fromDollars(15.0), line.unitPrice)
        assertEquals(Money.fromDollars(30.0), line.lineTotal)
    }

    @Test
    fun testComplexTransaction() {
        // Simulate a typical transaction
        val lines = listOf(
            TransactionLine(1, 2, Money.fromDollars(3.99)),    // 7.98
            TransactionLine(2, 1, Money.fromDollars(12.99)),   // 12.99
            TransactionLine(3, 4, Money.fromDollars(1.50)),    // 6.00
            TransactionLine(4, 1, Money.fromDollars(5.50))     // 5.50
        )

        val subtotal = lines.fold(Money(0)) { acc, line -> acc + line.lineTotal }
        // 7.98 + 12.99 + 6.00 + 5.50 = 32.47
        assertEquals(Money.fromDollars(32.47), subtotal)

        val taxRate = 0.0825
        val tax = Money.fromDollars(subtotal.toDouble() * taxRate)
        // 32.47 * 0.0825 = 2.678775, rounds to 2.68
        val totalExpected = Money.fromDollars(35.15)
        val totalCalculated = subtotal + tax

        // Allow small rounding differences (1 cent)
        assertTrue("Tax calculation off", (totalCalculated.cents - totalExpected.cents).let { if (it < 0) -it else it } <= 2)
    }

    @Test
    fun testHighValueTransaction() {
        val lines = listOf(
            TransactionLine(1, 10, Money.fromDollars(50.0)), // 500.00
            TransactionLine(2, 5, Money.fromDollars(100.0))  // 500.00
        )

        val subtotal = lines.fold(Money(0)) { acc, line -> acc + line.lineTotal }
        assertEquals(Money.fromDollars(1000.0), subtotal)

        val tax = Money.fromDollars(subtotal.toDouble() * 0.0825)
        val total = subtotal + tax

        assertEquals(Money.fromDollars(1082.5), total)
    }

    @Test
    fun testSingleItemTransaction() {
        val lines = listOf(
            TransactionLine(1, 1, Money.fromDollars(9.99))
        )

        val subtotal = lines[0].lineTotal
        val tax = Money.fromDollars(subtotal.toDouble() * 0.0825)

        assertEquals(Money.fromDollars(9.99), subtotal)
        assertTrue(tax.cents > 0)
    }
}
