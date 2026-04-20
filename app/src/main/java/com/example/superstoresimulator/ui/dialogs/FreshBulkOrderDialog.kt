package com.example.superstoresimulator.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.superstoresimulator.domain.Money

@Composable
fun FreshBulkOrderDialog(
    money: Money,
    onConfirm: (maxTotalQuantity: Int, casePacksPerItem: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var maxTotalQuantity by remember { mutableStateOf(50f) }
    var casePacksPerItem by remember { mutableStateOf(2f) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.LocalShipping,
                        contentDescription = null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.height(28.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Fresh Bulk Order",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                }

                Text(
                    "Order fresh items with volume discounts",
                    fontSize = 13.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

                Spacer(Modifier.height(16.dp))

                // Max Total Quantity Slider
                Text(
                    "Maximum Stock Threshold: ${maxTotalQuantity.toInt()} items",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E293B),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                Slider(
                    value = maxTotalQuantity,
                    onValueChange = { maxTotalQuantity = it },
                    valueRange = 10f..200f,
                    steps = 9,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF2563EB),
                        activeTrackColor = Color(0xFF2563EB),
                        inactiveTrackColor = Color(0xFFE2E8F0)
                    )
                )

                Text(
                    "Only order items with total stock below this amount",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    style = androidx.compose.ui.text.TextStyle(
                        textDecoration = TextDecoration.None
                    ),
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                // Case Packs Per Item Slider
                Text(
                    "Case Packs Per Item: ${casePacksPerItem.toInt()}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E293B),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                Slider(
                    value = casePacksPerItem,
                    onValueChange = { casePacksPerItem = it },
                    valueRange = 1f..10f,
                    steps = 8,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF2563EB),
                        activeTrackColor = Color(0xFF2563EB),
                        inactiveTrackColor = Color(0xFFE2E8F0)
                    )
                )

                Spacer(Modifier.height(8.dp))

                // Discount Tiers Info
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F4F8)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            "Fresh Discount Tiers",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("< 15 cases: 0%", fontSize = 10.sp, color = Color(0xFF64748B))
                        Text("≥ 15 cases: 5% off", fontSize = 10.sp, color = Color(0xFF64748B))
                        Text("≥ 30 cases: 10% off", fontSize = 10.sp, color = Color(0xFF64748B))
                        Text("≥ 50 cases: 15% off", fontSize = 10.sp, color = Color(0xFF64748B))
                    }
                }

                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

                Spacer(Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF2563EB)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            onConfirm(maxTotalQuantity.toInt(), casePacksPerItem.toInt())
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Order Fresh Items", color = Color.White)
                    }
                }
            }
        }
    }
}

