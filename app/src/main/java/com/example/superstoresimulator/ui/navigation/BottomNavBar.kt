package com.example.superstoresimulator.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.Screen
import com.example.superstoresimulator.ui.theme.Primary
import com.example.superstoresimulator.ui.theme.NavBarBackground
import com.example.superstoresimulator.ui.theme.NavBarIndicator
import com.example.superstoresimulator.ui.theme.NavBarSelectedIcon
import com.example.superstoresimulator.ui.theme.NavBarSelectedText
import com.example.superstoresimulator.ui.theme.NavBarUnselectedIcon
import com.example.superstoresimulator.ui.theme.NavBarUnselectedText

private data class NavItem(
    val screen: Screen,
    val icon: ImageVector,
    val contentDescription: String,
    val label: String,
)

private val navItems = listOf(
    NavItem(Screen.GAME, Icons.Default.AddShoppingCart, "Transactions", "Store"),
    NavItem(Screen.INVENTORY, Icons.Default.Inbox, "Inventory", "Inventory"),
    NavItem(Screen.STAFF, Icons.Default.People, "Manage", "Manage"),
    NavItem(Screen.HISTORY, Icons.Default.History, "History", "History"),
    NavItem(Screen.METRICS, Icons.Default.BarChart, "Metrics", "Metrics"),
)

@Composable
fun BottomNavBar(
    current: Screen,
    onSelect: (Screen) -> Unit,
    hintScreen: Screen? = null,
) {
    NavigationBar(containerColor = NavBarBackground) {
        navItems.forEach { item ->
            // Highlight the tab the active tutorial step points to (but not the one already showing).
            val showsTutorialHint = hintScreen == item.screen && current != item.screen
            NavigationBarItem(
                selected = current == item.screen,
                onClick = { onSelect(item.screen) },
                icon = {
                    if (showsTutorialHint) {
                        Box {
                            Icon(item.icon, contentDescription = item.contentDescription)
                            TutorialHintDot()
                        }
                    } else {
                        Icon(item.icon, contentDescription = item.contentDescription)
                    }
                },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = NavBarSelectedIcon,
                    selectedTextColor = NavBarSelectedText,
                    indicatorColor = NavBarIndicator,
                    unselectedIconColor = NavBarUnselectedIcon,
                    unselectedTextColor = NavBarUnselectedText
                )
            )
        }
    }
}

@Composable
private fun BoxScope.TutorialHintDot() {
    Box(
        modifier = Modifier
            .size(10.dp)
            .align(Alignment.TopEnd)
            .offset(x = 6.dp, y = (-4).dp)
            .background(color = Primary, shape = CircleShape)
    )
}

