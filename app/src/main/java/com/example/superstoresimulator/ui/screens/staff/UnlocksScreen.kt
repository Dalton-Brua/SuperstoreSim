package com.example.superstoresimulator.ui.screens.staff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemUnlockTier
import com.example.superstoresimulator.ui.state.ProgressionUIState
import com.example.superstoresimulator.ui.components.common.ScreenHeader
import com.example.superstoresimulator.ui.theme.ActiveBlueSurface
import com.example.superstoresimulator.ui.theme.CardWhite
import com.example.superstoresimulator.ui.theme.InactiveGrey
import com.example.superstoresimulator.ui.theme.InfoChipSurface
import com.example.superstoresimulator.ui.theme.LightBackground
import com.example.superstoresimulator.ui.theme.PlaceholderSurface
import com.example.superstoresimulator.ui.theme.Primary
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.ProgressBarTrack
import com.example.superstoresimulator.ui.theme.SubtleText
import com.example.superstoresimulator.ui.theme.Success
import com.example.superstoresimulator.ui.theme.SuccessChipSurface
import com.example.superstoresimulator.ui.theme.SuccessSurface
import com.example.superstoresimulator.ui.theme.SuccessTextDark
import com.example.superstoresimulator.ui.theme.SuccessTextDarker
import com.example.superstoresimulator.ui.theme.SurfaceSubtle
import com.example.superstoresimulator.ui.theme.TextMuted
import com.example.superstoresimulator.ui.theme.TextSecondary
import com.example.superstoresimulator.ui.theme.TextWhite

@Composable
fun UnlocksScreen(
    progression: ProgressionUIState,
    money: Money,
    onUnlockNextTier: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ordered = ItemUnlockTier.ordered

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(LightBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(
                title = "Store Progression",
                subtitle = "Total revenue: ${progression.totalRevenue}"
            )
            Spacer(Modifier.height(8.dp))
        }

        // Progress card — next tier gate or max tier reached
        item {
            if (progression.nextTier != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val gateLabel = if (progression.availableTier != null)
                            "Revenue gate met — ready to unlock ${progression.availableTier.displayName}!"
                        else
                            "Progress to ${progression.nextTier.displayName}"
                        Text(text = gateLabel, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        LinearProgressIndicator(
                            progress = { progression.tierProgressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = if (progression.availableTier != null) Success else Primary,
                            trackColor = InfoChipSurface
                        )
                        if (progression.availableTier == null) {
                            Text(
                                text = "${progression.revenueToNextTier} more revenue needed  " +
                                        "(${(progression.tierProgressFraction * 100).toInt()}%)",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        } else {
                            Text(
                                text = "Unlock cost: ${progression.availableTier.unlockCost}  •  " +
                                        "Your cash: $money",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SuccessChipSurface),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Star, null, tint = Success, modifier = Modifier.size(24.dp))
                        Column {
                            Text("Full Store Unlocked!", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = SuccessTextDarker)
                            Text("All sections are now available.", fontSize = 13.sp, color = SuccessTextDark)
                        }
                    }
                }
            }
        }

        items(ordered) { tier ->
            TierCard(
                tier = tier,
                currentTier = progression.currentTier,
                availableTier = progression.availableTier,
                money = money,
                ordered = ordered,
                onUnlockNextTier = onUnlockNextTier,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TierCard(
    tier: ItemUnlockTier,
    currentTier: ItemUnlockTier,
    availableTier: ItemUnlockTier?,
    money: Money,
    ordered: List<ItemUnlockTier>,
    onUnlockNextTier: () -> Unit,
) {
    val isUnlocked = tier.unlockAmount <= currentTier.unlockAmount
    val isCurrent = tier == currentTier
    val isAvailable = tier == availableTier   // revenue gate met, not yet paid

    val prevTier = ordered.getOrNull(ordered.indexOf(tier) - 1)
    val newCategories: Set<ItemCategory> =
        if (prevTier == null) tier.unlockedSections
        else tier.unlockedSections - prevTier.unlockedSections

    val containerColor = when {
        isAvailable -> SuccessSurface     // soft green — ready to buy
        isCurrent   -> ActiveBlueSurface  // blue — active tier
        isUnlocked  -> CardWhite
        else        -> SurfaceSubtle
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(
            if (isAvailable || isCurrent) 6.dp else if (isUnlocked) 3.dp else 1.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isUnlocked) Icons.Default.CheckCircle else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (isUnlocked) Success else TextMuted,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = tier.displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = if (isUnlocked || isAvailable) PrimaryDark else TextMuted
                    )
                    when {
                        isCurrent -> Badge("CURRENT", Primary)
                        isAvailable -> Badge("AVAILABLE", Success)
                    }
                }
                Text(
                    text = if (tier.unlockAmount == 0L) "Free"
                           else Money(tier.unlockAmount).toString(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isUnlocked || isAvailable) TextSecondary else InactiveGrey
                )
            }

            // ── Narrative ─────────────────────────────────────────────────────
            HorizontalDivider(color = ProgressBarTrack)
            Text(
                text = tier.narrativeTitle,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isUnlocked || isAvailable) PrimaryDark else TextMuted
            )
            Text(
                text = tier.narrativeDescription,
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
                color = if (isUnlocked || isAvailable) SubtleText else InactiveGrey
            )

            // ── New sections ──────────────────────────────────────────────────
            if (newCategories.isNotEmpty()) {
                Text(
                    text = if (prevTier == null) "Starting sections:" else "Newly unlocked sections:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isUnlocked || isAvailable) SubtleText else InactiveGrey
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    newCategories.sortedBy { it.displayName }.forEach { category ->
                        Surface(
                            color = if (isUnlocked || isAvailable) InfoChipSurface else PlaceholderSurface,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = category.displayName,
                                fontSize = 12.sp,
                                color = if (isUnlocked || isAvailable) PrimaryDark else InactiveGrey,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // ── Unlock button — only on the available tier ────────────────────
            if (isAvailable) {
                val canAfford = money >= tier.unlockCost
                Button(
                    onClick = onUnlockNextTier,
                    enabled = canAfford,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Success,
                        disabledContainerColor = TextMuted
                    )
                ) {
                    Text(
                        text = if (canAfford) "Unlock for ${tier.unlockCost}"
                               else "Need ${tier.unlockCost} cash (you have $money)",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun Badge(label: String, color: Color) {
    Surface(color = color, shape = MaterialTheme.shapes.extraSmall) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = TextWhite,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
