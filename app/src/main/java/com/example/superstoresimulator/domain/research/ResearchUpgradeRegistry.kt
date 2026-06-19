package com.example.superstoresimulator.domain.research

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.store.StoreSize

enum class ResearchCategory(val displayName: String) {
    STORE_EXPANSION("Store Expansion"),
    PRODUCT_LINES("Product Lines"),
    OPERATIONS("Operations"),
    PRICING("Pricing"),
    STAFF_MANAGEMENT("Staff Management"),
}

/** Sub-grouping of [ResearchCategory.PRODUCT_LINES] research by store department. */
enum class ProductDepartment(val displayName: String) {
    GROCERY("Grocery"),
    SNACKS("Snacks"),
    DRINKS("Drinks"),
    FROZEN("Frozen"),
    BAKERY("Bakery"),
    FRESH("Fresh"),
    HEALTH_NONFOOD("Consumables"),
    PET_BABY("Pet & Baby"),
    GENERAL_MERCH("General Merchandise"),
}

data class ResearchableUpgrade(
    val id: String,
    val displayName: String,
    val description: String,
    val teaserDescription: String,
    val category: ResearchCategory,
    val researchCost: Int,
    val prerequisites: List<String> = emptyList(),
    val requiredStoreSize: StoreSize? = null,
    val gateCheck: ((GameState) -> Boolean)? = null,
)

