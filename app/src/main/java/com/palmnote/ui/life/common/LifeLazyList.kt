package com.palmnote.ui.life.common

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeLazyList(
    state: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
    verticalArrangement: androidx.compose.foundation.layout.Arrangement.Vertical = Arrangement.spacedBy(0.dp),
    bottomPadding: Int = 80,
    content: LazyListScope.() -> Unit
) {
    val scope = rememberCoroutineScope()

    Box(modifier = modifier) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize(), verticalArrangement = verticalArrangement) {
            item { Spacer(modifier = Modifier.height(4.dp)) }
            content()
            item { Spacer(modifier = Modifier.height(bottomPadding.dp)) }
        }

        val showTopButton by remember {
            derivedStateOf { state.firstVisibleItemIndex > 3 }
        }
        if (showTopButton) {
            SmallFloatingActionButton(
                onClick = { scope.launch { state.animateScrollToItem(0) } },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Icon(Icons.Default.KeyboardArrowUp, stringResource(com.palmnote.R.string.back_to_top), modifier = Modifier.size(20.dp))
            }
        }
    }
}