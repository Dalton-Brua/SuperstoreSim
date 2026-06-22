package com.example.superstoresimulator.ui.screens.empire

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.ui.theme.InfoChipSurface
import com.example.superstoresimulator.ui.theme.PrimaryDark

/**
 * Banner shown while the player is hands-on operating a store (loop 1 active inside empire mode).
 * Caller wires [onExit] to emit [GameEvent.ExitOperatedStore].
 */
@Composable
fun OperatingStoreBanner(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(InfoChipSurface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Store, contentDescription = null, tint = PrimaryDark)
            Text(
                text = "Running a store by hand",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = PrimaryDark,
            )
        }
        OutlinedButton(onClick = onExit) {
            Text("Exit to empire", fontWeight = FontWeight.SemiBold)
        }
    }
}
