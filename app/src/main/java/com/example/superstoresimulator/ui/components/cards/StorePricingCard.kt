package com.example.superstoresimulator.ui.components.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.ui.components.PriceSlider
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemUnlockTier
import com.example.superstoresimulator.ui.state.PricingUIState
import com.example.superstoresimulator.ui.theme.CardWhite
import com.example.superstoresimulator.ui.theme.CautionDark
import com.example.superstoresimulator.ui.theme.ChipSurface
import com.example.superstoresimulator.ui.theme.DestructiveDark
import com.example.superstoresimulator.ui.theme.Primary
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.Secondary
import com.example.superstoresimulator.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun StorePricingCard(
    pricingState: PricingUIState,
    currentTier: ItemUnlockTier,
    onSetDefaultMarkup: (Int) -> Unit,
    onSetCategoryMarkup: (ItemCategory, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    val reputationColor = when (pricingState.reputationLabel) {
        "Budget" -> Secondary
        "Premium" -> DestructiveDark
        else -> Primary
    }

    val trafficEffect = ((pricingState.trafficMultiplier - 1f) * 100).roundToInt()
    val basketEffect = ((pricingState.basketMultiplier - 1f) * 100).roundToInt()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocalOffer,
                    contentDescription = null,
                    tint = reputationColor,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Store Pricing",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = PrimaryDark
                    )
                    Text(
                        text = "${pricingState.reputationLabel} Store",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = reputationColor
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = TextSecondary,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatColumn("Price Index", "%.2f".format(pricingState.priceIndex))
                StatColumn(
                    "Traffic",
                    "${if (trafficEffect >= 0) "+" else ""}$trafficEffect%",
                    if (trafficEffect >= 0) Secondary else DestructiveDark
                )
                StatColumn(
                    "Basket",
                    "${if (basketEffect >= 0) "+" else ""}$basketEffect%",
                    if (basketEffect >= 0) Secondary else DestructiveDark
                )
                if (pricingState.itemsMarkedDown > 0) {
                    StatColumn("Markdowns", "${pricingState.itemsMarkedDown}", CautionDark)
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = ChipSurface, thickness = 1.dp)
                    Spacer(Modifier.height(12.dp))

                    PriceSlider(
                        label = "Default Markup",
                        currentPercent = pricingState.pricingState.defaultMarkup,
                        range = -50f..100f,
                        onSet = onSetDefaultMarkup,
                        labelColor = PrimaryDark,
                        secondaryColor = TextSecondary
                    )

                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Category Markups",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryDark
                    )
                    Spacer(Modifier.height(8.dp))

                    val unlockedCategories = currentTier.unlockedSections.sortedBy { it.ordinal }
                    for (category in unlockedCategories) {
                        PriceSlider(
                            label = category.name.lowercase().replaceFirstChar { it.uppercase() },
                            currentPercent = pricingState.pricingState.categoryMarkups[category] ?: 0,
                            range = -50f..100f,
                            onSet = { percent -> onSetCategoryMarkup(category, percent) },
                            modifier = Modifier.padding(vertical = 2.dp),
                            labelColor = PrimaryDark,
                            secondaryColor = TextSecondary,
                            sliderModifier = Modifier.fillMaxWidth().height(32.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatColumn(
    label: String,
    value: String,
    valueColor: Color = PrimaryDark
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = TextSecondary)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

