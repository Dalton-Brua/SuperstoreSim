# Future Items Expansion Plan

## Context

The game currently has **136 items** across **12 categories**, with a planned research system (see `plans/plan-researchSystem.md`) that will gate items behind **24 product-line research entries**. Many of these gates have only 3-4 items, making discoveries feel thin. Additionally, real-world superstores carry entire departments not yet represented.

This document defines **~218 new items** across existing and new categories, brings the total to **~354 items** across **20 categories**, and adds **31 new research gates** (55 total product-line gates). It also introduces **28 item affinity groups** — tagged cross-selling relationships (e.g. `breakfast`, `burger_bbq`, `cleaning_day`) that make customer baskets feel realistic by boosting the purchase weight of related items after one is selected. All items follow the existing `items.json` format (prices in cents, casePack, purchaseWeight, optional shelfLifeDays/soldByWeight).

**Gating philosophy**: Items are gated by **store size** (must have a large enough store), **research** (must spend research points to discover), and **prerequisite research** (must discover earlier gates first). No revenue gates — progression is driven by store upgrades and research, not arbitrary revenue thresholds.

---

## Part 1: New Items for Existing Research Gates

Flesh out existing gates that currently have fewer than 5 items.

### GROCERY

**`prod_breakfast`** (5 pts) — currently: Cereal, Oatmeal, Pancake Mix (3)
| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Granola | $4.99 | $2.99 | 8 | 5.0 | |
| Instant Grits | $2.49 | $1.29 | 12 | 4.0 | |
| Toaster Pastries | $3.49 | $1.99 | 12 | 6.0 | |

**`prod_baking`** (6 pts) — currently: Sugar, Flour, Salt, Pepper, Vegetable Oil (5)
| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Baking Soda | $0.99 | $0.49 | 24 | 3.0 | |
| Vanilla Extract | $4.99 | $2.99 | 12 | 2.0 | |
| Cornstarch | $1.49 | $0.79 | 18 | 2.5 | |

**`prod_canned`** (6 pts) — currently: Tomatoes, Soup, Corn, Green Beans, Broth (5)
| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Canned Tuna | $1.29 | $0.79 | 24 | 6.0 | |
| Canned Pinto Beans | $0.99 | $0.59 | 24 | 5.5 | |
| Tomato Sauce | $0.99 | $0.49 | 24 | 6.5 | |

**`prod_gourmet`** (25 pts) — currently: Truffle Oil, Imported Penne, Artisanal Crackers, Sun-Dried Tomatoes (4)
| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Aged Parmesan Wedge | $12.99 | $8.99 | 4 | 1.5 | 14-day shelf life |
| Kalamata Olives | $5.99 | $3.79 | 8 | 2.0 | |
| Dijon Mustard | $4.49 | $2.69 | 10 | 2.5 | |

### SNACKS

**`prod_candy_treats`** (8 pts) — currently: Chocolate Bar, Gummy Bears, Cheese Puffs (3)
| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Sour Gummy Worms | $2.49 | $1.19 | 24 | 5.5 | |
| Peanut Butter Cups | $1.49 | $0.69 | 36 | 7.0 | |
| Hard Candy Bag | $3.49 | $1.69 | 12 | 4.0 | |

**`prod_premium_snacks`** (10 pts) — currently: Trail Mix, Rice Cakes, Mixed Nuts, Organic Granola Bars (4)
| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Dried Mango Slices | $4.99 | $2.79 | 10 | 3.5 | |
| Dark Chocolate Almonds | $5.99 | $3.49 | 8 | 3.0 | |

### DRINKS

**`prod_beverages`** (5 pts) — currently: OJ, Soda 12-Pack, Lemonade, Ground Coffee (4)
| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Cranberry Juice | $3.99 | $2.49 | 8 | 5.5 | |
| Grape Juice | $3.49 | $2.09 | 8 | 5.0 | |

**`prod_specialty_drinks`** (10 pts) — currently: Herbal Tea, Energy Drink, Sparkling Water, Cold Brew, Kombucha (5)
| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Coconut Water | $2.99 | $1.59 | 12 | 4.0 | |
| Probiotic Drink | $3.49 | $1.99 | 12 | 3.0 | |

### DAIRY

**`prod_dairy_cheese`** (10 pts) — currently: Cheddar, Mozzarella, Cream Cheese, Strawberry Yogurt, Greek Yogurt, Heavy Cream (6)
| Name | Price | Unit Cost | Case Pack | Wt | Shelf Life | Notes |
|------|-------|-----------|-----------|-----|------------|-------|
| Cottage Cheese | $3.49 | $2.19 | 10 | 5.0 | 7 days | |
| Swiss Cheese | $5.99 | $4.19 | 8 | 4.5 | 7 days | |
| Whipped Cream | $3.99 | $2.49 | 10 | 4.0 | 7 days | |

### FROZEN

**`prod_frozen_meals`** (10 pts) — currently: Burritos, Waffles, Mac & Cheese, Breakfast Sandwiches, Sorbet (5)
| Name | Price | Unit Cost | Case Pack | Wt | Shelf Life | Notes |
|------|-------|-----------|-----------|-----|------------|-------|
| Frozen Pot Pie | $3.49 | $1.99 | 12 | 5.0 | 30 days | |
| Frozen Fish Sticks | $4.99 | $2.99 | 8 | 5.5 | 30 days | |
| Frozen Egg Rolls | $5.49 | $3.19 | 8 | 4.5 | 30 days | |

### BAKERY

**`prod_artisan_bread`** (8 pts) — currently: Sourdough, Baguette, Danish (3)
| Name | Price | Unit Cost | Case Pack | Wt | Shelf Life | Notes |
|------|-------|-----------|-----------|-----|------------|-------|
| Ciabatta Roll | $2.49 | $0.89 | 12 | 5.0 | 3 days | |
| Pumpernickel Bread | $4.49 | $1.79 | 8 | 4.0 | 3 days | |

### PRODUCE

**`prod_premium_produce`** (10 pts) — currently: Tomato, Broccoli, Strawberries, Grapes, Spinach, Bell Peppers (6)
| Name | Price | Unit Cost | Case Pack | Wt | Shelf Life | Notes |
|------|-------|-----------|-----------|-----|------------|-------|
| Blueberries | $4.99 | $2.99 | 10 | 5.0 | 5 days | |
| Mushrooms | $2.99 | $1.59 | 15 | 5.5 | 5 days | |
| Cucumbers | $1.29 | $0.59 | 20 | 6.0 | 5 days | sold by weight |

