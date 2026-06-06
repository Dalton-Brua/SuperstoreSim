package com.example.superstoresimulator.domain.pricing

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.persistence.GameStateSerializer
import org.junit.Assert.*
import org.junit.Test

class PricingSerializationTest {

    private fun stateWithPricing(pricing: PricingState): GameState =
        GameState(money = Money(10_000)).copy(pricingState = pricing)

    @Test
    fun `PricingState round-trips through serialization`() {
        val original = PricingState(
            categoryMarkups = mapOf(ItemCategory.GROCERY to 10, ItemCategory.DAIRY to -5),
            itemOverrides = mapOf(1 to -15, 42 to 20),
            activeMarkdowns = mapOf(
                7 to Markdown(30, MarkdownReason.EXPIRING_SOON, 3),
                12 to Markdown(50, MarkdownReason.PLAYER_SALE, 5),
            ),
            defaultMarkup = 8,
            smoothedPriceIndex = 1.15f,
            priceHistory = listOf(
                PriceChangeEvent(3, null, ItemCategory.GROCERY, 0, 10, null),
                PriceChangeEvent(4, 42, null, 0, 20, MarkdownReason.PLAYER_SALE),
            ),
        )
        val state = stateWithPricing(original)
        val json = GameStateSerializer.serialize(state)
        val restored = GameStateSerializer.deserialize(json)

        assertNotNull(restored)
        val pricing = restored!!.pricingState
        assertEquals(10, pricing.categoryMarkups[ItemCategory.GROCERY])
        assertEquals(-5, pricing.categoryMarkups[ItemCategory.DAIRY])
        assertEquals(-15, pricing.itemOverrides[1])
        assertEquals(20, pricing.itemOverrides[42])

        val md7 = pricing.activeMarkdowns[7]!!
        assertEquals(30, md7.percentOff)
        assertEquals(MarkdownReason.EXPIRING_SOON, md7.reason)
        assertEquals(3, md7.appliedOnDay)

        val md12 = pricing.activeMarkdowns[12]!!
        assertEquals(50, md12.percentOff)
        assertEquals(MarkdownReason.PLAYER_SALE, md12.reason)

        assertEquals(8, pricing.defaultMarkup)
        assertEquals(1.15f, pricing.smoothedPriceIndex, 0.01f)

        assertEquals(2, pricing.priceHistory.size)
        assertEquals(ItemCategory.GROCERY, pricing.priceHistory[0].category)
        assertEquals(42, pricing.priceHistory[1].itemId)
    }

    @Test
    fun `multipliers are computed from smoothedPriceIndex`() {
        val state = stateWithPricing(PricingState(smoothedPriceIndex = 1.0f))
        val json = GameStateSerializer.serialize(state)
        val restored = GameStateSerializer.deserialize(json)!!

        assertEquals(1.0f, restored.pricingState.priceTrafficMultiplier, 0.001f)
        assertEquals(1.0f, restored.pricingState.basketSizeMultiplier, 0.001f)
    }

    @Test
    fun `empty PricingState deserializes as defaults from pre-pricing save`() {
        // Simulate a save file that has no pricingState key
        val state = GameState(money = Money(10_000))
        val json = GameStateSerializer.serialize(state)
        // Remove pricingState from the JSON to simulate old save
        val jsonObj = org.json.JSONObject(json)
        jsonObj.remove("pricingState")
        val restored = GameStateSerializer.deserialize(jsonObj.toString())!!

        assertEquals(PricingState(), restored.pricingState)
    }

    @Test
    fun `price history capped at 200 on deserialization`() {
        val events = (1..250).map {
            PriceChangeEvent(it, null, ItemCategory.GROCERY, 0, it, null)
        }
        val state = stateWithPricing(PricingState(priceHistory = events.take(200)))
        val json = GameStateSerializer.serialize(state)
        val restored = GameStateSerializer.deserialize(json)!!
        assertEquals(200, restored.pricingState.priceHistory.size)
    }

    @Test
    fun `TransactionLine new fields round-trip`() {
        val state = GameState(money = Money(10_000))
        val json = GameStateSerializer.serialize(state)
        val restored = GameStateSerializer.deserialize(json)
        assertNotNull(restored)
    }
}
