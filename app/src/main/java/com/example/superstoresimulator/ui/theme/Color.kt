package com.example.superstoresimulator.ui.theme

import androidx.compose.ui.graphics.Color

// ===== PRIMARY BRAND COLORS =====
val Primary = Color(0xFF3B82F6)       // Primary Blue
val PrimaryDark = Color(0xFF1E40AF)   // Darker Blue (text/icons)
val PrimaryLight = Color(0xFF93C5FD)  // Light Blue (switch tracks)
val Secondary = Color(0xFF22C55E)     // Green
val Destructive = Color(0xFFEF4444)   // Red
val DestructiveDark = Color(0xFFDC2626)  // Red-600 (destructive text/icons)

// ===== STORE STATE COLORS =====
val OpenGreen = Color(0xFF2ECC71)           // Store Open
val ClosedRed = Color(0xFFE74C3C)           // Store Closed
val ClosingOrange = Color(0xFFF39C12)       // Store Closing
val ClosingProceduresPurple = Color(0xFF9B59B6)  // Closing Procedures
val OpeningBlue = Color(0xFF3498DB)         // Store Opening
val PausedOrange = Color(0xFFE67E22)        // Paused state

// ===== SEMANTIC STATUS COLORS =====
val Success = Color(0xFF16A34A)       // Green-600 — success, unlocked, positive
val SuccessAccent = Color(0xFF059669) // Emerald-600 — report accents
val PositiveGreen = Color(0xFF27AE60) // Green — net positive income
val CriticalRed = Color(0xFFB91C1C)   // Red-800 — critical/out-of-stock
val Violet = Color(0xFF8B5CF6)        // Purple-500 — tier upgrades, backroom badge
val Amber = Color(0xFFF59E0B)         // Amber-500 — warnings, utilization bar
val Emerald = Color(0xFF10B981)       // Emerald-500 — fresh handler role
val Teal = Color(0xFF0F766E)          // Teal-700 — export button
val OrangeAccent = Color(0xFFEA580C)  // Orange-600 — freshness warning
val FreshnessYellow = Color(0xFFFBBF24) // Amber-400 — mid freshness

// ===== CAUTION / WARNING STATE =====
val Caution = Color(0xFFF39C12)         // Amber warning — customers waiting, queue pressure
val CautionSurface = Color(0xFF7C5000)  // Dark amber surface for caution badge backgrounds
val CautionDark = Color(0xFFD97706)     // Amber-600 — markdown/warning text

// ===== BACKGROUND & SURFACE COLORS =====
val LightBackground = Color(0xFFF5F8FF)   // Light blue background
val CardBlue = Color(0xFFE0EDFF)          // Distinct blue for card highlights
val DarkBackground = Color(0xFF2C3E50)    // Dark background (TimeDisplayBar, pill containers)
val DarkSurface = Color(0xFF34495E)       // Slightly elevated surface on DarkBackground
val DarkNavy = Color(0xFF1E2A3A)          // Very dark navy (player role indicator)
val CardWhite = Color(0xFFFFFFFF)         // Card background
val LightGrey = Color(0xFF64748B)         // Light grey text
val SurfaceSubtle = Color(0xFFF8FAFC)     // Slate-50 — very subtle surface
val SurfaceElevated = Color(0xFFFAFAFA)   // Near-white elevated surface
val Scrim = Color.Black                   // Scrim overlay base