### MEAT

**`prod_meat_counter`** (15 pts) — currently: Chicken Breast, Ground Beef, Pork Chops, Bacon (4)
| Name | Price | Unit Cost | Case Pack | Wt | Shelf Life | Notes |
|------|-------|-----------|-----------|-----|------------|-------|
| Italian Sausage | $4.99 | $3.29 | 6 | 5.0 | 5 days | |
| Ground Turkey | $5.49 | $3.69 | 4 | 5.5 | 5 days | |

**`prod_premium_meat`** (20 pts) — currently: Ribeye, Salmon, Shrimp, Deli Turkey (4)
| Name | Price | Unit Cost | Case Pack | Wt | Shelf Life | Notes |
|------|-------|-----------|-----------|-----|------------|-------|
| Lamb Chops | $14.99 | $10.99 | 3 | 2.0 | 5 days | sold by weight |
| Crab Legs | $19.99 | $14.49 | 2 | 1.5 | 5 days | sold by weight |

### HEALTH

**`prod_health_beauty`** (12 pts) — currently: Shampoo, Conditioner, Deodorant, Body Wash, Face Wash, Toothpaste (6)
| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Sunscreen | $8.99 | $4.49 | 6 | 2.5 | |
| Body Lotion | $6.99 | $3.49 | 8 | 3.5 | |
| Razor Pack | $7.99 | $3.99 | 6 | 2.0 | |

### HOUSEHOLD

**`prod_household`** (12 pts) — currently: Laundry Detergent, Dish Soap, Hand Soap, Paper Towels, Toilet Paper, Trash Bags (6)
| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| All-Purpose Cleaner | $3.99 | $2.19 | 12 | 4.0 | |
| Sponges 3-Pack | $2.99 | $1.49 | 18 | 3.5 | |
| Aluminum Foil | $4.49 | $2.49 | 10 | 3.0 | |
| Plastic Wrap | $3.49 | $1.79 | 12 | 3.0 | |

### PHARMACY

**`prod_pharmacy`** (15 pts) — currently: Aspirin, Cold Medicine, Vitamins, Antacid, Bandages (5)
| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Allergy Medicine | $12.99 | $8.49 | 4 | 2.0 | |
| Ibuprofen | $8.99 | $5.99 | 6 | 2.5 | |
| First Aid Kit | $14.99 | $9.49 | 4 | 1.5 | |

### ELECTRONICS

**`prod_electronics`** (20 pts) — currently: Headphones, USB Cable, Screen Protector, Portable Charger (4)
| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Wireless Earbuds | $29.99 | $17.99 | 3 | 0.5 | |
| Phone Case | $14.99 | $7.49 | 8 | 1.0 | |
| HDMI Cable | $12.99 | $6.49 | 10 | 1.0 | |
| Bluetooth Speaker | $34.99 | $21.99 | 2 | 0.5 | |

**Part 1 subtotal: ~44 new items added to existing gates**

---

## Part 2: New Research Gates for Existing Categories

Each gate extends category depth, giving players more to unlock in mid and late game.

### GROCERY — New Gates

#### `prod_international` — "International Foods Study"
- **Research cost**: 12 pts
- **Prerequisite**: `prod_condiments`

| Name | Price | Unit Cost | Case Pack | Wt | Shelf Life | Notes |
|------|-------|-----------|-----------|-----|------------|-------|
| Sriracha Sauce | $3.99 | $2.19 | 12 | 3.5 | | |
| Coconut Milk | $2.49 | $1.39 | 18 | 4.0 | | canned |
| Curry Paste | $3.49 | $1.99 | 12 | 2.0 | | |
| Rice Noodles | $2.49 | $1.29 | 18 | 3.5 | | |
| Salsa | $3.99 | $2.29 | 10 | 5.0 | | |
| Flour Tortillas | $3.49 | $1.99 | 10 | 5.5 | 7 days | perishable |

#### `prod_spices` — "Spice Rack Study"
- **Research cost**: 8 pts
- **Prerequisite**: `prod_baking`

| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Garlic Powder | $3.49 | $1.89 | 12 | 2.0 | |
| Cumin | $3.99 | $2.29 | 12 | 1.5 | |
| Paprika | $3.49 | $1.99 | 12 | 1.5 | |
| Cinnamon | $3.99 | $2.29 | 12 | 1.5 | |
| Italian Seasoning | $3.49 | $1.99 | 12 | 1.5 | |

#### `prod_pasta_sauces` — "Pasta & Sauce Study"
- **Research cost**: 6 pts
- **Prerequisite**: `prod_canned`

| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Marinara Sauce | $3.49 | $1.99 | 10 | 5.5 | |
| Alfredo Sauce | $3.99 | $2.29 | 10 | 4.5 | |
| Salsa Verde | $3.49 | $1.99 | 10 | 4.0 | |
| Taco Seasoning | $1.49 | $0.69 | 24 | 3.0 | |

#### `prod_plant_based` — "Plant-Based Foods Study"
- **Research cost**: 12 pts
- **Prerequisite**: `prod_dairy_alternatives`

| Name | Price | Unit Cost | Case Pack | Wt | Shelf Life | Notes |
|------|-------|-----------|-----------|-----|------------|-------|
| Veggie Burgers 4-Pack | $6.99 | $4.29 | 6 | 3.5 | 14 days | |
| Tofu | $3.49 | $1.89 | 10 | 4.0 | 7 days | |
| Tempeh | $4.49 | $2.69 | 8 | 3.0 | 7 days | |
| Plant-Based Sausage | $6.99 | $4.49 | 6 | 3.0 | 7 days | |

### SNACKS — New Gates

#### `prod_snack_bars` — "Snack Bar Study"
- **Research cost**: 8 pts
- **Prerequisite**: `prod_premium_snacks`

| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Protein Bar | $2.99 | $1.49 | 18 | 5.0 | |
| Fruit Snack Pack | $3.99 | $1.99 | 18 | 5.5 | |
| Beef Jerky | $7.99 | $4.99 | 6 | 3.0 | |
| Seaweed Snack | $2.99 | $1.49 | 18 | 2.5 | |

#### `prod_snack_dips` — "Dips & Spreads Study"
- **Research cost**: 6 pts
- **Prerequisite**: `prod_snack_variety`

| Name | Price | Unit Cost | Case Pack | Wt | Shelf Life | Notes |
|------|-------|-----------|-----------|-----|------------|-------|
| Guacamole | $4.99 | $2.99 | 8 | 4.0 | 5 days | perishable |
| Salsa Dip | $3.99 | $2.29 | 10 | 4.5 | | |
| French Onion Dip | $3.49 | $1.99 | 10 | 4.5 | 7 days | |
| Hummus | $4.49 | $2.69 | 8 | 3.5 | 7 days | |

