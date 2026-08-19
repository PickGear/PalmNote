package com.palmnote.domain.model

import androidx.compose.runtime.Stable

@Stable
data class SubscriptionDueItem(
    val id: Long,
    val title: String,
    val priceText: String,
    val daysLeft: Int,
    val cycle: String
)