object ResearchUpgradeRegistry {
    val allUpgrades: Map<String, ResearchableUpgrade> = buildMap {
        // ── Store Expansion ───────────────────────────────────────────────────
        put("store_small_grocery", ResearchableUpgrade(
            id = "store_small_grocery",
            displayName = "Expansion Study: Small Grocery",
            description = "Unlocks the Small Grocery store size upgrade.",
            teaserDescription = "A larger store means more customers...",
            category = ResearchCategory.STORE_EXPANSION,
            researchCost = 10,
            gateCheck = { s -> s.totalRevenue.cents >= 100_000L },
        ))
        put("store_grocery", ResearchableUpgrade(
            id = "store_grocery",
            displayName = "Expansion Study: Grocery Store",
            description = "Unlocks the Grocery Store size upgrade.",
            teaserDescription = "Take your store to the next level.",
            category = ResearchCategory.STORE_EXPANSION,
            researchCost = 25,
            prerequisites = listOf("register_expansion"),
            requiredStoreSize = StoreSize.SMALL_GROCERY,
        ))
        put("store_superstore", ResearchableUpgrade(
            id = "store_superstore",
            displayName = "Expansion Study: Superstore",
            description = "Unlocks the Superstore size upgrade.",
            teaserDescription = "A superstore serves the whole community.",
            category = ResearchCategory.STORE_EXPANSION,
            researchCost = 50,
            prerequisites = listOf("store_grocery"),
            requiredStoreSize = StoreSize.GROCERY_STORE,
        ))
        put("store_supercenter", ResearchableUpgrade(
            id = "store_supercenter",
            displayName = "Expansion Study: Supercenter",
            description = "Unlocks the Supercenter size upgrade.",
            teaserDescription = "The ultimate retail experience.",
            category = ResearchCategory.STORE_EXPANSION,
            researchCost = 100,
            prerequisites = listOf("store_superstore"),
            requiredStoreSize = StoreSize.SUPERSTORE,
        ))
        put("register_expansion", ResearchableUpgrade(
            id = "register_expansion",
            displayName = "Register Capacity Study",
            description = "Unlocks the ability to purchase additional registers.",
            teaserDescription = "Longer lines mean lost sales...",
            category = ResearchCategory.STORE_EXPANSION,
            researchCost = 15,
            prerequisites = listOf("store_small_grocery"),
        ))
        put("building_purchase", ResearchableUpgrade(
            id = "building_purchase",
            displayName = "Property Acquisition Study",
            description = "Unlocks the ability to purchase your building and eliminate daily rent.",
            teaserDescription = "Own the building. Keep the rent.",
            category = ResearchCategory.STORE_EXPANSION,
            researchCost = 150,
            prerequisites = listOf("store_supercenter"),
            requiredStoreSize = StoreSize.SUPERCENTER,
        ))

        // ── Product Lines: Grocery ────────────────────────────────────────────
        put("prod_breakfast", ResearchableUpgrade(
            id = "prod_breakfast",
            displayName = "Breakfast Aisle Study",
            description = "Unlocks Cereal, Oatmeal, and Pancake Mix.",
            teaserDescription = "Morning staples — something customers always need.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 5,
        ))
        put("prod_condiments", ResearchableUpgrade(
            id = "prod_condiments",
            displayName = "Condiment Market Study",
            description = "Unlocks Peanut Butter, Jam, Ketchup, Mustard, Mayo, and Hot Sauce.",
            teaserDescription = "Everything people put on other things.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 5,
        ))
        put("prod_baking", ResearchableUpgrade(
            id = "prod_baking",
            displayName = "Baking Supplies Study",
            description = "Unlocks Sugar, Flour, Salt, Pepper, and Vegetable Oil.",
            teaserDescription = "Home bakers are loyal customers.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 6,
            prerequisites = listOf("prod_breakfast"),
        ))
        put("prod_canned", ResearchableUpgrade(
            id = "prod_canned",
            displayName = "Canned Goods Study",
            description = "Unlocks Canned Tomatoes, Chicken Noodle Soup, Sweet Corn, Green Beans, and Chicken Broth.",
            teaserDescription = "Long shelf life. High turns.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 6,
        ))
        put("prod_premium_grocery", ResearchableUpgrade(
            id = "prod_premium_grocery",
            displayName = "Premium Grocery Study",
            description = "Unlocks Soy Sauce, Honey, Applesauce, Olive Oil, Brown Rice, and Whole Wheat Pasta.",
            teaserDescription = "Better margins from health-conscious shoppers.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 10,
            prerequisites = listOf("prod_condiments"),
            requiredStoreSize = StoreSize.SMALL_GROCERY,
        ))
        put("prod_organic", ResearchableUpgrade(
            id = "prod_organic",
            displayName = "Organic & Specialty Study",
            description = "Unlocks Organic Peanut Butter, Quinoa, Almond Butter, Balsamic Vinegar, and Coconut Oil.",
            teaserDescription = "Premium items command premium prices.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 15,
            prerequisites = listOf("prod_premium_grocery"),
            requiredStoreSize = StoreSize.SMALL_GROCERY,
        ))
        put("prod_gourmet", ResearchableUpgrade(
            id = "prod_gourmet",
            displayName = "Gourmet Foods Study",
            description = "Unlocks Truffle Oil, Imported Penne Rigate, Artisanal Crackers, and Sun-Dried Tomatoes.",
            teaserDescription = "High-value items for discerning shoppers.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 25,
            prerequisites = listOf("prod_organic"),
            requiredStoreSize = StoreSize.SMALL_GROCERY,
        ))
        put("prod_international", ResearchableUpgrade(
            id = "prod_international",
            displayName = "International Foods Study",
            description = "Unlocks imported sauces, noodles, and specialty ingredients.",
            teaserDescription = "Diverse aisles attract diverse shoppers.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 12,
            prerequisites = listOf("prod_condiments"),
        ))
        put("prod_spices", ResearchableUpgrade(
            id = "prod_spices",
            displayName = "Spice Rack Study",
            description = "Unlocks a full spice selection.",
            teaserDescription = "Cooks always need more spices.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 8,
            prerequisites = listOf("prod_baking"),
        ))
        put("prod_pasta_sauces", ResearchableUpgrade(
            id = "prod_pasta_sauces",
            displayName = "Pasta & Sauce Study",
            description = "Unlocks pasta sauces and tomato products.",
            teaserDescription = "Pasta night is every night.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 6,
            prerequisites = listOf("prod_canned"),
        ))

        // ── Product Lines: Snacks ─────────────────────────────────────────────
        put("prod_snack_variety", ResearchableUpgrade(
            id = "prod_snack_variety",
            displayName = "Snack Variety Study",
            description = "Unlocks Tortilla Chips, Popcorn, Peanuts, Crackers, and Sugar Cookies.",
            teaserDescription = "A fully stocked snack aisle drives impulse buys.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 5,
        ))
        put("prod_candy_treats", ResearchableUpgrade(
            id = "prod_candy_treats",
            displayName = "Candy & Treats Study",
            description = "Unlocks Chocolate Bar, Gummy Bears, and Cheese Puffs.",
            teaserDescription = "Treats keep customers coming back.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 8,
            prerequisites = listOf("prod_snack_variety"),
        ))
        put("prod_premium_snacks", ResearchableUpgrade(
            id = "prod_premium_snacks",
            displayName = "Premium Snacks Study",
            description = "Unlocks Trail Mix, Rice Cakes, Mixed Nuts, and Organic Granola Bars.",
            teaserDescription = "Health-focused snackers are growing fast.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 10,
            prerequisites = listOf("prod_snack_variety"),
            requiredStoreSize = StoreSize.SMALL_GROCERY,
        ))
        put("prod_snack_bars", ResearchableUpgrade(
            id = "prod_snack_bars",
            displayName = "Snack Bar Study",
            description = "Unlocks protein bars and specialty snack bars.",
            teaserDescription = "On-the-go shoppers always grab a bar.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 8,
            prerequisites = listOf("prod_premium_snacks"),
            requiredStoreSize = StoreSize.SMALL_GROCERY,
        ))
        put("prod_snack_dips", ResearchableUpgrade(
            id = "prod_snack_dips",
            displayName = "Dips & Spreads Study",
            description = "Unlocks hummus, salsa, guacamole, and other dips.",
            teaserDescription = "Chips without dip is a missed opportunity.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 6,
            prerequisites = listOf("prod_snack_variety"),
        ))

        // ── Product Lines: Drinks ─────────────────────────────────────────────
        put("prod_beverages", ResearchableUpgrade(
            id = "prod_beverages",
            displayName = "Beverage Expansion Study",
            description = "Unlocks Orange Juice, Soda 12-Pack, Lemonade, and Ground Coffee.",
            teaserDescription = "Beverages are your highest-velocity items.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 5,
        ))
        put("prod_specialty_drinks", ResearchableUpgrade(
            id = "prod_specialty_drinks",
            displayName = "Specialty Drinks Study",
            description = "Unlocks Herbal Tea, Energy Drink, Sparkling Water, Cold Brew, and Kombucha.",
            teaserDescription = "Premium beverage margins are unbeatable.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 10,
            prerequisites = listOf("prod_beverages"),
            requiredStoreSize = StoreSize.SMALL_GROCERY,
        ))
        put("prod_juice_variety", ResearchableUpgrade(
            id = "prod_juice_variety",
            displayName = "Juice Variety Study",
            description = "Unlocks expanded juice selection.",
            teaserDescription = "More juice options mean more basket additions.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 8,
            prerequisites = listOf("prod_beverages"),
        ))
        put("prod_coffee_tea", ResearchableUpgrade(
            id = "prod_coffee_tea",
            displayName = "Coffee & Tea Selection Study",
            description = "Unlocks premium coffee and specialty tea.",
            teaserDescription = "Coffee lovers are daily shoppers.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 10,
            prerequisites = listOf("prod_specialty_drinks"),
            requiredStoreSize = StoreSize.SMALL_GROCERY,
        ))

        // ── Product Lines: Dairy ──────────────────────────────────────────────
        put("prod_dairy_basics", ResearchableUpgrade(
            id = "prod_dairy_basics",
            displayName = "Dairy Section Study",
            description = "Unlocks Milk, Eggs, Salted Butter, and Sour Cream.",
            teaserDescription = "Every household needs dairy staples.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 8,
            requiredStoreSize = StoreSize.SMALL_GROCERY,
            gateCheck = { s -> s.totalRevenue.cents >= 250_000L },
        ))
        put("prod_dairy_cheese", ResearchableUpgrade(
            id = "prod_dairy_cheese",
            displayName = "Cheese & Yogurt Study",
            description = "Unlocks Cheddar, Mozzarella, Cream Cheese, Strawberry Yogurt, Greek Yogurt, and Heavy Cream.",
            teaserDescription = "A real dairy aisle brings people in.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 10,
            prerequisites = listOf("prod_dairy_basics"),
            requiredStoreSize = StoreSize.GROCERY_STORE,
        ))
        put("prod_dairy_alternatives", ResearchableUpgrade(
            id = "prod_dairy_alternatives",
            displayName = "Dairy Alternatives Study",
            description = "Unlocks almond milk, oat milk, and plant-based dairy.",
            teaserDescription = "Plant-based is the fastest-growing dairy segment.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 10,
            prerequisites = listOf("prod_dairy_cheese"),
            requiredStoreSize = StoreSize.GROCERY_STORE,
        ))
        put("prod_plant_based", ResearchableUpgrade(
            id = "prod_plant_based",
            displayName = "Plant-Based Foods Study",
            description = "Unlocks plant-based meat, cheese, and prepared foods.",
            teaserDescription = "Plant-based shoppers are high-value customers.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 12,
            prerequisites = listOf("prod_dairy_alternatives"),
            requiredStoreSize = StoreSize.GROCERY_STORE,
        ))

        // ── Product Lines: Frozen ─────────────────────────────────────────────
        put("prod_frozen_basics", ResearchableUpgrade(
            id = "prod_frozen_basics",
            displayName = "Frozen Foods Study",
            description = "Unlocks Frozen Pizza, Frozen Vegetables, Frozen Fries, Ice Cream, and Chicken Nuggets.",
            teaserDescription = "The frozen aisle is a destination — customers come specifically for it.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 12,
            prerequisites = listOf("prod_dairy_basics"),
            gateCheck = { s -> s.totalRevenue.cents >= 1_000_000L },
        ))
        put("prod_frozen_meals", ResearchableUpgrade(
            id = "prod_frozen_meals",
            displayName = "Frozen Meals Study",
            description = "Unlocks Frozen Burritos, Waffles, Mac & Cheese, Breakfast Sandwiches, and Mango Sorbet.",
            teaserDescription = "Convenient frozen meals are high-margin items.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 10,
            prerequisites = listOf("prod_frozen_basics"),
        ))
        put("prod_frozen_desserts", ResearchableUpgrade(
            id = "prod_frozen_desserts",
            displayName = "Frozen Desserts Study",
            description = "Unlocks ice cream novelties and specialty frozen desserts.",
            teaserDescription = "Impulse frozen treats drive unplanned purchases.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 10,
            prerequisites = listOf("prod_frozen_meals"),
        ))
        put("prod_frozen_international", ResearchableUpgrade(
            id = "prod_frozen_international",
            displayName = "International Frozen Foods Study",
            description = "Unlocks imported frozen meals and international specialties.",
            teaserDescription = "Global flavors in your freezer aisle.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 12,
            prerequisites = listOf("prod_frozen_meals"),
        ))

        // ── Product Lines: Bakery ─────────────────────────────────────────────
        put("prod_bakery", ResearchableUpgrade(
            id = "prod_bakery",
            displayName = "In-Store Bakery Study",
            description = "Unlocks Butter Croissant, Cinnamon Roll, Glazed Donut, Chocolate Chip Muffin, and Sesame Bagel.",
            teaserDescription = "The smell of fresh bakery brings people in.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 12,
            prerequisites = listOf("prod_dairy_basics"),
            requiredStoreSize = StoreSize.GROCERY_STORE,
        ))
        put("prod_artisan_bread", ResearchableUpgrade(
            id = "prod_artisan_bread",
            displayName = "Artisan Bread Study",
            description = "Unlocks Sourdough Loaf, French Baguette, and Danish Pastry.",
            teaserDescription = "Artisan bread commands 3x the margin of basic bread.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 8,
            prerequisites = listOf("prod_bakery"),
            requiredStoreSize = StoreSize.GROCERY_STORE,
        ))
        put("prod_bakery_cakes", ResearchableUpgrade(
            id = "prod_bakery_cakes",
            displayName = "Cake & Pastry Study",
            description = "Unlocks cakes, pies, and specialty pastries.",
            teaserDescription = "Celebration items are high-ticket purchases.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 12,
            prerequisites = listOf("prod_artisan_bread"),
            requiredStoreSize = StoreSize.GROCERY_STORE,
        ))
        put("prod_premium_bakery", ResearchableUpgrade(
            id = "prod_premium_bakery",
            displayName = "Premium Bakery Study",
            description = "Unlocks artisan cakes and premium baked goods.",
            teaserDescription = "The highest-margin items in the store.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 15,
            prerequisites = listOf("prod_bakery_cakes"),
            requiredStoreSize = StoreSize.GROCERY_STORE,
        ))

        // ── Product Lines: Produce ────────────────────────────────────────────
        put("prod_produce", ResearchableUpgrade(
            id = "prod_produce",
            displayName = "Produce Section Study",
            description = "Unlocks Bananas, Fuji Apples, Green Lettuce, Fresh Carrots, Yellow Onion, and Avocado.",
            teaserDescription = "Fresh produce makes your store feel like a real grocery.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 12,
            requiredStoreSize = StoreSize.SMALL_GROCERY,
        ))
        put("prod_premium_produce", ResearchableUpgrade(
            id = "prod_premium_produce",
            displayName = "Premium Produce Study",
            description = "Unlocks Ripe Tomato, Broccoli, Strawberries, Red Grapes, Baby Spinach, and Bell Peppers.",
            teaserDescription = "A full produce section increases basket size by 30%.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 10,
            prerequisites = listOf("prod_produce"),
            requiredStoreSize = StoreSize.SMALL_GROCERY,
        ))
        put("prod_herbs", ResearchableUpgrade(
            id = "prod_herbs",
            displayName = "Fresh Herbs Study",
            description = "Unlocks fresh herb bundles and specialty greens.",
            teaserDescription = "Home cooks always pick up fresh herbs.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 8,
            prerequisites = listOf("prod_premium_produce"),
            requiredStoreSize = StoreSize.SMALL_GROCERY,
        ))
        put("prod_organic_produce", ResearchableUpgrade(
            id = "prod_organic_produce",
            displayName = "Organic Produce Study",
            description = "Unlocks certified organic fruits and vegetables.",
            teaserDescription = "Organic shoppers spend 40% more per basket.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 15,
            prerequisites = listOf("prod_herbs"),
            requiredStoreSize = StoreSize.SMALL_GROCERY,
        ))

        // ── Product Lines: Meat ───────────────────────────────────────────────
        put("prod_meat_counter", ResearchableUpgrade(
            id = "prod_meat_counter",
            displayName = "Meat Counter Study",
            description = "Unlocks Chicken Breast, Ground Beef, Pork Chops, and Bacon.",
            teaserDescription = "A meat department is the anchor of any grocery.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 15,
            prerequisites = listOf("prod_dairy_basics"),
            requiredStoreSize = StoreSize.SMALL_GROCERY,
            gateCheck = { s -> s.totalRevenue.cents >= 1_000_000L },
        ))
        put("prod_premium_meat", ResearchableUpgrade(
            id = "prod_premium_meat",
            displayName = "Premium Meat & Seafood Study",
            description = "Unlocks Ribeye Steak, Salmon Fillet, Shrimp, and Deli Turkey.",
            teaserDescription = "Premium cuts attract high-value shoppers.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 20,
            prerequisites = listOf("prod_meat_counter"),
            requiredStoreSize = StoreSize.SMALL_GROCERY,
        ))
        put("prod_deli_meats", ResearchableUpgrade(
            id = "prod_deli_meats",
            displayName = "Deli Meats Study",
            description = "Unlocks sliced deli meats and cold cuts.",
            teaserDescription = "Sandwich fixings are weekly essentials.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 12,
            prerequisites = listOf("prod_meat_counter"),
            requiredStoreSize = StoreSize.SMALL_GROCERY,
        ))
        put("prod_seafood_market", ResearchableUpgrade(
            id = "prod_seafood_market",
            displayName = "Seafood Market Study",
            description = "Unlocks fresh seafood selection.",
            teaserDescription = "A seafood counter elevates your whole brand.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 22,
            prerequisites = listOf("prod_premium_meat"),
            requiredStoreSize = StoreSize.SMALL_GROCERY,
        ))

        // ── Product Lines: Health & Non-food ─────────────────────────────────
        put("prod_health_beauty", ResearchableUpgrade(
            id = "prod_health_beauty",
            displayName = "Health & Beauty Study",
            description = "Unlocks Shampoo, Conditioner, Deodorant, Body Wash, Face Wash, and Toothpaste.",
            teaserDescription = "Non-food departments add reliable revenue.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 12,
            requiredStoreSize = StoreSize.GROCERY_STORE,
        ))
        put("prod_household", ResearchableUpgrade(
            id = "prod_household",
            displayName = "Household Goods Study",
            description = "Unlocks Laundry Detergent, Dish Soap, Hand Soap, Paper Towels, Toilet Paper, and Trash Bags.",
            teaserDescription = "Household essentials bring people back weekly.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 12,
            requiredStoreSize = StoreSize.GROCERY_STORE,
        ))
        put("prod_pharmacy", ResearchableUpgrade(
            id = "prod_pharmacy",
            displayName = "Pharmacy Study",
            description = "Unlocks Aspirin, Cold Medicine, Vitamins, Antacid Tablets, and Bandages.",
            teaserDescription = "OTC pharmacy is high-margin, low-return.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 15,
            prerequisites = listOf("prod_health_beauty"),
            gateCheck = { s -> s.totalRevenue.cents >= 2_500_000L },
        ))
        put("prod_electronics", ResearchableUpgrade(
            id = "prod_electronics",
            displayName = "Electronics Study",
            description = "Unlocks Headphones, USB Cable, Screen Protector, and Portable Charger.",
            teaserDescription = "Electronics accessories have massive margins.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 20,
            prerequisites = listOf("prod_household"),
            requiredStoreSize = StoreSize.SUPERCENTER,
            gateCheck = { s -> s.totalRevenue.cents >= 2_500_000L },
        ))
        put("prod_personal_care", ResearchableUpgrade(
            id = "prod_personal_care",
            displayName = "Personal Care Study",
            description = "Unlocks expanded personal care and grooming products.",
            teaserDescription = "Personal care loyalty is strong.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 10,
            prerequisites = listOf("prod_health_beauty"),
        ))
        put("prod_cleaning_supplies", ResearchableUpgrade(
            id = "prod_cleaning_supplies",
            displayName = "Cleaning Supplies Study",
            description = "Unlocks cleaning sprays, sponges, and home cleaning products.",
            teaserDescription = "Cleaning is a high-frequency purchase.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 10,
            prerequisites = listOf("prod_household"),
        ))
        put("prod_home_essentials", ResearchableUpgrade(
            id = "prod_home_essentials",
            displayName = "Home Essentials Study",
            description = "Unlocks storage, organization, and home utility items.",
            teaserDescription = "Home essentials turn casual browsers into buyers.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 12,
            prerequisites = listOf("prod_cleaning_supplies"),
            requiredStoreSize = StoreSize.SUPERCENTER,
        ))
        put("prod_wellness", ResearchableUpgrade(
            id = "prod_wellness",
            displayName = "Wellness Products Study",
            description = "Unlocks supplements, wellness teas, and natural remedies.",
            teaserDescription = "Wellness shoppers are high-spend customers.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 12,
            prerequisites = listOf("prod_pharmacy"),
        ))
        put("prod_tech_accessories", ResearchableUpgrade(
            id = "prod_tech_accessories",
            displayName = "Tech Accessories Study",
            description = "Unlocks smart home accessories and tech gadgets.",
            teaserDescription = "Tech gadgets drive impulse buys.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 15,
            prerequisites = listOf("prod_electronics"),
            requiredStoreSize = StoreSize.SUPERCENTER,
        ))

        // ── Product Lines: New Categories (Superstore+) ───────────────────────
        put("prod_deli_counter", ResearchableUpgrade(
            id = "prod_deli_counter",
            displayName = "Deli Counter Study",
            description = "Unlocks deli counter meats, cheeses, and prepared salads.",
            teaserDescription = "A deli counter is the mark of a real grocery.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 15,
            prerequisites = listOf("prod_dairy_basics"),
            requiredStoreSize = StoreSize.GROCERY_STORE,
        ))
        put("prod_deli_prepared", ResearchableUpgrade(
            id = "prod_deli_prepared",
            displayName = "Prepared Foods Study",
            description = "Unlocks hot bar, soups, and ready-to-eat meals.",
            teaserDescription = "Prepared foods are the highest-margin items per square foot.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 18,
            prerequisites = listOf("prod_deli_counter"),
            requiredStoreSize = StoreSize.GROCERY_STORE,
        ))
        put("prod_pet_basics", ResearchableUpgrade(
            id = "prod_pet_basics",
            displayName = "Pet Supplies Study",
            description = "Unlocks pet food, treats, and basic supplies.",
            teaserDescription = "Pet owners shop on a weekly schedule.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 12,
            requiredStoreSize = StoreSize.GROCERY_STORE,
        ))
        put("prod_pet_premium", ResearchableUpgrade(
            id = "prod_pet_premium",
            displayName = "Premium Pet Products Study",
            description = "Unlocks premium pet food and accessories.",
            teaserDescription = "Pet parents spare no expense.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 15,
            prerequisites = listOf("prod_pet_basics"),
            requiredStoreSize = StoreSize.GROCERY_STORE,
        ))
        put("prod_baby_basics", ResearchableUpgrade(
            id = "prod_baby_basics",
            displayName = "Baby Products Study",
            description = "Unlocks diapers, formula, and baby essentials.",
            teaserDescription = "Baby products are non-negotiable purchases.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 15,
            prerequisites = listOf("prod_household"),
            requiredStoreSize = StoreSize.GROCERY_STORE,
        ))
        put("prod_baby_gear", ResearchableUpgrade(
            id = "prod_baby_gear",
            displayName = "Baby Gear Study",
            description = "Unlocks baby toys, bottles, and accessories.",
            teaserDescription = "Baby gear drives high-ticket basket sizes.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 18,
            prerequisites = listOf("prod_baby_basics"),
            requiredStoreSize = StoreSize.GROCERY_STORE,
        ))

        // ── Product Lines: Supercenter-only Categories ────────────────────────
        put("prod_office_basics", ResearchableUpgrade(
            id = "prod_office_basics",
            displayName = "Office Supplies Study",
            description = "Unlocks pens, paper, folders, and basic office supplies.",
            teaserDescription = "Office runs are convenient trips.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 10,
            requiredStoreSize = StoreSize.SUPERCENTER,
        ))
        put("prod_school_supplies", ResearchableUpgrade(
            id = "prod_school_supplies",
            displayName = "School Supplies Study",
            description = "Unlocks notebooks, backpacks, and school essentials.",
            teaserDescription = "Back-to-school season is a massive revenue opportunity.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 10,
            prerequisites = listOf("prod_office_basics"),
            requiredStoreSize = StoreSize.SUPERCENTER,
        ))
        put("prod_toys_basics", ResearchableUpgrade(
            id = "prod_toys_basics",
            displayName = "Toys & Games Study",
            description = "Unlocks board games, action figures, and everyday toys.",
            teaserDescription = "Toys are impulse purchases — location matters.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 12,
            requiredStoreSize = StoreSize.SUPERCENTER,
        ))
        put("prod_toys_premium", ResearchableUpgrade(
            id = "prod_toys_premium",
            displayName = "Premium Toys Study",
            description = "Unlocks high-value toys and collectibles.",
            teaserDescription = "Premium toys are holiday must-haves.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 18,
            prerequisites = listOf("prod_toys_basics"),
            requiredStoreSize = StoreSize.SUPERCENTER,
        ))
        put("prod_auto_basics", ResearchableUpgrade(
            id = "prod_auto_basics",
            displayName = "Automotive Study",
            description = "Unlocks motor oil, wiper blades, and car essentials.",
            teaserDescription = "Car supplies are a destination purchase.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 12,
            requiredStoreSize = StoreSize.SUPERCENTER,
        ))
        put("prod_auto_care", ResearchableUpgrade(
            id = "prod_auto_care",
            displayName = "Auto Care Study",
            description = "Unlocks car care products, air fresheners, and accessories.",
            teaserDescription = "Car owners spend regularly on their vehicles.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 10,
            prerequisites = listOf("prod_auto_basics"),
            requiredStoreSize = StoreSize.SUPERCENTER,
        ))
        put("prod_garden_basics", ResearchableUpgrade(
            id = "prod_garden_basics",
            displayName = "Garden Center Study",
            description = "Unlocks seeds, soil, and basic gardening supplies.",
            teaserDescription = "Home gardening is growing year-over-year.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 10,
            requiredStoreSize = StoreSize.SUPERCENTER,
        ))
        put("prod_garden_tools", ResearchableUpgrade(
            id = "prod_garden_tools",
            displayName = "Garden Tools Study",
            description = "Unlocks hand tools, planters, and garden accessories.",
            teaserDescription = "Tools are high-margin, low-return items.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 12,
            prerequisites = listOf("prod_garden_basics"),
            requiredStoreSize = StoreSize.SUPERCENTER,
        ))
        put("prod_seasonal_summer", ResearchableUpgrade(
            id = "prod_seasonal_summer",
            displayName = "Summer Merchandise Study",
            description = "Unlocks sunscreen, pool toys, and summer essentials.",
            teaserDescription = "Seasonal merchandise drives peak-season revenue.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 15,
            prerequisites = listOf("prod_household"),
            requiredStoreSize = StoreSize.SUPERCENTER,
        ))
        put("prod_seasonal_outdoor", ResearchableUpgrade(
            id = "prod_seasonal_outdoor",
            displayName = "Outdoor Living Study",
            description = "Unlocks outdoor furniture, grilling, and patio items.",
            teaserDescription = "Outdoor living is a high-ticket category.",
            category = ResearchCategory.PRODUCT_LINES,
            researchCost = 18,
            prerequisites = listOf("prod_seasonal_summer"),
            requiredStoreSize = StoreSize.SUPERCENTER,
        ))

        // ── Operations ────────────────────────────────────────────────────────
        put("bulk_ordering", ResearchableUpgrade(
            id = "bulk_ordering",
            displayName = "Bulk Procurement Study",
            description = "Unlocks bulk ordering with volume discounts.",
            teaserDescription = "Volume discounts can save you thousands.",
            category = ResearchCategory.OPERATIONS,
            researchCost = 12,
        ))
        put("extra_truck_slots", ResearchableUpgrade(
            id = "extra_truck_slots",
            displayName = "Logistics Optimization",
            description = "Unlocks the ability to purchase extra weekly truck delivery slots.",
            teaserDescription = "More deliveries means fresher shelves.",
            category = ResearchCategory.OPERATIONS,
            researchCost = 15,
        ))
        put("truck_upgrade_enhanced", ResearchableUpgrade(
            id = "truck_upgrade_enhanced",
            displayName = "Enhanced Fleet Study",
            description = "Unlocks upgraded truck capacity: 3,000 regular / 750 fresh case packs.",
            teaserDescription = "Bigger trucks mean fewer stockouts.",
            category = ResearchCategory.OPERATIONS,
            researchCost = 40,
            prerequisites = listOf("extra_truck_slots"),
            requiredStoreSize = StoreSize.SUPERSTORE,
        ))
        put("truck_upgrade_heavy", ResearchableUpgrade(
            id = "truck_upgrade_heavy",
            displayName = "Heavy Fleet Study",
            description = "Unlocks maximum truck capacity: 5,000 regular / 1,250 fresh case packs.",
            teaserDescription = "The ultimate logistics upgrade.",
            category = ResearchCategory.OPERATIONS,
            researchCost = 80,
            prerequisites = listOf("truck_upgrade_enhanced"),
            requiredStoreSize = StoreSize.SUPERCENTER,
        ))
        put("fresh_auto_order", ResearchableUpgrade(
            id = "fresh_auto_order",
            displayName = "Fresh Inventory Automation",
            description = "Unlocks fresh item auto-ordering configuration.",
            teaserDescription = "Never run out of fresh items again.",
            category = ResearchCategory.OPERATIONS,
            researchCost = 18,
            prerequisites = listOf("prod_frozen_basics"),
        ))
        put("early_truck", ResearchableUpgrade(
            id = "early_truck",
            displayName = "Express Delivery Research",
            description = "Unlocks the ability to request an early truck delivery.",
            teaserDescription = "Emergency restocks save lost sales.",
            category = ResearchCategory.OPERATIONS,
            researchCost = 10,
            prerequisites = listOf("extra_truck_slots"),
        ))

        // ── Pricing ───────────────────────────────────────────────────────────
        // Tiered chain: store-wide markup → category markups → per-item overrides.
        // Each level requires the broader one above it.
        put("default_markup", ResearchableUpgrade(
            id = "default_markup",
            displayName = "Store Pricing Strategy",
            description = "Unlocks the store-wide default markup slider.",
            teaserDescription = "A consistent markup strategy builds margins.",
            category = ResearchCategory.PRICING,
            researchCost = 10,
        ))
        put("category_pricing", ResearchableUpgrade(
            id = "category_pricing",
            displayName = "Category Pricing Analysis",
            description = "Unlocks category-level markup sliders.",
            teaserDescription = "Different categories have different price sensitivities.",
            category = ResearchCategory.PRICING,
            researchCost = 15,
            prerequisites = listOf("default_markup"),
        ))
        put("item_pricing", ResearchableUpgrade(
            id = "item_pricing",
            displayName = "Item-Level Pricing Study",
            description = "Unlocks per-item price override controls.",
            teaserDescription = "Surgical pricing is the most profitable strategy.",
            category = ResearchCategory.PRICING,
            researchCost = 20,
            prerequisites = listOf("category_pricing"),
        ))
        put("revenue_reputation", ResearchableUpgrade(
            id = "revenue_reputation",
            displayName = "Customer Loyalty Study",
            description = "Unlocks the Store Reputation system.",
            teaserDescription = "A trusted store attracts more shoppers and bigger baskets.",
            category = ResearchCategory.PRICING,
            researchCost = 20,
            prerequisites = listOf("category_pricing"),
            requiredStoreSize = StoreSize.GROCERY_STORE,
        ))

        // ── Staff Management ──────────────────────────────────────────────────
        put("manager_hiring", ResearchableUpgrade(
            id = "manager_hiring",
            displayName = "Management Structure Study",
            description = "Unlocks the ability to hire Managers.",
            teaserDescription = "You need managers before you can scale.",
            category = ResearchCategory.STAFF_MANAGEMENT,
            researchCost = 20,
            gateCheck = { s -> s.hiredEntityRegistry.hiredEntities.size >= 3 },
        ))
        put("auto_hire", ResearchableUpgrade(
            id = "auto_hire",
            displayName = "Automated HR Study",
            description = "Unlocks auto-hire budget settings.",
            teaserDescription = "Let management handle staffing decisions.",
            category = ResearchCategory.STAFF_MANAGEMENT,
            researchCost = 25,
            prerequisites = listOf("manager_hiring"),
        ))
    }

