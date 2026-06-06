package com.example.superstoresimulator.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.ui.theme.ChipTextDark
import com.example.superstoresimulator.ui.theme.DestructiveDark
import com.example.superstoresimulator.ui.theme.Primary
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.Secondary
import com.example.superstoresimulator.ui.theme.TextSecondary

@Composable
fun PriceSlider(
    label: String,
    currentPercent: Int,
    range: ClosedFloatingPointRange<Float>,
    onSet: (Int) -> Unit,
    modifier: Modifier = Modifier,
    labelColor: Color = ChipTextDark,
    secondaryColor: Color = TextSecondary,
    sliderModifier: Modifier = Modifier.fillMaxWidth()
) {
    var sliderValue by remember(currentPercent) { mutableStateOf(currentPercent.toFloat()) }
    val displayVal = sliderValue.toInt()
    val sign = if (displayVal > 0) "+" else ""
    val valueColor = when {
        displayVal > 0 -> DestructiveDark
        displayVal < 0 -> Secondary
        else -> secondaryColor
    }

    val min = range.start
    val max = range.endInclusive
    val total = max - min
    val zeroFraction = (-min) / total

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = labelColor)
            Text("$sign$displayVal%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor)
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onSet(sliderValue.toInt()) },
            valueRange = range,
            modifier = sliderModifier,
            colors = SliderDefaults.colors(
                thumbColor = PrimaryDark,
                activeTrackColor = Primary
            )
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            Text("${min.toInt()}%", fontSize = 10.sp, color = secondaryColor,
                modifier = Modifier.align(Alignment.CenterStart))
            Text("0%", fontSize = 10.sp, color = secondaryColor,
                modifier = Modifier.fillMaxWidth(zeroFraction).align(Alignment.CenterStart)
                    .wrapContentWidth(Alignment.End))
            Text("+${max.toInt()}%", fontSize = 10.sp, color = secondaryColor,
                modifier = Modifier.align(Alignment.CenterEnd))
        }
    }
}
