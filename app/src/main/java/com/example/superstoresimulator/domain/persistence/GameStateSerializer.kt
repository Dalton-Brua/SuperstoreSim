package com.example.superstoresimulator.domain.persistence

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.Entities.HiredEntity
import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.EntityType
import com.example.superstoresimulator.domain.Entities.EntityTrait
import com.example.superstoresimulator.domain.Transactions.Transaction
import com.example.superstoresimulator.domain.Transactions.TransactionLine
import com.example.superstoresimulator.domain.RefundRequest
import com.example.superstoresimulator.domain.RefundLine
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.items.ItemUnlockTier
import com.example.superstoresimulator.domain.metrics.DailyMetrics
import com.example.superstoresimulator.domain.metrics.DailyMetricsAccumulator
import com.example.superstoresimulator.domain.metrics.OutOfStockEvent
import com.example.superstoresimulator.domain.metrics.SoldItemEvent
import com.example.superstoresimulator.domain.player.PlayerRole
import com.example.superstoresimulator.domain.store.StoreConfig
import com.example.superstoresimulator.domain.store.StoreSize
import com.example.superstoresimulator.domain.store.StoreState
import com.example.superstoresimulator.domain.time.GameTime
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes and deserializes GameState to/from JSON for persistence.
 * Uses org.json (built into Android) to avoid additional dependencies.
 */
object GameStateSerializer {

    fun serialize(state: GameState): String {
        val json = JSONObject()
        
        // Basic fields
        json.put("storeName", state.storeName)
        json.put("money", state.money.cents)
        json.put("transactionActive", state.transactionActive)
        json.put("totalTransactionsCompleted", state.totalTransactionsCompleted)
        json.put("totalTaxCollected", state.totalTaxCollected.cents)
        json.put("nextRefundId", state.nextRefundId)
        json.put("playerPausedTime", state.playerPausedTime)
        json.put("totalRevenue", state.totalRevenue.cents)
        json.put("currentTier", state.currentTier.name)
        json.put("currentStoreSize", state.currentStoreSize.name)
        json.put("playerRole", state.playerRole.name)
        json.put("playerCashierProgress", state.playerCashierProgress.toDouble())
        json.put("playerStockerProgress", state.playerStockerProgress.toDouble())
        json.put("pendingCustomers", state.pendingCustomers)
        json.put("showEndOfDayReport", state.showEndOfDayReport)
        json.put("pausedByEndOfDay", state.pausedByEndOfDay)
        json.put("objectiveBonusEarned", state.objectiveBonusEarned.cents)
        
        // Time and store
        json.put("currentTime", state.currentTime.totalMinutesElapsed)
        json.put("storeState", state.storeState.name)
        json.put("storeConfig", serializeStoreConfig(state.storeConfig))
        
        // Current transaction
        json.put("currentTransaction", serializeTransaction(state.currentTransaction))
        
        // Sales history
        val historyArray = JSONArray()
        state.salesHistory.forEach { tx ->
            historyArray.put(serializeTransaction(tx))
        }
        json.put("salesHistory", historyArray)
        
        // Pending refunds
        val refundsArray = JSONArray()
        state.pendingRefunds.forEach { refund ->
            refundsArray.put(serializeRefundRequest(refund))
        }
        json.put("pendingRefunds", refundsArray)
        
        // Inventory
        val inventoryObj = JSONObject()
        state.inventory.forEach { (itemId, invState) ->
            inventoryObj.put(itemId.toString(), serializeInventoryState(invState))
        }
        json.put("inventory", inventoryObj)
        
        // Hired entity registry
        json.put("hiredEntityRegistry", serializeHiredEntityRegistry(state.hiredEntityRegistry))
        
        // Daily metrics
        json.put("currentDayMetrics", serializeDailyMetricsAccumulator(state.currentDayMetrics))
        val completedDaysArray = JSONArray()
        state.completedDayMetrics.forEach { day ->
            completedDaysArray.put(serializeDailyMetrics(day))
        }
        json.put("completedDayMetrics", completedDaysArray)
        
        // Last end of day report
        state.lastEndOfDayReport?.let {
            json.put("lastEndOfDayReport", serializeDailyMetrics(it))
        }
        
        return json.toString()
    }

