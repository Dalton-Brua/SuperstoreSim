package com.example.superstoresimulator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.ui.theme.Caution
import com.example.superstoresimulator.ui.theme.CautionSurface
import com.example.superstoresimulator.ui.theme.DarkBackground
import com.example.superstoresimulator.ui.theme.TextSecondary
import com.example.superstoresimulator.ui.theme.TextWhite

/**
 * Compact indicator shown above the transaction card.
 *
 * States:
 *  - pendingCustomers > 0  → Caution badge (amber): "X customers waiting"
 *  - pendingCustomers == 0 && !transactionActive  → DarkBackground row: "Register idle"
 *  - transactionActive (queue empty)  → hidden
 */
@Composable
fun CustomerQueueIndicator(
    pendingCustomers: Int,
    transactionActive: Boolean,
    modifier: Modifier = Modifier
) {
    // Hide while a transaction is actively being processed and no one else is waiting
    if (transactionActive && pendingCustomers == 0) return

    val waiting = pendingCustomers > 0

    val label = when {
        waiting           -> if (pendingCustomers == 1) "1 customer waiting" else "$pendingCustomers customers waiting"
        transactionActive -> return   // guard already above; keep compiler happy
        else              -> "Register idle — no customers yet"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (waiting) CautionSurface else DarkBackground)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (waiting) Icons.Default.Person else Icons.Default.PersonOff,
            contentDescription = null,
            tint = if (waiting) Caution else TextSecondary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (waiting) FontWeight.Bold else FontWeight.Normal,
            color = if (waiting) Caution else TextWhite.copy(alpha = 0.75f)
        )
    }
}
