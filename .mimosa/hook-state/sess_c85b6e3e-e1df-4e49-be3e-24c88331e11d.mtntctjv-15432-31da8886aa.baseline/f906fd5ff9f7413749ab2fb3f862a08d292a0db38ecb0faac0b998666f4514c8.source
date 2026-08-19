package com.palmnote.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp = 16.dp,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp)
) {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )
    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 200f, 0f),
        end = Offset(translateAnim, 0f)
    )
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .clip(shape)
            .background(brush)
    )
}

@Composable
fun LifeScreenSkeleton() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Stats row skeleton
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { SkeletonBox(modifier = Modifier.weight(1f), height = 72.dp, shape = RoundedCornerShape(14.dp)) }
        }
        Spacer(modifier = Modifier.height(20.dp))
        // Section header skeleton
        SkeletonBox(width = 120.dp, height = 20.dp)
        Spacer(modifier = Modifier.height(12.dp))
        // Card skeletons
        repeat(3) {
            SkeletonBox(height = 72.dp, shape = RoundedCornerShape(14.dp))
            Spacer(modifier = Modifier.height(8.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        SkeletonBox(width = 120.dp, height = 20.dp)
        Spacer(modifier = Modifier.height(12.dp))
        repeat(2) {
            SkeletonBox(height = 72.dp, shape = RoundedCornerShape(14.dp))
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
