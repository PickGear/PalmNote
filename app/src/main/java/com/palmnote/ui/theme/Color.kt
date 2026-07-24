package com.palmnote.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ============================================================
// PalmNote Design Tokens
// ============================================================

// ----- Brand Colors -----
val PrimaryGreen = Color(0xFF2D4A3E)
val PrimaryGreenLight = Color(0xFF4A7A5E)
val AccentOrange = Color(0xFFFF8C42)

// ----- Status Colors -----
val StatusHeld = Color(0xFF34A853)
val StatusAway = Color(0xFFFF9800)
val StatusRemoved = Color(0xFF9E9E9E)
val StatusActive = StatusHeld
val Success = StatusHeld
val StatusRetired = StatusRemoved
val StatusLost = Color(0xFFEA4335)
val StatusSold = Color(0xFFFBBC04)

// ----- Warning -----
val Warning = Color(0xFFFFBD44)
val DarkWarning = Color(0xFFE8B94A)

// ----- Error -----
val ErrorLight = Color(0xFFEA4335)

// ----- Surface / Background (Light) -----
val BackgroundLight = Color(0xFFF8F6F3)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFF2EFE9)
val NavBarLight = Color(0xFFFBF8F2)

// ----- Surface / Background (Dark) -----
val BackgroundDark = Color(0xFF121212)
val SurfaceDark = Color(0xFF1E1E1E)
val SurfaceVariantDark = Color(0xFF2A2A2A)
val NavBarDark = Color(0xFF1E1E1E)

// ----- Text Colors (Light) -----
val TextPrimaryLight = Color(0xFF1C1B1F)
val TextSecondaryLight = Color(0xFF7A7570)
val TextTertiaryLight = Color(0xFFB0ABA5)

// ----- Text Colors (Dark) -----
val TextPrimaryDark = Color(0xFFE8E8E8)
val TextSecondaryDark = Color(0xFFABABAB)
val TextTertiaryDark = Color(0xFF8A8580)

// ----- Dark Theme Brand Colors -----
val DarkPrimary = Color(0xFF7BC4A0)
val DarkSecondary = Color(0xFFF0A060)
val DarkSuccess = Color(0xFF66D98D)
val ErrorDark = Color(0xFFFF6B6B)

// ----- Outline -----
val OutlineLight = Color(0xFFD0CBC5)
val OutlineDark = Color(0xFF4A4540)

// ----- Module Tint Colors (Light) -----
val AssetTint = Color(0xFFE8EFF5)
val BillTint = Color(0xFFFFF8EE)
val GoalTint = Color(0xFFEAF4EC)
val AnniversaryTint = Color(0xFFFFF0EE)
val MomentTint = Color(0xFFF4EFFE)

// ----- Module Tint Colors (Dark) -----
val AssetTintDark = Color(0xFF1A2A36)
val BillTintDark = Color(0xFF2E261A)
val GoalTintDark = Color(0xFF1A2E20)
val AnniversaryTintDark = Color(0xFF2E1A1C)
val MomentTintDark = Color(0xFF221A30)

// ----- Chart Colors -----
val ChartColors = listOf(
    Color(0xFFE8D5B7), AccentOrange, StatusActive,
    Color(0xFF4285F4), Color(0xFFFBBC04), Color(0xFFEA4335),
    Color(0xFF9C27B0), Color(0xFF00BCD4)
)

// ----- Utility Colors -----
val ExpenseRed = Color(0xFFEA4335)
val IncomeGreen = Color(0xFF34A853)
val InfoBlue = Color(0xFF4285F4)
val Purple = Color(0xFF9C27B0)
val DeepOrange = Color(0xFFFF6D00)
val Amber = Color(0xFFFBBC04)
val Brown = Color(0xFF795548)

// ----- Module Theme Colors -----
val ModuleHome = Color(0xFF3D6B4A)
val ModuleBill = Color(0xFFA67A00)
val ModuleLife = Color(0xFFC2185B)
val ModuleSettings = Color(0xFF607D8B)
val ModuleItem = Color(0xFF2A6BAB)