// ===== STATUS SURFACE COLORS =====
val SuccessSurface = Color(0xFFF0FDF4)       // Green-50 — success background
val SuccessChipSurface = Color(0xFFDCFCE7)   // Green-100 — success chip bg
val ErrorSurface = Color(0xFFFEE2E2)         // Red-100 — error/idle chip bg
val ErrorSurfaceSubtle = Color(0xFFFEF2F2)   // Red-50 — subtle error bg
val DangerSurface = Color(0xFFFFEBEE)        // Material Red-50 — danger bg
val WarningChipSurface = Color(0xFFFEF3C7)   // Amber-100 — waiting/warning chip bg
val WarningOrangeSurface = Color(0xFFFED7AA)  // Orange-200 — freshness warning surface
val InfoChipSurface = Color(0xFFDBEAFE)      // Blue-100 — info/zoning chip bg
val InfoSurface = Color(0xFFDEEBFF)          // Blue-50 — info highlight surface
val ActiveBlueSurface = Color(0xFFEFF6FF)    // Blue-50 variant — active tier bg

// ===== MATERIAL 3 THEME COLORS (Dark) =====
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

// ===== MATERIAL 3 THEME COLORS (Light) =====
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// ===== TEXT & SEMANTIC COLORS =====
val TextPrimary = Color(0xFF2563EB)       // Primary text (blue)
val TextSecondary = Color(0xFF64748B)     // Secondary text (grey / Slate-500)
val TextWhite = Color.White               // White text
val TextDark = Color(0xFF1C1B1F)          // Dark text (Material 3 default)
val SubtleText = Color(0xFF475569)        // Slate-600 — descriptive body text
val TextTertiary = Color(0xFF6B7280)      // Gray-500 — fine-print text
val TextNeutralDark = Color(0xFF1F2937)   // Gray-800 — dark neutral text
val TextNeutralMedium = Color(0xFF374151) // Gray-700 — medium neutral text
val SuccessTextDark = Color(0xFF166534)   // Green-800 — success chip text
val SuccessTextDarker = Color(0xFF14532D) // Green-900 — full-store-unlocked text
val WarningTextDark = Color(0xFF92400E)   // Amber-800 — waiting chip text
val WarningTextDarker = Color(0xFF78350F) // Amber-900 — dark text on amber
val ErrorTextDark = Color(0xFF991B1B)     // Red-900 — low-sales text

// ===== PLACEHOLDER / STATUS STATE COLORS =====
val PlaceholderSurface = Color(0xFFF1F5F9)  // Very light slate — background for status placeholder cards
val TextMuted = Color(0xFF94A3B8)           // Slate-400 — dimmed icons and secondary placeholder text
val IconBlue = Color(0xFF60A5FA)            // Blue-400 — informational / "waiting" icon tint

// ===== CHIP / PILL COLORS =====
val ChipSurface = Color(0xFFE2E8F0)   // Unselected chip/pill background (slate-200)
val ChipTextDark = Color(0xFF1E293B)  // Dark text on unselected chips (slate-900)

// ===== ROLE COLORS =====
val ManageGrey = Color(0xFF95A5A6)  // Silver grey — manage role indicator
val InactiveGrey = Color(0xFFCBD5E1)  // Slate-300 — inactive/empty/locked elements
val DisabledGrey = Color.Gray         // Standard disabled grey

// ===== NAVIGATION BAR COLORS =====
val NavBarBackground = Color(0xFFFFFFFF)        // Navigation bar background (white)
val NavBarSelectedIcon = Color(0xFF3B82F6)      // Selected icon color (Primary Blue)
val NavBarSelectedText = Color(0xFF3B82F6)      // Selected text color (Primary Blue)
val NavBarIndicator = Color(0xFFDEEBFF)         // Selected item indicator background (light blue)
val NavBarUnselectedIcon = Color(0xFF64748B)   // Unselected icon color (grey)
val NavBarUnselectedText = Color(0xFF64748B)   // Unselected text color (grey)

// ===== PROGRESS BAR COLORS =====
val ProgressBarTrack = Color(0xFFE2E8F0)        // Progress bar track/background (light grey)
val ProgressBarIndicator = Color(0xFF334155)    // Progress bar indicator (dark grey/slate)

// ===== DEPRECATED - USE NEW NAMES =====
@Deprecated("Use Primary instead", ReplaceWith("Primary"))
val upgradeButtonColor = Color(0xFF3B82F6)