### DRINKS — New Gates

#### `prod_juice_variety` — "Juice Variety Study"
- **Research cost**: 8 pts
- **Prerequisite**: `prod_beverages`

| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Smoothie Bottle | $4.99 | $2.99 | 8 | 4.0 | 7-day shelf life |
| Vegetable Juice | $3.99 | $2.29 | 8 | 5.0 | |
| Iced Tea Gallon | $3.99 | $2.29 | 6 | 6.0 | |
| Sports Drink 8-Pack | $6.99 | $3.99 | 4 | 7.0 | |

#### `prod_coffee_tea` — "Coffee & Tea Selection Study"
- **Research cost**: 10 pts
- **Prerequisite**: `prod_specialty_drinks`

| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Espresso Pods 12-Pack | $9.99 | $6.49 | 4 | 3.0 | |
| Green Tea 20-Pack | $4.99 | $2.69 | 12 | 2.5 | |
| Whole Bean Coffee | $12.99 | $8.99 | 4 | 4.0 | |
| Chai Concentrate | $5.49 | $3.19 | 8 | 4.0 | |

### DAIRY — New Gates

#### `prod_dairy_alternatives` — "Dairy Alternatives Study"
- **Research cost**: 10 pts
- **Prerequisite**: `prod_dairy_cheese`

| Name | Price | Unit Cost | Case Pack | Wt | Shelf Life | Notes |
|------|-------|-----------|-----------|-----|------------|-------|
| Oat Milk | $5.49 | $3.49 | 6 | 5.0 | 14 days | |
| Almond Milk | $4.99 | $2.99 | 6 | 5.0 | 14 days | |
| Vegan Cheese Slices | $5.99 | $3.99 | 6 | 3.0 | 14 days | |
| Coconut Yogurt | $4.49 | $2.79 | 8 | 3.5 | 14 days | |
| Egg Substitute | $4.99 | $3.19 | 8 | 3.0 | 14 days | |

### FROZEN — New Gates

#### `prod_frozen_desserts` — "Frozen Desserts Study"
- **Research cost**: 10 pts
- **Prerequisite**: `prod_frozen_meals`

| Name | Price | Unit Cost | Case Pack | Wt | Shelf Life | Notes |
|------|-------|-----------|-----------|-----|------------|-------|
| Ice Cream Sandwiches 6-Pack | $4.99 | $2.99 | 8 | 5.0 | 30 days | |
| Frozen Fruit Bars 6-Pack | $4.49 | $2.69 | 8 | 4.0 | 30 days | |
| Frozen Cheesecake | $7.99 | $5.29 | 4 | 3.0 | 30 days | |
| Popsicles 12-Pack | $3.99 | $2.29 | 8 | 5.5 | 30 days | |
| Gelato Pint | $6.49 | $4.29 | 6 | 3.5 | 30 days | |

#### `prod_frozen_international` — "International Frozen Foods Study"
- **Research cost**: 12 pts
- **Prerequisite**: `prod_frozen_meals`

| Name | Price | Unit Cost | Case Pack | Wt | Shelf Life | Notes |
|------|-------|-----------|-----------|-----|------------|-------|
| Frozen Dumplings | $6.99 | $4.29 | 6 | 4.0 | 30 days | |
| Frozen Samosas | $5.99 | $3.49 | 8 | 4.5 | 30 days | |
| Frozen Spring Rolls | $5.49 | $3.19 | 8 | 4.0 | 30 days | |
| Frozen Empanadas | $6.99 | $4.29 | 6 | 4.0 | 30 days | |

### BAKERY — New Gates

#### `prod_bakery_cakes` — "Cake & Pastry Study"
- **Research cost**: 12 pts
- **Prerequisite**: `prod_artisan_bread`

| Name | Price | Unit Cost | Case Pack | Wt | Shelf Life | Notes |
|------|-------|-----------|-----------|-----|------------|-------|
| Chocolate Cake Slice | $4.49 | $1.79 | 8 | 4.0 | 3 days | |
| Apple Pie | $9.99 | $4.49 | 4 | 3.5 | 3 days | |
| Brownies 6-Pack | $5.99 | $2.49 | 6 | 4.0 | 3 days | |
| Lemon Tart | $3.99 | $1.59 | 8 | 3.0 | 3 days | |

#### `prod_premium_bakery` — "Premium Bakery Study"
- **Research cost**: 15 pts
- **Prerequisite**: `prod_bakery_cakes`

| Name | Price | Unit Cost | Case Pack | Wt | Shelf Life | Notes |
|------|-------|-----------|-----------|-----|------------|-------|
| Birthday Cake 8-inch | $19.99 | $9.99 | 2 | 2.0 | 3 days | high margin |
| Macarons 6-Pack | $12.99 | $6.99 | 4 | 1.5 | 3 days | |
| Fruit Tart | $7.99 | $3.99 | 4 | 2.5 | 3 days | |
| Tiramisu Slice | $5.99 | $2.79 | 6 | 2.5 | 3 days | |

### PRODUCE — New Gates

#### `prod_herbs` — "Fresh Herbs Study"
- **Research cost**: 8 pts
- **Prerequisite**: `prod_premium_produce`

| Name | Price | Unit Cost | Case Pack | Wt | Shelf Life | Notes |
|------|-------|-----------|-----------|-----|------------|-------|
| Fresh Basil | $2.99 | $1.49 | 12 | 3.0 | 5 days | |
| Fresh Cilantro | $1.49 | $0.69 | 20 | 3.0 | 5 days | |
| Lemons | $0.99 | $0.39 | 30 | 6.0 | 5 days | |
| Limes | $0.79 | $0.29 | 30 | 5.5 | 5 days | |
| Fresh Garlic | $0.69 | $0.29 | 30 | 5.0 | 5 days | |

#### `prod_organic_produce` — "Organic Produce Study"
- **Research cost**: 15 pts
- **Prerequisite**: `prod_herbs`

| Name | Price | Unit Cost | Case Pack | Wt | Shelf Life | Notes |
|------|-------|-----------|-----------|-----|------------|-------|
| Organic Kale | $3.99 | $2.19 | 10 | 4.0 | 5 days | |
| Organic Sweet Potatoes | $2.99 | $1.59 | 15 | 6.0 | 5 days | sold by weight |
| Organic Apples | $3.49 | $1.99 | 12 | 5.0 | 5 days | sold by weight |
| Organic Baby Carrots | $2.99 | $1.59 | 12 | 4.5 | 5 days | |

