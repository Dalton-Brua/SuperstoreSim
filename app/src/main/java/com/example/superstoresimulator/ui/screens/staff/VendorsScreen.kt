package com.example.superstoresimulator.ui.screens.staff

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.ui.components.common.ScreenHeader
import com.example.superstoresimulator.ui.state.VendorCardUI
import com.example.superstoresimulator.ui.state.VendorUIState
import com.example.superstoresimulator.ui.theme.CardWhite
import com.example.superstoresimulator.ui.theme.ChipSurface
import com.example.superstoresimulator.ui.theme.LightBackground
import com.example.superstoresimulator.ui.theme.Primary
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.ProgressBarTrack
import com.example.superstoresimulator.ui.theme.SubtleText
import com.example.superstoresimulator.ui.theme.Success
import com.example.superstoresimulator.ui.theme.SuccessChipSurface
import com.example.superstoresimulator.ui.theme.SuccessSurface
import com.example.superstoresimulator.ui.theme.SuccessTextDark
import com.example.superstoresimulator.ui.theme.SuccessTextDarker
import com.example.superstoresimulator.ui.theme.TextMuted
import com.example.superstoresimulator.ui.theme.TextSecondary
import com.example.superstoresimulator.ui.theme.TextWhite

@Composable
fun VendorsScreen(
    vendorState: VendorUIState,
    money: Money,
    onUnlockNextTier: () -> Unit,
    onInvestInVendor: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(LightBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                title = "Vendors",
                subtitle = "Commission partners that stock your shelves",
            )
            Spacer(Modifier.height(8.dp))
        }

        // Vendor tier unlock card
        item {
            VendorTierCard(
                vendorState = vendorState,
                money = money,
                onUnlockNextTier = onUnlockNextTier,
            )
        }

        // Per-vendor cards
        items(vendorState.vendors) { vendor ->
            VendorCard(
                vendor = vendor,
                currentVendorTier = vendorState.currentVendorTier,
                money = money,
                onInvest = { onInvestInVendor(vendor.vendorId) },
            )
        }
    }
}

@Composable
private fun VendorTierCard(
    vendorState: VendorUIState,
    money: Money,
    onUnlockNextTier: () -> Unit,
) {
    if (vendorState.maxTierReached) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SuccessChipSurface),
            elevation = CardDefaults.cardElevation(4.dp),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Star, null, tint = Success, modifier = Modifier.size(24.dp))
                Column {
                    Text("All Vendor Tiers Unlocked!", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = SuccessTextDarker)
                    Text("Every vendor product is available.", fontSize = 13.sp, color = SuccessTextDark)
                }
            }
        }
    } else {
        val nextCost = vendorState.nextTierCost ?: return
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            elevation = CardDefaults.cardElevation(4.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Vendor Product Tier ${vendorState.currentVendorTier + 1}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = PrimaryDark,
                )
                Text(
                    text = "Unlock the next tier to access more vendor products across all partners.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                )
                Button(
                    onClick = onUnlockNextTier,
                    enabled = vendorState.canUnlockNextTier,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        contentColor = TextWhite,
                        disabledContainerColor = TextMuted,
                        disabledContentColor = TextWhite,
                    ),
                ) {
                    Text(
                        text = if (vendorState.canUnlockNextTier) "Unlock Tier ${vendorState.currentVendorTier + 2} for $nextCost"
                        else "Need $nextCost (you have $money)",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun VendorCard(
    vendor: VendorCardUI,
    currentVendorTier: Int,
    money: Money,
    onInvest: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(3.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = vendor.vendorName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = PrimaryDark,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(color = Primary, shape = MaterialTheme.shapes.extraSmall) {
                        Text(
                            text = vendor.tierName.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            HorizontalDivider(color = ProgressBarTrack)

            // Reputation bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Reputation", fontSize = 13.sp, color = SubtleText)
                Text(
                    "${vendor.reputation} / ${vendor.maxReputation}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryDark,
                )
            }
            LinearProgressIndicator(
                progress = { vendor.reputation.toFloat() / vendor.maxReputation },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = if (vendor.atMaxRep) Success else Primary,
                trackColor = ProgressBarTrack,
            )

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatChip("Commission", "${vendor.commissionPercent}%")
                StatChip("Restock", "${vendor.restockIntervalDays}d")
                StatChip("Products", "${vendor.items.size}")
            }

            // Invest button
            if (vendor.atMaxRep) {
                Surface(
                    color = SuccessSurface,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Star, null, tint = Success, modifier = Modifier.size(18.dp))
                        Text("Max reputation — lowest commission rate!", fontSize = 13.sp, color = SuccessTextDark)
                    }
                }
            } else {
                Button(
                    onClick = onInvest,
                    enabled = vendor.canInvest,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        contentColor = TextWhite,
                        disabledContainerColor = TextMuted,
                        disabledContentColor = TextWhite,
                    ),
                ) {
                    Icon(Icons.AutoMirrored.Filled.TrendingUp, null, modifier = Modifier.size(16.dp))
                    Text(
                        text = if (vendor.canInvest) "  Invest ${vendor.investCost} (+5 rep)"
                        else "  Need ${vendor.investCost}",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Expandable items section
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HorizontalDivider(color = ProgressBarTrack)
                    Text(
                        "Products Supplied",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = PrimaryDark,
                    )
                    if (vendor.items.isEmpty()) {
                        Text("No products configured.", fontSize = 12.sp, color = TextMuted)
                    } else {
                        vendor.items.forEach { item ->
                            val locked = item.vendorTier > currentVendorTier
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (locked) ChipSurface else LightBackground,
                                        RoundedCornerShape(6.dp),
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = item.name,
                                    fontSize = 13.sp,
                                    color = if (locked) TextMuted else PrimaryDark,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "${item.price}",
                                        fontSize = 12.sp,
                                        color = if (locked) TextMuted else TextSecondary,
                                    )
                                    if (locked) {
                                        Text(
                                            text = "T${item.vendorTier + 1}",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextWhite,
                                            modifier = Modifier
                                                .background(TextMuted, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 1.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PrimaryDark)
        Text(label, fontSize = 11.sp, color = TextSecondary)
    }
}