// ----- Life Module Category Colors -----
val LifePlan = Color(0xFF7C8CF0)
val LifeTime = Color(0xFFF07070)
val LifeRecord = Color(0xFF50C890)

// ----- Life Module Preset Template Colors -----
val LifeSaving = Color(0xFFEC407A)
val LifeShopping = Color(0xFF7C8CF0)
val LifeTravel = Color(0xFFFF7043)
val LifeReading = Color(0xFF26A69A)
val LifeStudy = Color(0xFFAB47BC)
val LifeTodo = Color(0xFF5C6BC0)
val LifeCountdown = Color(0xFFF07070)
val LifeCountUp = Color(0xFF50C890)
val LifeBirthday = Color(0xFFFFCA28)
val LifeAnniversary = Color(0xFFF07070)
val LifeHabit = Color(0xFFFF7043)
val LifeMoodColor = Color(0xFFFFCA28)
val LifeJournal = Color(0xFFAB47BC)
val LifeFocus = Color(0xFF00ACC1)
val LifeSubscription = Color(0xFF66BB6A)
val LifeReport = Color(0xFF42A5F5)

// ----- Mood Colors -----
val LifeMoodHappy = Color(0xFFFFCA28)
val LifeMoodNormal = Color(0xFF78909C)
val LifeMoodUpset = Color(0xFF5C6BC0)
val LifeMoodSad = Color(0xFFEF5350)
val LifeMoodAngry = Color(0xFFE53935)

// ----- Category Colors -----
val CatLightGreen = Color(0xFF7CB342)
val CatRed = Color(0xFFD32F2F)
val CatOrange = Color(0xFFF4511E)
val CatLime = Color(0xFFC0CA33)
val CatTeal = Color(0xFF00897B)
val CatWarmGray = Color(0xFF8D6E63)
val CatBlueGray = Color(0xFF546E7A)
val CatLightPurple = Color(0xFFCE93D8)
val CatPink = Color(0xFFF48FB1)
val CatGold = Color(0xFFFFD54F)
val CatRose = Color(0xFFE91E63)
val CatSkyBlue = Color(0xFF29B6F6)
val CatMint = Color(0xFF4DB6AC)
val CatPeach = Color(0xFFFFAB91)

// ----- Bright Category Colors (supplement) -----
val CatBrightRed = Color(0xFFE57373)
val CatBrightTeal = Color(0xFF4DD0E1)
val CatIndigo = Color(0xFF7986CB)
val CatWarmRose = Color(0xFFFF80AB)
val CatFreshGreen = Color(0xFF66BB6A)
val CatBrightBlue = Color(0xFF64B5F6)
val CatBrightPink = Color(0xFFF06292)
val CatBrightPurple = Color(0xFFBA68C8)

// ----- Gray Scale -----
val Gray100 = Color(0xFFF5F5F5)
val Gray400 = Color(0xFFBDBDBD)

// ----- Composable helpers -----
@Composable
fun assetTint() = if (LocalIsDarkTheme.current) AssetTintDark else AssetTint
@Composable
fun billTint() = if (LocalIsDarkTheme.current) BillTintDark else BillTint
@Composable
fun goalTint() = if (LocalIsDarkTheme.current) GoalTintDark else GoalTint
@Composable
fun anniversaryTint() = if (LocalIsDarkTheme.current) AnniversaryTintDark else AnniversaryTint
@Composable
fun momentTint() = if (LocalIsDarkTheme.current) MomentTintDark else MomentTint

// ----- Life Module Tint Colors -----
@Composable
fun lifePlanTint() = if (LocalIsDarkTheme.current) Color(0xFF1A1530) else Color(0xFFEDE7F6)
@Composable
fun lifeTimeTint() = if (LocalIsDarkTheme.current) Color(0xFF301A1A) else Color(0xFFFFEBEE)
@Composable
fun lifeRecordTint() = if (LocalIsDarkTheme.current) Color(0xFF1A3020) else Color(0xFFE8F5E9)