### MEAT — New Gates

#### `prod_deli_meats` — "Deli Meats Study"
- **Research cost**: 12 pts
- **Prerequisite**: `prod_meat_counter`

| Name | Price | Unit Cost | Case Pack | Wt | Shelf Life | Notes |
|------|-------|-----------|-----------|-----|------------|-------|
| Deli Ham | $7.49 | $5.49 | 4 | 4.0 | 5 days | |
| Deli Roast Beef | $9.99 | $7.49 | 3 | 3.0 | 5 days | |
| Deli Salami | $6.99 | $4.79 | 4 | 3.5 | 5 days | |
| Hot Dogs | $4.49 | $2.79 | 8 | 5.5 | 5 days | |
| Bratwurst | $5.99 | $3.99 | 6 | 4.0 | 5 days | |

#### `prod_seafood_market` — "Seafood Market Study"
- **Research cost**: 22 pts
- **Prerequisite**: `prod_premium_meat`

| Name | Price | Unit Cost | Case Pack | Wt | Shelf Life | Notes |
|------|-------|-----------|-----------|-----|------------|-------|
| Lobster Tail | $24.99 | $17.99 | 2 | 1.0 | 5 days | sold by weight |
| Tuna Steak | $14.99 | $10.49 | 3 | 1.5 | 5 days | sold by weight |
| Filet Mignon | $29.99 | $21.99 | 2 | 1.0 | 5 days | sold by weight |
| Scallops | $19.99 | $14.49 | 2 | 1.0 | 5 days | sold by weight |

### HEALTH — New Gates

#### `prod_personal_care` — "Personal Care Study"
- **Research cost**: 10 pts
- **Prerequisite**: `prod_health_beauty`

| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Cotton Swabs | $3.49 | $1.69 | 12 | 2.0 | |
| Hair Gel | $5.99 | $2.99 | 8 | 3.0 | |
| Lip Balm | $2.99 | $1.39 | 24 | 2.0 | |
| Hand Sanitizer | $4.49 | $2.29 | 10 | 3.0 | |
| Mouthwash | $5.99 | $2.99 | 6 | 3.5 | |

### HOUSEHOLD — New Gates

#### `prod_cleaning_supplies` — "Cleaning Supplies Study"
- **Research cost**: 10 pts
- **Prerequisite**: `prod_household`

| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Glass Cleaner | $3.99 | $2.19 | 10 | 3.5 | |
| Bleach | $4.49 | $2.49 | 6 | 4.0 | |
| Floor Cleaner | $5.99 | $3.49 | 6 | 3.5 | |
| Disinfectant Wipes | $4.99 | $2.79 | 8 | 3.0 | |

#### `prod_home_essentials` — "Home Essentials Study"
- **Research cost**: 12 pts
- **Prerequisite**: `prod_cleaning_supplies`

| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Batteries 8-Pack | $8.99 | $4.99 | 6 | 2.5 | |
| Light Bulbs 4-Pack | $6.99 | $3.49 | 8 | 2.0 | |
| Candles 3-Pack | $9.99 | $5.49 | 6 | 2.5 | |
| Storage Bins 3-Pack | $14.99 | $8.99 | 3 | 2.0 | |

### PHARMACY — New Gates

#### `prod_wellness` — "Wellness Products Study"
- **Research cost**: 12 pts
- **Prerequisite**: `prod_pharmacy`

| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Fish Oil Supplements | $14.99 | $9.99 | 4 | 2.0 | |
| Melatonin | $9.99 | $6.49 | 6 | 1.5 | |
| Probiotics | $19.99 | $13.99 | 3 | 1.0 | |
| Zinc Lozenges | $6.99 | $3.99 | 8 | 2.0 | |

### ELECTRONICS — New Gates

#### `prod_tech_accessories` — "Tech Accessories Study"
- **Research cost**: 15 pts
- **Prerequisite**: `prod_electronics`

| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Wireless Mouse | $24.99 | $14.99 | 3 | 1.0 | |
| USB Flash Drive | $14.99 | $7.99 | 8 | 0.8 | |
| Car Phone Mount | $19.99 | $10.99 | 4 | 0.8 | |
| Laptop Stand | $34.99 | $21.99 | 2 | 0.5 | |
| Webcam | $39.99 | $25.99 | 2 | 0.5 | |

**Part 2 subtotal: ~20 new gates, ~85 new items**

---

## Part 3: New Categories

Eight new categories with their own research gates. These unlock at Superstore/Supercenter store sizes.

### DELI (Deli & Prepared Foods)
*Perishable prepared foods — natural extension of fresh departments.*

#### `prod_deli_counter` — "Deli Counter Study"
- **Research cost**: 15 pts
- **Prerequisite**: `prod_dairy_basics`
- **Store size**: Superstore+

| Name | Price | Unit Cost | Case Pack | Wt | Shelf Life | Notes |
|------|-------|-----------|-----------|-----|------------|-------|
| Rotisserie Chicken | $7.99 | $4.49 | 4 | 5.0 | 3 days | high demand |
| Potato Salad | $4.99 | $2.49 | 8 | 4.0 | 3 days | |
| Macaroni Salad | $4.49 | $2.19 | 8 | 4.0 | 3 days | |
| Chicken Wings 8-Pack | $8.99 | $5.49 | 4 | 4.5 | 3 days | |
| Sandwich Wrap | $6.99 | $3.49 | 6 | 3.5 | 3 days | |

#### `prod_deli_prepared` — "Prepared Foods Study"
- **Research cost**: 18 pts
- **Prerequisite**: `prod_deli_counter`

| Name | Price | Unit Cost | Case Pack | Wt | Shelf Life | Notes |
|------|-------|-----------|-----------|-----|------------|-------|
| Sushi 8-Piece | $9.99 | $5.99 | 4 | 2.0 | 3 days | high margin |
| Soup Quart | $5.99 | $2.99 | 6 | 4.0 | 3 days | |
| Fresh Pizza Slice | $2.99 | $1.29 | 12 | 5.0 | 3 days | |
| Fruit Cup | $4.49 | $2.29 | 8 | 3.5 | 3 days | |
| Pasta Salad | $5.49 | $2.79 | 6 | 3.5 | 3 days | |

---

### PET (Pet Supplies)
*Dedicated aisle — high repeat purchase rates, loyal customer base.*

