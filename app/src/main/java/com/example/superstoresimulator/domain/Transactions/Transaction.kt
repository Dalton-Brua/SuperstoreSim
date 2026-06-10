package com.example.superstoresimulator.domain.Transactions

import com.example.superstoresimulator.domain.Money
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant

object InstantEpochMillisSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Instant) =
        encoder.encodeLong(value.toEpochMilli())
    override fun deserialize(decoder: Decoder): Instant =
        Instant.ofEpochMilli(decoder.decodeLong())
}

@Serializable
data class Transaction(
    val id: Int,
    val lines: List<TransactionLine>,
    val subtotal: Money,
    val tax: Money,
    val totalEarned: Money,
    @Serializable(with = InstantEpochMillisSerializer::class)
    val completedAt: Instant? = null,
    val registerId: Int = 0,
    val gameDayNumber: Int = 0,
) {
    constructor(): this(0, emptyList(), Money(0), Money(0), Money(0))
}

@Serializable
data class TransactionLine(
    val itemId: Int,
    val quantity: Int,
    val rungQty: Int,
    val unitPrice: Money,
    val lineTotal: Money,
    /** True when the cashier could not ring this line because shelf stock was zero. */
    val lostToOutOfStock: Boolean = false,
    val basePrice: Money = unitPrice,
    val priceModifier: Int = 0,
    val weight: Float? = null,
) {
    constructor(id: Int, quantity: Int, price: Money) : this(
        itemId = id,
        quantity = quantity,
        unitPrice = price,
        lineTotal = price * quantity,
        rungQty = quantity,
    )
}

fun Transaction.toEntity() = TransactionEntity(
    id = id,
    subtotalCents = subtotal.cents,
    taxCents = tax.cents,
    totalEarnedCents = totalEarned.cents,
    completedAtMillis = completedAt?.toEpochMilli(),
    registerId = registerId,
    gameDayNumber = gameDayNumber,
)

fun TransactionLine.toLineEntity(transactionId: Int) = TransactionLineEntity(
    transactionId = transactionId,
    itemId = itemId,
    quantity = quantity,
    rungQty = rungQty,
    unitPriceCents = unitPrice.cents,
    lineTotalCents = lineTotal.cents,
    lostToOutOfStock = lostToOutOfStock,
    basePriceCents = basePrice.cents,
    priceModifier = priceModifier,
    weight = weight,
)
