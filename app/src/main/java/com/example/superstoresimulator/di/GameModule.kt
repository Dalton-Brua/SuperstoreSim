package com.example.superstoresimulator.di

import com.example.superstoresimulator.domain.Transactions.TransactionEngine
import com.example.superstoresimulator.domain.expiration.SpoilageManager
import com.example.superstoresimulator.domain.inventory.InventoryManager
import com.example.superstoresimulator.domain.items.ItemDao
import com.example.superstoresimulator.domain.delivery.TruckManager
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.metrics.DayManager
import com.example.superstoresimulator.domain.player.PlayerActionHandler
import com.example.superstoresimulator.domain.pricing.PricingManager
import com.example.superstoresimulator.domain.progression.ProgressionManager
import com.example.superstoresimulator.domain.staff.StaffManager
import com.example.superstoresimulator.domain.store.StoreController
import com.example.superstoresimulator.domain.time.TimeManager
import com.example.superstoresimulator.domain.traffic.TrafficManager
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
    fun provideItemMetadataCache(itemDao: ItemDao): ItemMetadataCache =
        ItemMetadataCache(itemDao)

    @Provides
    @Singleton
    fun providePricingManager(cache: ItemMetadataCache): PricingManager =
        PricingManager(cache)

    @Provides
    @Singleton
    fun provideTransactionEngine(cache: ItemMetadataCache, pricingManager: PricingManager): TransactionEngine =
        TransactionEngine(cache = cache, pricingManager = pricingManager)

    @Provides
    @Singleton
    fun provideTimeManager(): TimeManager = TimeManager()

    @Provides
    @Singleton
    fun provideTrafficManager(): TrafficManager = TrafficManager()

    @Provides
    @Singleton
    fun provideProgressionManager(): ProgressionManager = ProgressionManager()

    @Provides
    @Singleton
    fun provideStoreController(): StoreController = StoreController()

    @Provides
    @Singleton
    fun provideDayManager(): DayManager = DayManager()

    @Provides
    @Singleton
    fun provideStaffManager(): StaffManager = StaffManager()

    @Provides
    @Singleton
    fun providePlayerActionHandler(): PlayerActionHandler = PlayerActionHandler()

    @Provides
    @Singleton
    fun provideInventoryManager(cache: ItemMetadataCache, truckManager: TruckManager): InventoryManager =
        InventoryManager(cache, truckManager)

    @Provides
    @Singleton
    fun provideSpoilageManager(cache: ItemMetadataCache): SpoilageManager =
        SpoilageManager(cache)

    @Provides
    @Singleton
    fun provideTruckManager(cache: ItemMetadataCache): TruckManager =
        TruckManager(cache)
}