    fun deserialize(jsonString: String): GameState? {
        return try {
            val json = JSONObject(jsonString)
            
            GameState(
                storeName = json.getString("storeName"),
                money = Money(json.getLong("money")),
                transactionActive = json.getBoolean("transactionActive"),
                totalTransactionsCompleted = json.getInt("totalTransactionsCompleted"),
                totalTaxCollected = Money(json.getLong("totalTaxCollected")),
                nextRefundId = json.getInt("nextRefundId"),
                playerPausedTime = json.getBoolean("playerPausedTime"),
                totalRevenue = Money(json.getLong("totalRevenue")),
                currentTier = ItemUnlockTier.valueOf(json.getString("currentTier")),
                currentStoreSize = StoreSize.valueOf(json.getString("currentStoreSize")),
                playerRole = PlayerRole.valueOf(json.getString("playerRole")),
                playerCashierProgress = json.getDouble("playerCashierProgress").toFloat(),
                playerStockerProgress = json.getDouble("playerStockerProgress").toFloat(),
                pendingCustomers = json.getInt("pendingCustomers"),
                showEndOfDayReport = json.getBoolean("showEndOfDayReport"),
                pausedByEndOfDay = json.getBoolean("pausedByEndOfDay"),
                objectiveBonusEarned = Money(json.getLong("objectiveBonusEarned")),
                currentTime = GameTime(json.getLong("currentTime")),
                storeState = StoreState.valueOf(json.getString("storeState")),
                storeConfig = deserializeStoreConfig(json.getJSONObject("storeConfig")),
                currentTransaction = deserializeTransaction(json.getJSONObject("currentTransaction")),
                salesHistory = deserializeTransactionList(json.getJSONArray("salesHistory")),
                pendingRefunds = deserializeRefundRequestList(json.getJSONArray("pendingRefunds")),
                inventory = deserializeInventory(json.getJSONObject("inventory")),
                hiredEntityRegistry = deserializeHiredEntityRegistry(json.getJSONObject("hiredEntityRegistry")),
                currentDayMetrics = if (json.has("currentDayMetrics")) {
                    deserializeDailyMetricsAccumulator(json.getJSONObject("currentDayMetrics"))
                } else {
                    DailyMetricsAccumulator()  // Default empty accumulator
                },
                completedDayMetrics = if (json.has("completedDayMetrics")) {
                    deserializeDailyMetricsList(json.getJSONArray("completedDayMetrics"))
                } else {
                    emptyList()  // Default empty list
                },
                lastEndOfDayReport = if (json.has("lastEndOfDayReport")) {
                    deserializeDailyMetrics(json.getJSONObject("lastEndOfDayReport"))
                } else null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun serializeStoreConfig(config: StoreConfig): JSONObject {
        return JSONObject().apply {
            put("openTimeMinutes", config.openTimeMinutes)
            put("closeTimeMinutes", config.closeTimeMinutes)
            put("closingProcedureDuration", config.closingProcedureDuration)
            put("allowTransactionsDuringClosing", config.allowTransactionsDuringClosing)
            put("gameSpeedMultiplier", config.gameSpeedMultiplier.toDouble())
            put("backroomCapPerItem", config.backroomCapPerItem)
        }
    }

    private fun deserializeStoreConfig(json: JSONObject): StoreConfig {
        return StoreConfig(
            openTimeMinutes = json.getInt("openTimeMinutes"),
            closeTimeMinutes = json.getInt("closeTimeMinutes"),
            closingProcedureDuration = json.getInt("closingProcedureDuration"),
            allowTransactionsDuringClosing = json.getBoolean("allowTransactionsDuringClosing"),
            gameSpeedMultiplier = json.getDouble("gameSpeedMultiplier").toFloat(),
            backroomCapPerItem = json.getInt("backroomCapPerItem")
        )
    }

    private fun serializeTransaction(tx: Transaction): JSONObject {
        return JSONObject().apply {
            put("id", tx.id)
            put("subtotal", tx.subtotal.cents)
            put("tax", tx.tax.cents)
            put("totalEarned", tx.totalEarned.cents)
            // Serialize completedAt as null - we don't need to persist the exact instant
            // The transaction is either in progress (null) or completed
            val linesArray = JSONArray()
            tx.lines.forEach { line ->
                linesArray.put(serializeTransactionLine(line))
            }
            put("lines", linesArray)
        }
    }

    private fun deserializeTransaction(json: JSONObject): Transaction {
        val lines = mutableListOf<TransactionLine>()
        val linesArray = json.getJSONArray("lines")
        for (i in 0 until linesArray.length()) {
            lines.add(deserializeTransactionLine(linesArray.getJSONObject(i)))
        }
        // completedAt will be null for saved transactions - this is fine as it's only used for display
        return Transaction(
            id = json.getInt("id"),
            lines = lines,
            subtotal = Money(json.getLong("subtotal")),
            tax = Money(json.getLong("tax")),
            totalEarned = Money(json.getLong("totalEarned")),
            completedAt = null
        )
    }

    private fun deserializeTransactionList(jsonArray: JSONArray): List<Transaction> {
        val list = mutableListOf<Transaction>()
        for (i in 0 until jsonArray.length()) {
            list.add(deserializeTransaction(jsonArray.getJSONObject(i)))
        }
        return list
    }

    private fun serializeTransactionLine(line: TransactionLine): JSONObject {
        return JSONObject().apply {
            put("itemId", line.itemId)
            put("quantity", line.quantity)
            put("rungQty", line.rungQty)
            put("unitPrice", line.unitPrice.cents)
            put("lineTotal", line.lineTotal.cents)
            put("lostToOutOfStock", line.lostToOutOfStock)
        }
    }

    private fun deserializeTransactionLine(json: JSONObject): TransactionLine {
        return TransactionLine(
            itemId = json.getInt("itemId"),
            quantity = json.getInt("quantity"),
            rungQty = json.getInt("rungQty"),
            unitPrice = Money(json.getLong("unitPrice")),
            lineTotal = Money(json.getLong("lineTotal")),
            lostToOutOfStock = json.getBoolean("lostToOutOfStock")
        )
    }

    private fun serializeRefundRequest(refund: RefundRequest): JSONObject {
        return JSONObject().apply {
            put("id", refund.id)
            put("timestamp", refund.timestamp)
            put("originalTransactionId", refund.originalTransactionId)
            put("subtotal", refund.subtotal.cents)
            put("tax", refund.tax.cents)
            val linesArray = JSONArray()
            refund.lines.forEach { line ->
                linesArray.put(serializeRefundLine(line))
            }
            put("lines", linesArray)
        }
    }

    private fun deserializeRefundRequest(json: JSONObject): RefundRequest {
        val lines = mutableListOf<RefundLine>()
        val linesArray = json.getJSONArray("lines")
        for (i in 0 until linesArray.length()) {
            lines.add(deserializeRefundLine(linesArray.getJSONObject(i)))
        }
        return RefundRequest(
            id = json.getInt("id"),
            timestamp = json.getLong("timestamp"),
            originalTransactionId = json.getInt("originalTransactionId"),
            lines = lines,
            subtotal = Money(json.getLong("subtotal")),
            tax = Money(json.getLong("tax"))
        )
    }

    private fun deserializeRefundRequestList(jsonArray: JSONArray): List<RefundRequest> {
        val list = mutableListOf<RefundRequest>()
        for (i in 0 until jsonArray.length()) {
            list.add(deserializeRefundRequest(jsonArray.getJSONObject(i)))
        }
        return list
    }

    private fun serializeRefundLine(line: RefundLine): JSONObject {
        return JSONObject().apply {
            put("itemId", line.itemId)
            put("quantity", line.quantity)
            put("unitPrice", line.unitPrice.cents)
        }
    }

    private fun deserializeRefundLine(json: JSONObject): RefundLine {
        return RefundLine(
            itemId = json.getInt("itemId"),
            quantity = json.getInt("quantity"),
            unitPrice = Money(json.getLong("unitPrice"))
        )
    }

    private fun serializeInventoryState(invState: InventoryState): JSONObject {
        return JSONObject().apply {
            put("shelfStock", invState.shelfStock)
            put("backroomStock", invState.backroomStock)
        }
    }

    private fun deserializeInventory(json: JSONObject): Map<Int, InventoryState> {
        val map = mutableMapOf<Int, InventoryState>()
        json.keys().forEach { key ->
            val itemId = key.toInt()
            val invJson = json.getJSONObject(key)
            map[itemId] = InventoryState(
                shelfStock = invJson.getInt("shelfStock"),
                backroomStock = invJson.getInt("backroomStock")
            )
        }
        return map
    }

    private fun serializeHiredEntityRegistry(registry: HiredEntityRegistry): JSONObject {
        return JSONObject().apply {
            val entitiesArray = JSONArray()
            registry.hiredEntities.forEach { entity ->
                entitiesArray.put(serializeHiredEntity(entity))
            }
            put("entities", entitiesArray)
        }
    }

    private fun deserializeHiredEntityRegistry(json: JSONObject): HiredEntityRegistry {
        val entities = mutableListOf<HiredEntity>()
        val entitiesArray = json.getJSONArray("entities")
        for (i in 0 until entitiesArray.length()) {
            entities.add(deserializeHiredEntity(entitiesArray.getJSONObject(i)))
        }
        var registry = HiredEntityRegistry()
        entities.forEach { entity ->
            // Reconstruct by hiring each entity with its original definition and type
            registry = registry.hireEntity(entity.entityDefinition, entity.entityType)
        }
        return registry
    }

    private fun serializeHiredEntity(entity: HiredEntity): JSONObject {
        return JSONObject().apply {
            put("id", entity.id)
            put("name", entity.name)
            put("entityDefKey", entity.entityDefinition.key)
            put("entityTypeDisplayName", entity.entityType.displayName)
            put("trait", entity.trait.name)
        }
    }

    private fun deserializeHiredEntity(json: JSONObject): HiredEntity {
        val defKey = json.getString("entityDefKey")
        val typeDisplayName = json.getString("entityTypeDisplayName")
        
        // Find the entity definition and type from the companions
        val entityDef = EntityDef.allEntities.find { it.key == defKey } 
            ?: throw IllegalStateException("Unknown entity definition: $defKey")
        val entityType = EntityType.allEntityTypes.find { it.displayName == typeDisplayName }
            ?: throw IllegalStateException("Unknown entity type: $typeDisplayName")
        
        return HiredEntity(
            id = json.getInt("id"),
            name = json.getString("name"),
            entityDefinition = entityDef,
            entityType = entityType,
            trait = EntityTrait.valueOf(json.getString("trait"))
        )
    }

    private fun serializeDailyMetricsAccumulator(acc: DailyMetricsAccumulator): JSONObject {
        return JSONObject().apply {
            put("dayNumber", acc.dayNumber)
            put("revenue", acc.revenue.cents)
            put("subtotal", acc.subtotal.cents)
            put("taxCollected", acc.taxCollected.cents)
            put("transactionsCompleted", acc.transactionsCompleted)
            put("rentPaid", acc.rentPaid.cents)
            put("wagesPaid", acc.wagesPaid.cents)
            put("refundsProcessed", acc.refundsProcessed)
            put("refundAmount", acc.refundAmount.cents)
            put("customersServed", acc.customersServed)
            put("itemsSold", acc.itemsSold)
            put("itemsStocked", acc.itemsStocked)
            put("itemsOrdered", acc.itemsOrdered)
            put("lostRevenue", acc.lostRevenue.cents)
            put("itemsLostToOutOfStock", acc.itemsLostToOutOfStock)
            
            val oosArray = JSONArray()
            acc.outOfStockEvents.forEach { event ->
                oosArray.put(serializeOutOfStockEvent(event))
            }
            put("outOfStockEvents", oosArray)
            
            val soldArray = JSONArray()
            acc.soldItemEvents.forEach { event ->
                soldArray.put(serializeSoldItemEvent(event))
            }
            put("soldItemEvents", soldArray)
        }
    }

    private fun deserializeDailyMetricsAccumulator(json: JSONObject): DailyMetricsAccumulator {
        val oosEvents = mutableListOf<OutOfStockEvent>()
        val oosArray = json.getJSONArray("outOfStockEvents")
        for (i in 0 until oosArray.length()) {
            oosEvents.add(deserializeOutOfStockEvent(oosArray.getJSONObject(i)))
        }
        
        val soldEvents = mutableListOf<SoldItemEvent>()
        val soldArray = json.getJSONArray("soldItemEvents")
        for (i in 0 until soldArray.length()) {
            soldEvents.add(deserializeSoldItemEvent(soldArray.getJSONObject(i)))
        }
        
        return DailyMetricsAccumulator(
            dayNumber = if (json.has("dayNumber")) json.getInt("dayNumber") else 0,
            revenue = Money(json.getLong("revenue")),
            subtotal = Money(json.getLong("subtotal")),
            taxCollected = Money(json.getLong("taxCollected")),
            transactionsCompleted = json.getInt("transactionsCompleted"),
            rentPaid = Money(json.getLong("rentPaid")),
            wagesPaid = Money(json.getLong("wagesPaid")),
            refundsProcessed = json.getInt("refundsProcessed"),
            refundAmount = Money(json.getLong("refundAmount")),
            customersServed = json.getInt("customersServed"),
            itemsSold = json.getInt("itemsSold"),
            itemsStocked = json.getInt("itemsStocked"),
            itemsOrdered = json.getInt("itemsOrdered"),
            lostRevenue = Money(json.getLong("lostRevenue")),
            itemsLostToOutOfStock = json.getInt("itemsLostToOutOfStock"),
            outOfStockEvents = oosEvents,
            soldItemEvents = soldEvents
        )
    }

    private fun serializeDailyMetrics(metrics: DailyMetrics): JSONObject {
        return JSONObject().apply {
            put("dayNumber", metrics.dayNumber)
            put("dayOfWeek", metrics.dayOfWeek)
            put("revenue", metrics.revenue.cents)
            put("subtotal", metrics.subtotal.cents)
            put("taxCollected", metrics.taxCollected.cents)
            put("transactionsCompleted", metrics.transactionsCompleted)
            put("rentPaid", metrics.rentPaid.cents)
            put("wagesPaid", metrics.wagesPaid.cents)
            put("refundsProcessed", metrics.refundsProcessed)
            put("refundAmount", metrics.refundAmount.cents)
            put("customersServed", metrics.customersServed)
            put("itemsSold", metrics.itemsSold)
            put("itemsStocked", metrics.itemsStocked)
            put("itemsOrdered", metrics.itemsOrdered)
            put("lostRevenue", metrics.lostRevenue.cents)
            put("itemsLostToOutOfStock", metrics.itemsLostToOutOfStock)
            
            val oosArray = JSONArray()
            metrics.outOfStockEvents.forEach { event ->
                oosArray.put(serializeOutOfStockEvent(event))
            }
            put("outOfStockEvents", oosArray)
            
            val soldArray = JSONArray()
            metrics.soldItemEvents.forEach { event ->
                soldArray.put(serializeSoldItemEvent(event))
            }
            put("soldItemEvents", soldArray)
        }
    }

    private fun deserializeDailyMetrics(json: JSONObject): DailyMetrics {
        val oosEvents = mutableListOf<OutOfStockEvent>()
        val oosArray = json.getJSONArray("outOfStockEvents")
        for (i in 0 until oosArray.length()) {
            oosEvents.add(deserializeOutOfStockEvent(oosArray.getJSONObject(i)))
        }
        
        val soldEvents = mutableListOf<SoldItemEvent>()
        val soldArray = json.getJSONArray("soldItemEvents")
        for (i in 0 until soldArray.length()) {
            soldEvents.add(deserializeSoldItemEvent(soldArray.getJSONObject(i)))
        }
        
        return DailyMetrics(
            dayNumber = json.getInt("dayNumber"),
            dayOfWeek = json.getInt("dayOfWeek"),
            revenue = Money(json.getLong("revenue")),
            subtotal = Money(json.getLong("subtotal")),
            taxCollected = Money(json.getLong("taxCollected")),
            transactionsCompleted = json.getInt("transactionsCompleted"),
            rentPaid = Money(json.getLong("rentPaid")),
            wagesPaid = Money(json.getLong("wagesPaid")),
            refundsProcessed = json.getInt("refundsProcessed"),
            refundAmount = Money(json.getLong("refundAmount")),
            customersServed = json.getInt("customersServed"),
            itemsSold = json.getInt("itemsSold"),
            itemsStocked = json.getInt("itemsStocked"),
            itemsOrdered = json.getInt("itemsOrdered"),
            lostRevenue = Money(json.getLong("lostRevenue")),
            itemsLostToOutOfStock = json.getInt("itemsLostToOutOfStock"),
            outOfStockEvents = oosEvents,
            soldItemEvents = soldEvents
        )
    }

    private fun deserializeDailyMetricsList(jsonArray: JSONArray): List<DailyMetrics> {
        val list = mutableListOf<DailyMetrics>()
        for (i in 0 until jsonArray.length()) {
            list.add(deserializeDailyMetrics(jsonArray.getJSONObject(i)))
        }
        return list
    }

    private fun serializeOutOfStockEvent(event: OutOfStockEvent): JSONObject {
        return JSONObject().apply {
            put("itemId", event.itemId)
            put("itemName", event.itemName)
            put("quantityLost", event.quantityLost)
            put("revenueLost", event.revenueLost.cents)
        }
    }

    private fun deserializeOutOfStockEvent(json: JSONObject): OutOfStockEvent {
        return OutOfStockEvent(
            itemId = json.getInt("itemId"),
            itemName = json.getString("itemName"),
            quantityLost = json.getInt("quantityLost"),
            revenueLost = Money(json.getLong("revenueLost"))
        )
    }

    private fun serializeSoldItemEvent(event: SoldItemEvent): JSONObject {
        return JSONObject().apply {
            put("itemId", event.itemId)
            put("itemName", event.itemName)
            put("quantitySold", event.quantitySold)
            put("revenue", event.revenue.cents)
        }
    }

    private fun deserializeSoldItemEvent(json: JSONObject): SoldItemEvent {
        return SoldItemEvent(
            itemId = json.getInt("itemId"),
            itemName = json.getString("itemName"),
            quantitySold = json.getInt("quantitySold"),
            revenue = Money(json.getLong("revenue"))
        )
    }
}

