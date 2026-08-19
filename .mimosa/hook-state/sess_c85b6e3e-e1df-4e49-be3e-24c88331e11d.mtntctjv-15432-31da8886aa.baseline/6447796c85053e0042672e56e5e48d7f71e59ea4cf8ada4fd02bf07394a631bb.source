package com.palmnote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.palmnote.R
import com.palmnote.ui.theme.AppIcon

/**
 * 图标选择器 — 分类 Tab + 网格
 * 全部 109 个 AppIcon，按类别筛选，每类 3~20 个，一目了然
 */
@Composable
fun IconPickerGrid(
    selectedIcon: AppIcon,
    onSelected: (AppIcon) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 6
) {
    val allLabel = stringResource(R.string.icon_category_all)
    val studyLabel = stringResource(R.string.icon_category_study)
    val sportsLabel = stringResource(R.string.icon_category_sports)
    val lifeLabel = stringResource(R.string.icon_category_life)
    val workLabel = stringResource(R.string.icon_category_work)
    val healthLabel = stringResource(R.string.icon_category_health)
    val travelLabel = stringResource(R.string.icon_category_travel)
    val creativeLabel = stringResource(R.string.icon_category_creative)
    val financeLabel = stringResource(R.string.icon_category_finance)
    val goalsLabel = stringResource(R.string.icon_category_goals)
    val moodLabel = stringResource(R.string.icon_category_mood)
    val otherLabel = stringResource(R.string.icon_category_other)

    val categories = remember {
        mapOf(
            allLabel to AppIcon.entries.toList(),
            studyLabel to listOf(AppIcon.MenuBook, AppIcon.School, AppIcon.Edit, AppIcon.Psychology, AppIcon.Flag, AppIcon.Lightbulb, AppIcon.PushPin, AppIcon.Note, AppIcon.Star),
            sportsLabel to listOf(AppIcon.DirectionsRun, AppIcon.FitnessCenter, AppIcon.SelfImprovement, AppIcon.DirectionsBike, AppIcon.SportsSoccer, AppIcon.SportsBasketball, AppIcon.SportsEsports),
            lifeLabel to listOf(AppIcon.Home, AppIcon.Restaurant, AppIcon.Coffee, AppIcon.CleaningServices, AppIcon.ShoppingCart, AppIcon.Pets, AppIcon.ContentCut, AppIcon.ChildCare, AppIcon.Chair, AppIcon.Kitchen, AppIcon.Checkroom, AppIcon.Face, AppIcon.Cookie, AppIcon.LocalCafe),
            workLabel to listOf(AppIcon.Work, AppIcon.Payments, AppIcon.BusinessCenter, AppIcon.Build, AppIcon.Receipt, AppIcon.Devices, AppIcon.Code, AppIcon.PhoneAndroid),
            healthLabel to listOf(AppIcon.WaterDrop, AppIcon.Medication, AppIcon.WbSunny, AppIcon.SmokingRooms, AppIcon.Bedtime, AppIcon.Eco),
            travelLabel to listOf(AppIcon.Flight, AppIcon.DirectionsCar, AppIcon.BeachAccess, AppIcon.DirectionsCarFilled, AppIcon.LocalShipping),
            creativeLabel to listOf(AppIcon.Palette, AppIcon.MusicNote, AppIcon.Movie, AppIcon.Mic, AppIcon.CameraAlt, AppIcon.VideogameAsset, AppIcon.Brush, AppIcon.Headphones, AppIcon.Tv),
            financeLabel to listOf(AppIcon.Savings, AppIcon.TrendingUp, AppIcon.AccountBalance, AppIcon.CreditCard, AppIcon.Percent, AppIcon.HealthAndSafety),
            goalsLabel to listOf(AppIcon.CheckCircle, AppIcon.LocalFireDepartment, AppIcon.Repeat, AppIcon.CalendarMonth, AppIcon.Today, AppIcon.Assignment, AppIcon.EmojiEvents, AppIcon.Celebration),
            moodLabel to listOf(AppIcon.SentimentVerySatisfied, AppIcon.SentimentSatisfied, AppIcon.SentimentNeutral, AppIcon.SentimentDissatisfied, AppIcon.SentimentVeryDissatisfied, AppIcon.Mood, AppIcon.Favorite),
            otherLabel to listOf(AppIcon.Timer, AppIcon.Settings, AppIcon.MoreHoriz, AppIcon.Inventory2, AppIcon.PieChart, AppIcon.BarChart, AppIcon.ShowChart, AppIcon.Analytics, AppIcon.CardGiftcard, AppIcon.Redeem, AppIcon.VolunteerActivism, AppIcon.Groups, AppIcon.Group, AppIcon.Gavel, AppIcon.Shield, AppIcon.Casino, AppIcon.FamilyRestroom, AppIcon.HouseSiding, AppIcon.Workspaces, AppIcon.RecordVoiceOver, AppIcon.Air, AppIcon.AcUnit, AppIcon.Cloud, AppIcon.Handshake, AppIcon.SwapHoriz, AppIcon.LocalHospital, AppIcon.LocalBar, AppIcon.Star)
        )
    }
    var selectedCategory by remember { mutableStateOf(allLabel) }
    val icons = categories[selectedCategory] ?: AppIcon.entries.toList()

    Column(modifier = modifier) {
        // 分类 Tab
        ScrollableTabRow(
            selectedTabIndex = categories.keys.indexOf(selectedCategory).coerceAtLeast(0),
            containerColor = MaterialTheme.colorScheme.surface,
            edgePadding = 8.dp,
            divider = {}
        ) {
            categories.keys.forEach { category ->
                Tab(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    text = {
                        Text(
                            category,
                            style = if (selectedCategory == category) MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                    else MaterialTheme.typography.labelMedium
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // 图标网格
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.heightIn(max = 240.dp)
        ) {
            items(icons.size, key = { icons[it] }) { index ->
                val icon = icons[index]
                val isSelected = selectedIcon == icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelected(icon) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon.imageVector,
                        contentDescription = icon.name,
                        modifier = Modifier.size(24.dp),
                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                               else icon.tint
                    )
                }
            }
        }
    }
}