#### `prod_pet_basics` — "Pet Supplies Study"
- **Research cost**: 12 pts
- **Prerequisite**: none
- **Store size**: Superstore+

| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Dry Dog Food 15lb | $24.99 | $14.99 | 2 | 3.0 | |
| Dry Cat Food 10lb | $19.99 | $11.99 | 2 | 3.0 | |
| Dog Treats | $6.99 | $3.49 | 8 | 3.5 | |
| Cat Treats | $4.99 | $2.49 | 10 | 3.0 | |
| Cat Litter 20lb | $14.99 | $8.99 | 2 | 2.5 | |
| Dog Toy | $9.99 | $4.99 | 6 | 2.0 | |

#### `prod_pet_premium` — "Premium Pet Products Study"
- **Research cost**: 15 pts
- **Prerequisite**: `prod_pet_basics`

| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Wet Dog Food 12-Pack | $14.99 | $8.99 | 3 | 4.0 | |
| Wet Cat Food 12-Pack | $11.99 | $6.99 | 3 | 3.5 | |
| Flea Treatment | $19.99 | $12.99 | 3 | 1.0 | |
| Pet Shampoo | $8.99 | $4.49 | 6 | 2.5 | |
| Dog Leash | $12.99 | $6.99 | 4 | 1.5 | |

---

### BABY (Baby Supplies)
*High margins, consistent demand, essential for Supercenter feel.*

#### `prod_baby_basics` — "Baby Products Study"
- **Research cost**: 15 pts
- **Prerequisite**: `prod_household`
- **Store size**: Superstore+

| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Diapers 36-Pack | $24.99 | $14.99 | 2 | 3.5 | |
| Baby Wipes 72-Pack | $4.99 | $2.49 | 6 | 4.0 | |
| Baby Formula | $34.99 | $24.99 | 2 | 2.0 | highest unit price |
| Baby Food Jars 4-Pack | $4.99 | $2.79 | 8 | 3.5 | |
| Baby Shampoo | $5.99 | $2.99 | 8 | 3.0 | |
| Baby Lotion | $5.99 | $2.99 | 8 | 3.0 | |

#### `prod_baby_gear` — "Baby Gear Study"
- **Research cost**: 18 pts
- **Prerequisite**: `prod_baby_basics`

| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Pacifiers 2-Pack | $6.99 | $3.49 | 8 | 1.5 | |
| Sippy Cup | $7.99 | $3.99 | 6 | 1.5 | |
| Baby Bottles 3-Pack | $12.99 | $6.99 | 4 | 2.0 | |
| Teething Ring | $5.99 | $2.99 | 8 | 1.0 | |
| Baby Bib 3-Pack | $8.99 | $4.49 | 6 | 1.5 | |

---

### OFFICE (Office & School Supplies)
*Low margin, steady demand, seasonal back-to-school spikes.*

#### `prod_office_basics` — "Office Supplies Study"
- **Research cost**: 10 pts
- **Prerequisite**: none
- **Store size**: Supercenter

| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Notebook 3-Pack | $6.99 | $3.49 | 8 | 3.5 | |
| Pen 10-Pack | $4.99 | $2.49 | 10 | 2.5 | |
| Printer Paper Ream | $8.99 | $5.49 | 4 | 3.5 | |
| Tape Dispenser | $3.99 | $1.99 | 12 | 2.0 | |
| Scissors | $5.99 | $2.99 | 8 | 1.5 | |

#### `prod_school_supplies` — "School Supplies Study"
- **Research cost**: 10 pts
- **Prerequisite**: `prod_office_basics`

| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Backpack | $24.99 | $12.99 | 3 | 1.5 | |
| Folder 5-Pack | $4.99 | $2.49 | 10 | 2.5 | |
| Markers 10-Pack | $6.99 | $3.49 | 8 | 2.0 | |
| Glue Sticks 3-Pack | $2.99 | $1.29 | 18 | 2.5 | |
| Pencils 24-Pack | $3.99 | $1.99 | 12 | 3.0 | |

---

### TOYS (Toys & Games)
*High seasonal demand. Big-box store feel at Supercenter size.*

#### `prod_toys_basics` — "Toys & Games Study"
- **Research cost**: 12 pts
- **Prerequisite**: none
- **Store size**: Supercenter

| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Building Blocks Set | $29.99 | $17.99 | 2 | 1.5 | |
| Board Game | $19.99 | $11.99 | 3 | 1.5 | |
| Stuffed Animal | $14.99 | $7.99 | 4 | 1.5 | |
| Action Figure | $9.99 | $4.99 | 6 | 1.5 | |
| Coloring Book & Crayons | $6.99 | $3.49 | 8 | 2.5 | |

#### `prod_toys_premium` — "Premium Toys Study"
- **Research cost**: 18 pts
- **Prerequisite**: `prod_toys_basics`

| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Remote Control Car | $39.99 | $23.99 | 2 | 1.0 | |
| Puzzle 1000-Piece | $14.99 | $7.99 | 4 | 2.0 | |
| Card Game | $12.99 | $6.99 | 6 | 1.5 | |
| Science Kit | $24.99 | $14.99 | 2 | 1.0 | |
| Dollhouse Set | $34.99 | $19.99 | 2 | 0.8 | |

---

### AUTO (Automotive)
*High unit prices. Superstore/Supercenter appropriate.*

#### `prod_auto_basics` — "Automotive Study"
- **Research cost**: 12 pts
- **Prerequisite**: none
- **Store size**: Supercenter

| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Motor Oil 5-Qt | $29.99 | $18.99 | 2 | 3.0 | |
| Windshield Washer Fluid | $4.99 | $2.49 | 6 | 4.0 | |
| Air Freshener 3-Pack | $5.99 | $2.99 | 10 | 2.0 | |
| Jumper Cables | $24.99 | $14.99 | 2 | 1.5 | |
| Tire Pressure Gauge | $9.99 | $4.99 | 6 | 1.0 | |

#### `prod_auto_care` — "Auto Care Study"
- **Research cost**: 10 pts
- **Prerequisite**: `prod_auto_basics`

| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Car Wash Kit | $14.99 | $7.99 | 4 | 2.0 | |
| Microfiber Cloths 6-Pack | $8.99 | $4.49 | 6 | 1.5 | |
| Ice Scraper | $6.99 | $3.49 | 8 | 1.5 | |
| Emergency Roadside Kit | $29.99 | $17.99 | 2 | 1.5 | |

---

### GARDEN (Garden & Outdoor)
*Supercenter ideal. Seasonal demand peaks in spring/summer.*

