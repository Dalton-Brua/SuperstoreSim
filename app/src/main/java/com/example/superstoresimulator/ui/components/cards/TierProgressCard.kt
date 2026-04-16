package com.example.superstoresimulator.ui.components.cards

import android.R.attr.onClick
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.items.ItemUnlockTier
import com.example.superstoresimulator.ui.theme.CardBlue
import com.example.superstoresimulator.ui.theme.IconBlue
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.TextDark
import com.example.superstoresimulator.ui.theme.TextSecondary

/**
 * Card showing the current tier progression, revenue progress, and unlock button.
 * Styled similarly to StoreSizeCard for visual consistency.
 */
@Composable
fun TierProgressCard(
    currentTier: ItemUnlockTier,
    totalRevenue: Money,
    nextTier: ItemUnlockTier?,
    revenueToNextTier: Money?,
    tierProgressFraction: Float,
    availableTier: ItemUnlockTier?,
    playerMoney: Money,
    onUnlockNextTier: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canUnlock = availableTier != null && 
                    availableTier.unlockCost != null && 
                    playerMoney >= availableTier.unlockCost
    
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (nextTier == null) Icons.Default.CheckCircle else Icons.Default.Star,
                    contentDescription = null,
                    tint = if (nextTier == null) Color(0xFF16A34A) else IconBlue,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentTier.displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = PrimaryDark
                    )
                    Text(
                        text = if (nextTier == null) "All sections unlocked" else "Revenue: $totalRevenue",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
            
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = CardBlue, thickness = 1.dp)
            Spacer(Modifier.height(12.dp))
            
            // Progress section
            if (nextTier != null) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (availableTier != null) 
                                "Ready to unlock ${nextTier.displayName}!"
                            else 
                                "Progress to ${nextTier.displayName}",
                            fontSize = 13.sp,
                            fontWeight = if (availableTier != null) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (availableTier != null) Color(0xFF16A34A) else TextDark
                        )
                        Text(
                            text = "${(tierProgressFraction * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                    }
                    
                    Spacer(Modifier.height(6.dp))
                    
                    LinearProgressIndicator(
                        progress = { tierProgressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = if (availableTier != null) Color(0xFF16A34A) else IconBlue,
                        trackColor = Color(0xFFE2E8F0)
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    if (availableTier != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Unlock Cost",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = availableTier.unlockCost.toString(),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextDark
                                )
                            }
                            
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Your Cash",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = playerMoney.toString(),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (canUnlock) Color(0xFF16A34A) else Color(0xFFE74C3C)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "$revenueToNextTier more revenue needed",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
                
                // Unlock button
                if (availableTier != null) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onUnlockNextTier,
                        enabled = canUnlock,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryDark,
                            disabledContainerColor = Color.Gray
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Unlock ${nextTier.displayName} (${availableTier.unlockCost})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    if (!canUnlock) {
                        Text(
                            text = "Insufficient funds",
                            fontSize = 11.sp,
                            color = Color(0xFFE74C3C),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                // Max tier reached
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "You've unlocked all store sections!",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF16A34A)
                    )
                }
            }
        }
    }
}

