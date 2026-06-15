package com.example.superstoresimulator.domain.tutorial

import kotlinx.serialization.Serializable

@Serializable
enum class TutorialStep(
    val displayTitle: String,
    val instruction: String,
    val hintText: String,
) {
    WELCOME(
        "Welcome to Your Store!",
        "Time is paused. Tap the PAUSED button at the top of the Store screen to start the clock and begin.",
        "Tap PAUSED in the top bar to unpause."
    ),
    OPEN_STORE(
        "Open for Business",
        "Your shelves are already stocked! Once the clock reaches opening hours, customers will start coming in.",
        "Let time run until the store opens."
    ),
    FIRST_SALE(
        "Your First Sale",
        "Customers are browsing. Set your role to Cashier to ring them up yourself.",
        "Switch to the Cashier role on the Store screen."
    ),
    COMPLETE_TRANSACTION(
        "Ring Up a Customer",
        "A customer is ready to check out! Complete the transaction to earn your first revenue.",
        "Process a transaction at the register. Don't forget to switch to the Cashier role!"
    ),
    STOCK_SHELVES(
        "Restock the Shelves",
        "Selling drains your shelves. Set your role to Stocker to refill them from the backroom.",
        "Switch to the Stocker role to restock shelves."
    ),
    ORDER_FIRST_ITEM(
        "Order More Inventory",
        "Backroom stock runs out too. Go to the Inventory tab and order a case pack of any item.",
        "Tap 'Order' on any item to buy a case pack."
    ),
    WAIT_FOR_DELIVERY(
        "Wait for Delivery",
        "Your order is on the next truck. Keep running your store until it arrives!",
        "Keep playing until your truck arrives. Don't forget to keep ordering!"
    ),
    HIRE_STAFF(
        "Hire Help",
        "You can't do everything yourself. Go to the Staff tab and hire a Cashier or Stocker.",
        "Tap 'Hire' on the Staff tab."
    ),
    HIRE_ANALYST(
        "Hire a Market Analyst",
        "Market Analysts study your store's activity and discover new upgrades. Hire one to unlock new possibilities!",
        "Hire a Market Analyst from the Staff tab."
    ),
    RESEARCH_FIRST_UNLOCK(
        "Research an Upgrade",
        "Open the Manage → Unlocks tab and assign your analyst to a study. Keep running your store until it finishes to unlock your first upgrade.",
        "Assign your analyst to a study in the Unlocks tab."
    ),
    TUTORIAL_COMPLETE(
        "You're on Your Own!",
        "Make sure to keep assigning your analyst to new research, there are many upgrades to discover!",
        ""
    ),
}