#### `prod_garden_basics` — "Garden Center Study"
- **Research cost**: 10 pts
- **Prerequisite**: none
- **Store size**: Supercenter

| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Potting Soil 20lb | $9.99 | $5.49 | 3 | 3.5 | |
| Garden Gloves | $6.99 | $3.49 | 8 | 2.0 | |
| Plant Fertilizer | $8.99 | $4.99 | 4 | 2.5 | |
| Garden Hose 50ft | $24.99 | $14.99 | 2 | 2.0 | |
| Flower Seeds Variety | $3.99 | $1.99 | 12 | 1.5 | |

#### `prod_garden_tools` — "Garden Tools Study"
- **Research cost**: 12 pts
- **Prerequisite**: `prod_garden_basics`

| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Pruning Shears | $14.99 | $7.99 | 4 | 1.5 | |
| Watering Can | $9.99 | $4.99 | 4 | 2.0 | |
| Trowel Set | $12.99 | $6.99 | 4 | 1.5 | |
| Bird Seed 10lb | $12.99 | $7.49 | 3 | 3.0 | |
| Citronella Candle | $8.99 | $4.49 | 6 | 2.0 | |

---

### SEASONAL (Seasonal Items)
*Rotating merchandise. Integrates with planned weather/seasonal events.*

#### `prod_seasonal_summer` — "Summer Merchandise Study"
- **Research cost**: 15 pts
- **Prerequisite**: `prod_household`
- **Store size**: Supercenter

| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Charcoal 8lb | $9.99 | $5.49 | 4 | 3.5 | |
| Paper Plates 50-Pack | $4.99 | $2.49 | 6 | 3.0 | |
| Plastic Cups 50-Pack | $3.99 | $1.99 | 8 | 3.0 | |
| Sunglasses | $14.99 | $6.99 | 6 | 1.0 | |
| Reusable Water Bottle | $12.99 | $6.49 | 4 | 1.5 | |

#### `prod_seasonal_outdoor` — "Outdoor Living Study"
- **Research cost**: 18 pts
- **Prerequisite**: `prod_seasonal_summer`

| Name | Price | Unit Cost | Case Pack | Wt | Notes |
|------|-------|-----------|-----------|-----|-------|
| Beach Towel | $19.99 | $9.99 | 3 | 2.0 | |
| Cooler 24-Can | $29.99 | $17.99 | 2 | 1.5 | |
| Camping Lantern | $24.99 | $14.99 | 2 | 1.0 | |
| Portable Fan | $19.99 | $10.99 | 3 | 1.5 | |
| Picnic Blanket | $14.99 | $7.99 | 4 | 1.5 | |

---

## Summary

### Totals

| | Current | Added | New Total |
|--|---------|-------|-----------|
| Items | 136 | ~218 | ~354 |
| Categories | 12 | 8 | 20 |
| Product-line research gates | 24 | 31 | 55 |

### New Categories Summary

| Category | Items | Gates | Store Size Required |
|----------|-------|-------|---------------------|
| DELI | 10 | 2 | Superstore+ |
| PET | 11 | 2 | Superstore+ |
| BABY | 11 | 2 | Superstore+ |
| OFFICE | 10 | 2 | Supercenter |
| TOYS | 10 | 2 | Supercenter |
| AUTO | 9 | 2 | Supercenter |
| GARDEN | 10 | 2 | Supercenter |
| SEASONAL | 10 | 2 | Supercenter |

### All New Research Gates (31)

Gating: store size + prerequisite research + research cost. No revenue gates.

| Gate ID | Display Name | Cost | Prereq | Store Size |
|---------|-------------|------|--------|------------|
| `prod_international` | International Foods Study | 12 | `prod_condiments` | — |
| `prod_spices` | Spice Rack Study | 8 | `prod_baking` | — |
| `prod_pasta_sauces` | Pasta & Sauce Study | 6 | `prod_canned` | — |
| `prod_plant_based` | Plant-Based Foods Study | 12 | `prod_dairy_alternatives` | — |
| `prod_snack_bars` | Snack Bar Study | 8 | `prod_premium_snacks` | — |
| `prod_snack_dips` | Dips & Spreads Study | 6 | `prod_snack_variety` | — |
| `prod_juice_variety` | Juice Variety Study | 8 | `prod_beverages` | — |
| `prod_coffee_tea` | Coffee & Tea Selection Study | 10 | `prod_specialty_drinks` | — |
| `prod_dairy_alternatives` | Dairy Alternatives Study | 10 | `prod_dairy_cheese` | — |
| `prod_frozen_desserts` | Frozen Desserts Study | 10 | `prod_frozen_meals` | — |
| `prod_frozen_international` | International Frozen Foods Study | 12 | `prod_frozen_meals` | — |
| `prod_bakery_cakes` | Cake & Pastry Study | 12 | `prod_artisan_bread` | — |
| `prod_premium_bakery` | Premium Bakery Study | 15 | `prod_bakery_cakes` | — |
| `prod_herbs` | Fresh Herbs Study | 8 | `prod_premium_produce` | — |
| `prod_organic_produce` | Organic Produce Study | 15 | `prod_herbs` | — |
| `prod_deli_meats` | Deli Meats Study | 12 | `prod_meat_counter` | — |
| `prod_seafood_market` | Seafood Market Study | 22 | `prod_premium_meat` | — |
| `prod_personal_care` | Personal Care Study | 10 | `prod_health_beauty` | — |
| `prod_cleaning_supplies` | Cleaning Supplies Study | 10 | `prod_household` | — |
| `prod_home_essentials` | Home Essentials Study | 12 | `prod_cleaning_supplies` | — |
| `prod_wellness` | Wellness Products Study | 12 | `prod_pharmacy` | — |
| `prod_tech_accessories` | Tech Accessories Study | 15 | `prod_electronics` | — |
| `prod_deli_counter` | Deli Counter Study | 15 | `prod_dairy_basics` | Superstore+ |
| `prod_deli_prepared` | Prepared Foods Study | 18 | `prod_deli_counter` | Superstore+ |
| `prod_pet_basics` | Pet Supplies Study | 12 | — | Superstore+ |
| `prod_pet_premium` | Premium Pet Products Study | 15 | `prod_pet_basics` | Superstore+ |
| `prod_baby_basics` | Baby Products Study | 15 | `prod_household` | Superstore+ |
| `prod_baby_gear` | Baby Gear Study | 18 | `prod_baby_basics` | Superstore+ |
| `prod_office_basics` | Office Supplies Study | 10 | — | Supercenter |
| `prod_school_supplies` | School Supplies Study | 10 | `prod_office_basics` | Supercenter |
| `prod_toys_basics` | Toys & Games Study | 12 | — | Supercenter |
| `prod_toys_premium` | Premium Toys Study | 18 | `prod_toys_basics` | Supercenter |
| `prod_auto_basics` | Automotive Study | 12 | — | Supercenter |
| `prod_auto_care` | Auto Care Study | 10 | `prod_auto_basics` | Supercenter |
| `prod_garden_basics` | Garden Center Study | 10 | — | Supercenter |
| `prod_garden_tools` | Garden Tools Study | 12 | `prod_garden_basics` | Supercenter |
| `prod_seasonal_summer` | Summer Merchandise Study | 15 | `prod_household` | Supercenter |
| `prod_seasonal_outdoor` | Outdoor Living Study | 18 | `prod_seasonal_summer` | Supercenter |

