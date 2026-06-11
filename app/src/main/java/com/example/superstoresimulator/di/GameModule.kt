package com.example.superstoresimulator.di

import com.example.superstoresimulator.domain.Transactions.TransactionEngine
import com.example.superstoresimulator.domain.inventory.InventoryManager
import com.example.superstoresimulator.domain.delivery.TruckManager
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.pricing.PricingManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GameModule {

    @Provides
    @Singleton
    fun provideTransactionEngine(cache: ItemMetadataCache, pricingManager: PricingManager): TransactionEngine =
        TransactionEngine(cache = cache, pricingManager = pricingManager)

    @Provides
    @Singleton
    fun provideInventoryManager(cache: ItemMetadataCache, truckManager: TruckManager): InventoryManager =
        InventoryManager(cache, truckManager)
}
