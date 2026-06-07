package com.example.superstoresimulator.domain.pricing

data class PricingConfig(
    val priceElasticity: Float = 1.5f,
    val trafficElasticity: Float = 0.5f,
    val basketElasticity: Float = 1.0f,
    val freshMarkdownPercent: Int = 30,
    val expiryThresholdDays: Int = 1,
    val emaAlpha: Float = 0.3f,
    val maxPriceHistorySize: Int = 200,
    val produceWeightMin: Float = 0.5f,
    val produceWeightMax: Float = 3.0f,
    val meatWeightMin: Float = 0.75f,
    val meatWeightMax: Float = 2.5f,
) {
    companion object {
        val DEFAULT = PricingConfig()
    }
}
