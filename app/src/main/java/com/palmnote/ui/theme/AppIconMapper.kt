package com.palmnote.ui.theme

import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

fun iconFromName(name: String): ImageVector = when (name.lowercase()) {
    "savings" -> Icons.Default.AccountBalanceWallet
    "shopping_cart" -> Icons.Default.ShoppingCart
    "checklist" -> Icons.Default.Checklist
    "calendar_month" -> Icons.Default.CalendarMonth
    "mood" -> Icons.Default.Mood
    "book", "book_2" -> Icons.AutoMirrored.Filled.MenuBook
    "timer" -> Icons.Default.Timer
    "timer_off" -> Icons.Default.TimerOff
    "cake" -> Icons.Default.Cake
    "assessment" -> Icons.Default.Assessment
    "extension" -> Icons.Default.Extension
    "barchart" -> Icons.Default.BarChart
    "favorite" -> Icons.Default.Favorite
    "star" -> Icons.Default.Star
    "home" -> Icons.Default.Home
    "settings" -> Icons.Default.Settings
    "search" -> Icons.Default.Search
    "add" -> Icons.Default.Add
    "delete" -> Icons.Default.Delete
    "edit" -> Icons.Default.Edit
    "close" -> Icons.Default.Close
    "check" -> Icons.Default.Check
    "arrow_back" -> Icons.AutoMirrored.Filled.ArrowBack
    "arrow_forward" -> Icons.AutoMirrored.Filled.ArrowForward
    "more_vert" -> Icons.Default.MoreVert
    "info" -> Icons.Default.Info
    "warning" -> Icons.Default.Warning
    "error" -> Icons.Default.Error
    "refresh" -> Icons.Default.Refresh
    "share" -> Icons.Default.Share
    "lock" -> Icons.Default.Lock
    "visibility" -> Icons.Default.Visibility
    "visibility_off" -> Icons.Default.VisibilityOff
    "school" -> Icons.Default.School
    "flight" -> Icons.Default.Place
    else -> Icons.Default.Widgets
}

