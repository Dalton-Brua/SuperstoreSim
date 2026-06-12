package com.example.superstoresimulator.ui.screens.offline

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.offline.OfflineCatchUpResult
import com.example.superstoresimulator.domain.offline.OfflineEvent
import com.example.superstoresimulator.domain.offline.OfflineProgress

private val BgColor = Color(0xFF0F172A)
private val CardBgColor = Color(0xFF1E293B)
private val SubtextColor = Color(0xFF94A3B8)
private val MutedColor = Color(0xFF64748B)
private val AccentBlue = Color(0xFF3B82F6)
private val GreenColor = Color(0xFF4ADE80)
private val RedColor = Color(0xFFF87171)
private val YellowColor = Color(0xFFFBBF24)
private val BlueColor = Color(0xFF60A5FA)
private val PurpleColor = Color(0xFFA78BFA)
private val SunColor = Color(0xFFFDE68A)

@Composable
fun OfflineCatchUpScreen(
    progress: OfflineProgress?,
    result: OfflineCatchUpResult?,
    onContinue: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val isComplete = result != null

    val events = result?.events ?: progress?.events ?: emptyList()
    val money = result?.currentMoney ?: progress?.currentMoney ?: Money.ZERO
    val revenue = result?.revenue ?: progress?.revenueEarned ?: Money.ZERO
    val expenses = result?.expenses ?: progress?.expensesPaid ?: Money.ZERO
    val currentDay = progress?.currentDay ?: 0

    val fraction = progress?.fractionComplete ?: 1f
    val animatedProgress by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 200),
        label = "catch-up-progress",
    )

    val listState = rememberLazyListState()

    LaunchedEffect(events.size) {
        if (events.isNotEmpty()) {
            listState.animateScrollToItem(events.size - 1)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (isComplete) "Welcome Back!" else "Catching Up...",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (isComplete && result != null) {
                val hoursAway = result.gameMinutesSimulated / 60
                val daysText = if (result.daysCrossed > 0) {
                    "${result.daysCrossed} day${if (result.daysCrossed != 1) "s" else ""}"
                } else ""
                val hoursText = "${hoursAway % 24}h"
                val timeText = if (daysText.isNotEmpty()) "$daysText $hoursText simulated" else "$hoursText simulated"
                Text(
                    text = timeText,
                    fontSize = 16.sp,
                    color = SubtextColor,
                )
            } else {
                Text(
                    text = "Day ${currentDay + 1}",
                    fontSize = 18.sp,
                    color = SubtextColor,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (!isComplete) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = AccentBlue,
                    trackColor = CardBgColor,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${(fraction * 100).toInt()}%",
                    fontSize = 14.sp,
                    color = MutedColor,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(20.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                MetricChip(
                    icon = Icons.Default.AccountBalance,
                    label = "Balance",
                    value = money.toString(),
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                MetricChip(
                    icon = Icons.Default.AttachMoney,
                    label = "Revenue",
                    value = revenue.toString(),
                    color = GreenColor,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                MetricChip(
                    icon = Icons.Default.MoneyOff,
                    label = "Expenses",
                    value = expenses.toString(),
                    color = RedColor,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (events.isNotEmpty()) {
                HorizontalDivider(color = CardBgColor)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Activity",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = SubtextColor,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(events) { event ->
                        EventRow(event)
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            if (isComplete && onContinue != null) {
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = "Continue",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricChip(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CardBgColor)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = MutedColor,
        )
    }
}

@Composable
private fun EventRow(event: OfflineEvent) {
    val (icon, text, color) = when (event) {
        is OfflineEvent.NewDay -> Triple(
            Icons.Default.WbSunny,
            "Day ${event.dayNumber + 1} started",
            SunColor,
        )
        is OfflineEvent.TruckArrived -> Triple(
            Icons.Default.LocalShipping,
            if (event.isFresh) "Fresh truck arrived" else "Truck arrived",
            BlueColor,
        )
        is OfflineEvent.StaffHired -> Triple(
            Icons.Default.PersonAdd,
            "Hired ${event.role} — ${event.reason}",
            PurpleColor,
        )
        is OfflineEvent.AutoOrderPlaced -> Triple(
            Icons.Default.ShoppingCart,
            if (event.isFresh)
                "${event.itemCount} fresh case packs ordered (${event.totalCost})"
            else
                "${event.itemCount} case packs ordered (${event.totalCost})",
            GreenColor,
        )
        is OfflineEvent.ItemsExpired -> Triple(
            Icons.Default.DeleteSweep,
            "${event.count} items expired (${event.valueLost} lost)",
            YellowColor,
        )
        is OfflineEvent.ManagerAction -> Triple(
            Icons.Default.Star,
            "${event.subject} — ${event.detail}",
            PurpleColor,
        )
        is OfflineEvent.StaffTerminated -> Triple(
            Icons.Default.PersonOff,
            "Terminated ${event.role} — ${event.reason}",
            RedColor,
        )
        is OfflineEvent.TruckScheduled -> Triple(
            Icons.Default.Schedule,
            if (event.isFresh) "Fresh truck scheduled for Day ${event.arrivalDay + 1}"
            else "Truck scheduled for Day ${event.arrivalDay + 1}",
            BlueColor,
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(CardBgColor)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            color = Color(0xFFCBD5E1),
        )
    }
}
