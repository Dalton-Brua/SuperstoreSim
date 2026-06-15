package com.example.superstoresimulator.ui.root

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.Screen
import com.example.superstoresimulator.domain.tutorial.TutorialStep
import com.example.superstoresimulator.ui.state.TutorialUIState
import com.example.superstoresimulator.ui.theme.CardWhite
import com.example.superstoresimulator.ui.theme.Primary
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.SuccessSurface
import com.example.superstoresimulator.ui.theme.SuccessTextDark
import com.example.superstoresimulator.ui.theme.TextMuted
import com.example.superstoresimulator.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Number of real tutorial steps (excludes the terminal TUTORIAL_COMPLETE marker). */
private val TUTORIAL_STEP_COUNT = TutorialStep.entries.size - 1

/** Bottom-nav label for the screen a tutorial step points the player toward. */
private fun Screen.navLabel(): String = when (this) {
    Screen.GAME -> "Store"
    Screen.INVENTORY -> "Inventory"
    Screen.STAFF -> "Manage"
    Screen.HISTORY -> "History"
    Screen.METRICS -> "Metrics"
}

/**
 * Persistent tutorial banner shown above the bottom nav until the tutorial completes.
 * Swipe it right to tuck it off-screen; a small handle on the edge brings it back.
 */
@Composable
fun BoxScope.TutorialBanner(
    tutorial: TutorialUIState,
    currentScreen: Screen,
    onGoToScreen: (Screen) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tutorial.tutorialComplete) return

    var collapsed by remember { mutableStateOf(false) }

    if (collapsed) {
        RestoreHandle(
            onRestore = { collapsed = false },
            modifier = modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp),
        )
        return
    }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val offsetX = remember { Animatable(0f) }
    val dismissThresholdPx = with(density) { 120.dp.toPx() }
    val offscreenPx = with(density) { 520.dp.toPx() }

    val stepNumber = (tutorial.currentStep.ordinal + 1).coerceAtMost(TUTORIAL_STEP_COUNT)
    val target = tutorial.hintScreen
    val showGoTo = target != null && target != currentScreen

    Card(
        modifier = modifier
            .align(Alignment.BottomCenter)
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX.value > dismissThresholdPx) {
                            scope.launch {
                                offsetX.animateTo(offscreenPx)
                                collapsed = true
                                offsetX.snapTo(0f)
                            }
                        } else {
                            scope.launch { offsetX.animateTo(0f) }
                        }
                    },
                ) { _, dragAmount ->
                    // Only allow dragging toward the right edge (off-screen).
                    scope.launch {
                        offsetX.snapTo((offsetX.value + dragAmount).coerceAtLeast(0f))
                    }
                }
            }
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column {
            // Drag affordance.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(TextMuted, RoundedCornerShape(2.dp))
            )

            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                // Accent rail
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .fillMaxHeight()
                        .background(Primary)
                )
                Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 14.dp)) {
                    Text(
                        text = "TUTORIAL · STEP $stepNumber OF $TUTORIAL_STEP_COUNT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = tutorial.displayTitle,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryDark,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = tutorial.instruction,
                        fontSize = 13.sp,
                        color = TextSecondary,
                    )

                    if (tutorial.hintText.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SuccessSurface, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(text = "💡", fontSize = 14.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = tutorial.hintText,
                                fontSize = 12.sp,
                                color = SuccessTextDark,
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onSkip) {
                            Text("Skip tutorial", fontSize = 13.sp, color = TextSecondary)
                        }
                        if (showGoTo) {
                            Button(
                                onClick = { onGoToScreen(target!!) },
                                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            ) {
                                Text("Go to ${target!!.navLabel()}", color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Collapsed tutorial handle hugging the right edge; tap or swipe left to reopen. */
@Composable
private fun RestoreHandle(
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clickable(onClick = onRestore)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount < -8f) onRestore()
                }
            }
            .background(
                color = Primary,
                shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp),
            )
            .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "💡", fontSize = 20.sp)
    }
}
