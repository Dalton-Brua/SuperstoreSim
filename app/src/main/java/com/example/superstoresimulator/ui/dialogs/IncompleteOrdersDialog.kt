package com.example.superstoresimulator.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.superstoresimulator.domain.IncompleteOrderRequest
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.ui.theme.*

@Composable
fun IncompleteOrdersDialog(
    incompleteOrders: List<IncompleteOrderRequest>,
    itemNames: Map<Int, String>,
    itemCosts: Map<Int, Money>,
    money: Money,
    onOrderItem: (itemId: Int, casePacksRequested: Int) -> Unit,
    onOrderAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardWhite,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ShoppingCart,
                        contentDescription = null,
                        tint = DestructiveDark,
                        modifier = Modifier.height(28.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Incomplete Orders",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = ChipTextDark
                    )
                }

                Text(
                    "These auto-orders couldn't complete due to insufficient funds. Order them manually when funds are available.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                HorizontalDivider(color = ChipSurface, thickness = 1.dp)

                Spacer(Modifier.height(16.dp))

                if (incompleteOrders.isEmpty()) {
                    Text(
                        "No incomplete orders",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(24.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(incompleteOrders) { order ->
                            val itemName = itemNames[order.itemId] ?: "Item ${order.itemId}"
                            val costPerPack = itemCosts[order.itemId] ?: Money.ZERO
                            val totalCost = costPerPack * order.casePacksRequested
                            val canAfford = money >= totalCost

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (canAfford) SuccessSurface else ErrorSurfaceSubtle
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                itemName,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = ChipTextDark
                                            )
                                            Text(
                                                "${order.casePacksRequested} case packs × $costPerPack = $totalCost",
                                                fontSize = 12.sp,
                                                color = TextSecondary
                                            )
                                        }

                                        if (canAfford) {
                                            Button(
                                                onClick = {
                                                    onOrderItem(order.itemId, order.casePacksRequested)
                                                },
                                                modifier = Modifier
                                                    .width(80.dp)
                                                    .height(36.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = TextPrimary,
                                                    contentColor = TextWhite
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("Order", fontSize = 11.sp, color = TextWhite)
                                            }
                                        } else {
                                            Text(
                                                "Insufficient funds",
                                                fontSize = 11.sp,
                                                color = DestructiveDark,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    if (order.reason.isNotEmpty()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            order.reason,
                                            fontSize = 11.sp,
                                            color = DestructiveDark
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                HorizontalDivider(color = ChipSurface, thickness = 1.dp)

                Spacer(Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Close")
                    }

                    if (incompleteOrders.isNotEmpty()) {
                        Button(
                            onClick = {
                                onOrderAll()
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TextPrimary,
                                contentColor = TextWhite
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Order All Available", color = TextWhite, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

