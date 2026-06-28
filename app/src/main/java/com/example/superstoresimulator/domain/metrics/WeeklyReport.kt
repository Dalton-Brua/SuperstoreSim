package com.example.superstoresimulator.domain.metrics

import com.example.superstoresimulator.domain.Money

data class WeeklyReport(
    val startDay: Int,
    val endDay: Int,
    val startingBalance: Money,
    val endingBalance: Money,
    val dailyReports: List<DailyMetrics>,
) {
    val daysSimulated: Int get() = dailyReports.size

    val totalRevenue: Money get() = dailyReports.fold(Money.ZERO) { acc, d -> acc + d.revenue }
    val totalSubtotal: Money get() = dailyReports.fold(Money.ZERO) { acc, d -> acc + d.subtotal }
    val totalTaxCollected: Money get() = dailyReports.fold(Money.ZERO) { acc, d -> acc + d.taxCollected }
    val totalTransactions: Int get() = dailyReports.sumOf { it.transactionsCompleted }

    val totalRentPaid: Money get() = dailyReports.fold(Money.ZERO) { acc, d -> acc + d.rentPaid }
    val totalWagesPaid: Money get() = dailyReports.fold(Money.ZERO) { acc, d -> acc + d.wagesPaid }

    val totalCustomersServed: Int get() = dailyReports.sumOf { it.customersServed }
    val totalItemsSold: Int get() = dailyReports.sumOf { it.itemsSold }
    val totalItemsStocked: Int get() = dailyReports.sumOf { it.itemsStocked }
    val totalItemsOrdered: Int get() = dailyReports.sumOf { it.itemsOrdered }

    val totalLostRevenue: Money get() = dailyReports.fold(Money.ZERO) { acc, d -> acc + d.lostRevenue }
    val totalItemsLostToOOS: Int get() = dailyReports.sumOf { it.itemsLostToOutOfStock }

    val totalItemsExpired: Int get() = dailyReports.sumOf { it.itemsExpired }
    val totalExpiredWasteCost: Money get() = dailyReports.fold(Money.ZERO) { acc, d -> acc + d.expiredWasteCost }

    val totalNetRevenue: Money get() = dailyReports.fold(Money.ZERO) { acc, d -> acc + d.netRevenue }

    val balanceChange: Money get() = endingBalance - startingBalance

    val averageTransactionValue: Money
        get() = if (totalTransactions > 0)
            Money(totalRevenue.cents / totalTransactions)
        else Money.ZERO

    val averageBasketSize: Float
        get() = if (totalTransactions > 0)
            totalItemsSold.toFloat() / totalTransactions
        else 0f

    val totalDeliveredTrucks: Int get() = dailyReports.sumOf { it.deliveredTrucks.size }
}
