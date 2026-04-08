package com.example.superstoresimulator.ui.theme

import androidx.compose.ui.graphics.Color

// ===== PRIMARY BRAND COLORS =====
val Primary = Color(0xFF3B82F6)  // Primary Blue
val PrimaryDark = Color(0xFF1E40AF)  // Darker Blue (used for text/icons)
val Secondary = Color(0xFF22C55E)  // Green
val Destructive = Color(0xFFEF4444)  // Red

// ===== STORE STATE COLORS =====
val OpenGreen = Color(0xFF2ECC71)  // Store Open
val ClosedRed = Color(0xFFE74C3C)  // Store Closed
val ClosingOrange = Color(0xFFF39C12)  // Store Closing
val ClosingProceduresPurple = Color(0xFF9B59B6)  // Closing Procedures
val OpeningBlue = Color(0xFF3498DB)  // Store Opening

// ===== BACKGROUND & SURFACE COLORS =====
val LightBackground = Color(0xFFF5F8FF)  // Light blue background
val CardBlue = Color(0xFFE0EDFF)  // Distinct blue for card highlights (more prominent than background)
val DarkBackground = Color(0xFF2C3E50)  // Dark background (used by TimeDisplayBar, pill containers)
val DarkSurface = Color(0xFF34495E)     // Slightly elevated surface on top of DarkBackground (idle pill segments)
val CardWhite = Color(0xFFFFFFFF)  // Card background
val LightGrey = Color(0xFF64748B)  // Light grey text

// ===== CAUTION / WARNING STATE =====
val Caution = Color(0xFFF39C12)         // Amber warning — customers waiting, queue pressure
val CautionSurface = Color(0xFF7C5000)  // Dark amber surface for caution badge backgrounds

// ===== MATERIAL 3 THEME COLORS (Dark) =====
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

// ===== MATERIAL 3 THEME COLORS (Light) =====
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// ===== TEXT & SEMANTIC COLORS =====
val TextPrimary = Color(0xFF2563EB)  // Primary text (blue)
val TextSecondary = Color(0xFF64748B)  // Secondary text (grey)
val TextWhite = Color.White  // White text
val TextDark = Color(0xFF1C1B1F)  // Dark text (Material 3 default)

// ===== PLACEHOLDER / STATUS STATE COLORS =====
val PlaceholderSurface = Color(0xFFF1F5F9)  // Very light slate — background for status placeholder cards
val TextMuted = Color(0xFF94A3B8)           // Slate-400 — dimmed icons and secondary placeholder text
val IconBlue = Color(0xFF60A5FA)            // Blue-400 — informational / "waiting" icon tint

// ===== CHIP / PILL COLORS =====
val ChipSurface = Color(0xFFE2E8F0)   // Unselected chip/pill background (slate-200)
val ChipTextDark = Color(0xFF1E293B)  // Dark text on unselected chips (slate-900)

// ===== DEPRECATED - USE NEW NAMES =====
@Deprecated("Use Primary instead", ReplaceWith("Primary"))
val upgradeButtonColor = Color(0xFF3B82F6)