    /** Department membership for [ResearchCategory.PRODUCT_LINES] research. */
    private val departmentMembers: Map<ProductDepartment, List<String>> = mapOf(
        ProductDepartment.GROCERY to listOf(
            "prod_breakfast", "prod_condiments", "prod_baking", "prod_canned",
            "prod_premium_grocery", "prod_organic", "prod_gourmet", "prod_international",
            "prod_spices", "prod_pasta_sauces",
        ),
        ProductDepartment.SNACKS to listOf(
            "prod_snack_variety", "prod_candy_treats", "prod_premium_snacks",
            "prod_snack_bars", "prod_snack_dips",
        ),
        ProductDepartment.DRINKS to listOf(
            "prod_beverages", "prod_specialty_drinks", "prod_juice_variety", "prod_coffee_tea",
        ),
        ProductDepartment.FROZEN to listOf(
            "prod_frozen_basics", "prod_frozen_meals", "prod_frozen_desserts", "prod_frozen_international",
        ),
        ProductDepartment.BAKERY to listOf(
            "prod_bakery", "prod_artisan_bread", "prod_bakery_cakes", "prod_premium_bakery",
        ),
        ProductDepartment.FRESH to listOf(
            // Dairy
            "prod_dairy_basics", "prod_dairy_cheese", "prod_dairy_alternatives", "prod_plant_based",
            // Produce
            "prod_produce", "prod_premium_produce", "prod_herbs", "prod_organic_produce",
            // Deli
            "prod_deli_counter", "prod_deli_prepared",
            // Meat & Seafood
            "prod_meat_counter", "prod_premium_meat", "prod_deli_meats", "prod_seafood_market",
        ),
        ProductDepartment.HEALTH_NONFOOD to listOf(
            "prod_health_beauty", "prod_household", "prod_pharmacy", "prod_electronics",
            "prod_personal_care", "prod_cleaning_supplies", "prod_home_essentials",
            "prod_wellness", "prod_tech_accessories",
        ),
        ProductDepartment.PET_BABY to listOf(
            "prod_pet_basics", "prod_pet_premium", "prod_baby_basics", "prod_baby_gear",
        ),
        ProductDepartment.GENERAL_MERCH to listOf(
            "prod_office_basics", "prod_school_supplies", "prod_toys_basics", "prod_toys_premium",
            "prod_auto_basics", "prod_auto_care", "prod_garden_basics", "prod_garden_tools",
            "prod_seasonal_summer", "prod_seasonal_outdoor",
        ),
    )

    private val departmentById: Map<String, ProductDepartment> = buildMap {
        for ((dept, ids) in departmentMembers) {
            for (id in ids) put(id, dept)
        }
    }

    /** Department for a product-line upgrade id, or null if not a department-gated upgrade. */
    fun departmentOf(id: String): ProductDepartment? = departmentById[id]
}