### Research Point Economy

Total research points needed for all 55 product-line gates: **~627 pts** (up from ~302)

At early-game rate (~10 pts/day per analyst), unlocking everything takes many in-game weeks — provides long-term progression motivation.

### Progression Pacing

| Phase | Store Size | Gates Available | Typical Research Cost |
|-------|-----------|-----------------|----------------------|
| Early | Mom & Pop | Starter gates (no prereqs, no store size gate) | 5-8 pts (~1 day) |
| Mid | Small Grocery / Grocery | Dairy chain, frozen, produce, international | 8-12 pts (~1-2 days) |
| Late-Mid | Superstore | Premium chains + new categories (Pet, Baby, Deli) | 12-18 pts (~2-3 days) |
| Late | Supercenter | Office, Toys, Auto, Garden, Seasonal, premium tiers of all categories | 18-25 pts (~3-5 days) |

---

## Part 4: Item Affinity Groups

### Design

Items belong to one or more **affinity groups** — tags representing "goes well with" relationships. When a customer picks an item, other items sharing any affinity group get a weight boost for subsequent basket picks.

**Data model change**: Add `affinityGroups: List<String>` to `ItemMetadata` and `items.json`.

**Algorithm change in `TransactionEngine.weightedSample()`**: After selecting each item, boost weights of remaining items that share an affinity group with anything already in the basket.

```kotlin
// Pseudocode for modified weightedSample
for (i in 1..count) {
    val weights = remaining.map { id ->
        val base = cache.get(id)?.purchaseWeight ?: 1.0f
        val affinityBoost = if (selected.any { sel -> 
            cache.sharesAffinityGroup(sel, id) 
        }) AFFINITY_BOOST else 1.0f
        id to (base * zoneMultiplier * priceMultiplier * affinityBoost)
    }
    selected += selectByWeight(weights)
}
```

**Tuning constant**: `AFFINITY_BOOST = 2.0f` (items in same group are 2x more likely to be picked after a related item is selected). Tunable per group if needed.

Items with no affinity groups behave exactly as today (no boost). Items can be in multiple groups (e.g., Eggs is in both `breakfast` and `baking`).

### Affinity Groups

#### Meal-Based Groups

**`breakfast`** — Morning meal items
- Cereal, Oatmeal, Pancake Mix, Granola, Instant Grits, Toaster Pastries
- Milk, Eggs, Salted Butter, Bacon, Italian Sausage
- Orange Juice, Ground Coffee
- Frozen Waffles, Breakfast Sandwiches

**`sandwich`** — Sandwich fixings
- Bread, Deli Turkey, Deli Ham, Deli Roast Beef, Deli Salami
- Mayonnaise, Yellow Mustard, Lettuce, Tomato
- Cheddar Cheese, Swiss Cheese, Cream Cheese

**`burger_bbq`** — Grilling and burgers
- Ground Beef, Ground Turkey, Hot Dogs, Bratwurst
- Ketchup, Yellow Mustard, Mayonnaise
- Cheddar Cheese, Yellow Onion, Lettuce, Tomato
- Charcoal, Paper Plates, Plastic Cups
- Potato Salad, Potato Chips, Soda Can 12-Pack

**`taco_night`** — Mexican-inspired meal
- Flour Tortillas, Taco Seasoning, Salsa, Salsa Verde
- Ground Beef, Sour Cream, Cheddar Cheese
- Hot Sauce, Sriracha Sauce

**`pasta_dinner`** — Pasta meal
- Spaghetti Pasta, Whole Wheat Pasta, Imported Penne Rigate
- Marinara Sauce, Alfredo Sauce
- Aged Parmesan Wedge, Mozzarella
- Fresh Basil, Fresh Garlic, Olive Oil

**`soup_comfort`** — Soup and sides
- Chicken Noodle Soup, Chicken Broth
- Crackers, Bread, Sourdough Loaf
- Canned Tomatoes, Tomato Sauce

**`pbj`** — PB&J combo
- Peanut Butter, Organic Peanut Butter, Almond Butter
- Strawberry Jam
- Bread

**`baking`** — Baking ingredients
- All-Purpose Flour, White Sugar, Eggs, Salted Butter
- Baking Soda, Vanilla Extract, Cornstarch
- Chocolate Bar (baking chocolate)

**`asian_cooking`** — Asian cuisine ingredients
- Soy Sauce, Sriracha Sauce, Rice Noodles, Curry Paste
- Coconut Milk, White Rice, Brown Rice
- Tofu, Tempeh, Seaweed Snack
- Frozen Dumplings, Frozen Spring Rolls, Frozen Egg Rolls

**`salad_bowl`** — Salad ingredients
- Green Lettuce, Baby Spinach, Organic Kale
- Ripe Tomato, Cucumbers, Fresh Carrots, Bell Peppers
- Avocado, Mushrooms, Yellow Onion
- Olive Oil, Balsamic Vinegar

#### Snack & Drink Groups

**`snack_party`** — Party snacking
- Tortilla Chips, Potato Chips, Pretzels, Cheese Puffs
- Salsa Dip, Guacamole, Hummus, French Onion Dip
- Soda Can 12-Pack, Plastic Cups

**`movie_snacks`** — Movie night treats
- Microwave Popcorn, Chocolate Bar, Gummy Bears, Sour Gummy Worms
- Peanut Butter Cups, Hard Candy Bag
- Coca-Cola 2L, Soda Can 12-Pack
- Vanilla Ice Cream, Ice Cream Sandwiches

**`trail_energy`** — On-the-go snacking
- Trail Mix, Mixed Nuts, Granola Bar, Organic Granola Bars
- Protein Bar, Beef Jerky, Dried Mango Slices, Dark Chocolate Almonds
- Energy Drink, Sports Drink 8-Pack, Water 24-Pack

