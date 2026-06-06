package com.example.superstoresimulator.ui.components.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.store.StoreSize
import com.example.superstoresimulator.ui.theme.CardBlue
import com.example.superstoresimulator.ui.theme.CardWhite
import com.example.superstoresimulator.ui.theme.ClosedRed
import com.example.superstoresimulator.ui.theme.DisabledGrey
import com.example.superstoresimulator.ui.theme.IconBlue
import com.example.superstoresimulator.ui.theme.PositiveGreen
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.TextDark
import com.example.superstoresimulator.ui.theme.TextSecondary
import com.example.superstoresimulator.ui.theme.TextWhite

/**
 * Card showing the current store size, daily rent and wages, with an upgrade button.
 */
@Composable
fun StoreSizeCard(
    currentSize: StoreSize,
    dailyRent: Money,
    dailyWages: Money,
    playerMoney: Money,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nextSize = StoreSize.nextSize(currentSize)
    val canUpgrade = nextSize != null && 
                     nextSize.upgradeCost != null && 
                     playerMoney >= nextSize.upgradeCost
    
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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Store,
                    contentDescription = null,
                    tint = IconBlue,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentSize.displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = PrimaryDark
                    )
                    Text(
                        text = "Backroom: ${currentSize.backroomCapPerItem} case packs",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
            
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = CardBlue, thickness = 1.dp)
            Spacer(Modifier.height(12.dp))
            
            // Operating costs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Daily Rent",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = dailyRent.toString(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark
                    )
                }
                
                Column {
                    Text(
                        text = "Daily Wages",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = dailyWages.toString(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark
                    )
                }
                
                Column {
                    Text(
                        text = "Total Daily Cost",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = (dailyRent + dailyWages).toString(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ClosedRed
                    )
                }
            }

            // Upgrade button (if not at max size)
            if (nextSize != null && nextSize.upgradeCost != null) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onUpgrade,
                    enabled = canUpgrade,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryDark,
                        disabledContainerColor = DisabledGrey
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
                        text = "Upgrade to ${nextSize.displayName} (${nextSize.upgradeCost})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextWhite
                    )
                }
                
                if (!canUpgrade) {
                    Text(
                        text = "Insufficient funds",
                        fontSize = 11.sp,
                        color = ClosedRed,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "✓ Maximum store size",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PositiveGreen,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