**`coffee_ritual`** — Coffee preparation
- Ground Coffee, Whole Bean Coffee, Cold Brew Coffee, Espresso Pods 12-Pack
- Heavy Cream, Oat Milk, Almond Milk, White Sugar
- Chai Concentrate

**`tea_time`** — Tea lovers
- Herbal Tea, Green Tea 20-Pack, Chai Concentrate
- Honey, Lemons, Limes

#### Fresh & Frozen Groups

**`fruit_basket`** — Fresh fruit shopping
- Fuji Apples, Bananas, Strawberries, Blueberries, Red Grapes
- Organic Apples, Lemons, Limes

**`frozen_quick_meal`** — Quick frozen dinners
- Frozen Pizza, Frozen Burritos, Frozen Mac & Cheese
- Chicken Nuggets, Frozen Fish Sticks, Frozen Pot Pie, Frozen Egg Rolls
- Frozen Fries

**`ice_cream_run`** — Frozen desserts
- Vanilla Ice Cream, Mango Sorbet, Gelato Pint
- Ice Cream Sandwiches, Frozen Fruit Bars, Popsicles
- Frozen Cheesecake

**`deli_lunch`** — Deli counter meal
- Rotisserie Chicken, Sandwich Wrap, Chicken Wings 8-Pack
- Potato Salad, Macaroni Salad, Pasta Salad
- Sushi 8-Piece, Soup Quart, Fruit Cup

#### Non-Food Groups

**`cleaning_day`** — Cleaning supplies
- All-Purpose Cleaner, Glass Cleaner, Bleach, Floor Cleaner
- Disinfectant Wipes, Sponges 3-Pack
- Paper Towels, Trash Bags

**`laundry_bath`** — Laundry and bath
- Laundry Detergent, Dish Soap, Hand Soap
- Shampoo, Conditioner, Body Wash

**`personal_hygiene`** — Daily personal care
- Toothpaste, Mouthwash, Deodorant
- Face Wash, Body Lotion, Lip Balm, Hand Sanitizer, Razor Pack

**`baby_run`** — Baby supply trip
- Diapers 36-Pack, Baby Wipes 72-Pack, Baby Formula
- Baby Food Jars 4-Pack, Baby Shampoo, Baby Lotion
- Pacifiers 2-Pack, Sippy Cup, Baby Bottles 3-Pack

**`pet_run`** — Pet owner trip
- Dry Dog Food 15lb, Dog Treats, Dog Toy, Dog Leash
- Dry Cat Food 10lb, Cat Treats, Cat Litter 20lb
- Wet Dog Food 12-Pack, Wet Cat Food 12-Pack, Pet Shampoo

**`school_prep`** — Back to school
- Backpack, Notebook 3-Pack, Pen 10-Pack, Pencils 24-Pack
- Folder 5-Pack, Markers 10-Pack, Glue Sticks 3-Pack, Scissors

**`tech_setup`** — Electronics shopping
- Headphones, Wireless Earbuds, Bluetooth Speaker
- USB Cable, HDMI Cable, Phone Case
- Portable Charger, Wireless Mouse, USB Flash Drive

**`garden_day`** — Garden project
- Potting Soil 20lb, Plant Fertilizer, Flower Seeds Variety
- Garden Gloves, Garden Hose 50ft, Pruning Shears
- Watering Can, Trowel Set

**`cookout_party`** — Outdoor gathering (overlaps with burger_bbq)
- Charcoal, Paper Plates, Plastic Cups, Sunglasses
- Hot Dogs, Bratwurst, Potato Salad
- Soda Can 12-Pack, Water 24-Pack, Lemonade
- Cooler 24-Can, Beach Towel, Picnic Blanket

**`cold_flu`** — Sick day supplies
- Cold Medicine, Aspirin, Ibuprofen
- Chicken Noodle Soup, Chicken Broth, Crackers
- Orange Juice, Herbal Tea, Honey
- Zinc Lozenges

#### Special Single-Purpose Groups

**`dairy_staples`** — Core dairy items bought together
- Milk, Eggs, Salted Butter, Cheddar Cheese

**`condiments`** — Condiment aisle
- Ketchup, Yellow Mustard, Mayonnaise, Hot Sauce
- Soy Sauce, Sriracha Sauce, Dijon Mustard

**`canned_pantry`** — Pantry stocking
- Canned Black Beans, Canned Pinto Beans, Canned Tomatoes
- Canned Sweet Corn, Canned Green Beans, Canned Tuna
- Tomato Sauce, Chicken Broth

**`auto_care`** — Car maintenance
- Motor Oil 5-Qt, Windshield Washer Fluid
- Air Freshener 3-Pack, Car Wash Kit, Microfiber Cloths 6-Pack

### Items With No Affinity Group

Some items are "island" purchases with no strong pairing. These keep their existing `purchaseWeight` behavior with no boost. Examples:
- Ramen Noodles (impulse/staple, no strong pairing)
- Olive Oil (cooking but no specific meal group — could optionally join `salad_bowl` or `pasta_dinner`)
- Quinoa, Kombucha (health-conscious but niche)

When finalizing, each item should have 0-3 groups. Most items will have 1-2 groups. Starter items (Bread, Rice, etc.) often appear in multiple groups, which naturally keeps them popular as the item pool grows — good for gameplay.

### Implementation Notes

**Files to modify:**
- `items.json` — add `"affinityGroups": ["breakfast", "dairy_staples"]` per item
- `Item.kt` — add `affinityGroups: String` (comma-separated, stored as single column)
- `ItemMetadata.kt` — add `affinityGroups: List<String>`
- `ItemMetadataCache.kt` — add `sharesAffinityGroup(id1, id2): Boolean` and precomputed group→items index
- `TransactionEngine.kt` — modify `weightedSample()` lines 343-371

**No UI changes needed** — affinity is invisible to the player, it just makes baskets feel more realistic.

---

## Verification

After implementing these items:
1. Add entries to `items.json` following existing format (prices in cents)
2. Add new `ItemCategory` enum values for 8 new categories
3. Register all new `ResearchableUpgrade` entries in the research upgrade registry
4. Update `requiredTierForSection()` for new categories (DELI→TIER_3, others→TIER_GM for migration compat)
5. Test that all research prerequisite chains are acyclic
6. Verify each gate has 4-6 items for satisfying unlock moments
7. Playtest pacing: can a player reasonably discover most gates within a few hours of real play?
