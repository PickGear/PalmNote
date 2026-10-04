package com.palmnote.ui.life

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.app.R
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.ui.components.SecondaryTopAppBar
import com.palmnote.ui.theme.ModuleLife
import com.palmnote.ui.theme.TypeScale
import java.time.LocalDate

/**
 * 首页三类清单的完整视图。
 *
 * 三种模式共用首页 [LifeCalendarViewModel] 已派生好的同源数据，不新增 SQL、不复制过滤条件：
 * 首页折叠的是哪一批，这里展示的就是哪一批；完成 / 推迟 / 删除后两边同时刷新。
 */
@Composable
internal fun LifeFullListScreen(
    mode: LifeFullListMode,
    dateEpochDay: Long,
    onBack: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    calendarVm: LifeCalendarViewModel = hiltViewModel()
) {
    val boardRows by calendarVm.boardRows.collectAsStateWithLifecycle()
    val overdueItems by calendarVm.overdueItems.collectAsStateWithLifecycle()
    val unscheduledItems by calendarVm.unscheduledItems.collectAsStateWithLifecycle()

    val selectedDate = remember(dateEpochDay) {
        if (dateEpochDay == 0L) LocalDate.now() else LocalDate.ofEpochDay(dateEpochDay)
    }
    val today = LocalDate.now()
    val title = when (mode) {
        LifeFullListMode.OVERDUE -> stringResource(R.string.life_board_overdue)
        LifeFullListMode.AGENDA -> if (selectedDate == LocalDate.now()) {
            stringResource(R.string.life_board_agenda_today)
        } else {
            stringResource(R.string.life_board_agenda_on)
        }
        LifeFullListMode.UNSCHEDULED -> stringResource(R.string.life_home_todo)
    }

    // 首页只展示 selectedDate 的逾期项，并被当天已排进今日安排的条目剔除。
    // 完整页沿用同一口径，否则会显示别的日期或凭空多出几条。
    // 两处都走 [scheduledOn]（含「每年重复」滚动），与首页看板严格同源。
    val selectedDayScheduledIds = remember(boardRows, selectedDate, today) {
        boardRows.scheduledOn(selectedDate, today).map { it.itemId }.toSet()
    }
    val selectedDayRows = remember(boardRows, selectedDate, today) {
        boardRows.scheduledOn(selectedDate, today)
    }
    val rows: List<LifeItemDao.LifeBoardItemRow> = remember(
        mode,
        boardRows,
        overdueItems,
        unscheduledItems,
        selectedDayScheduledIds,
        selectedDayRows
    ) {
        when (mode) {
            LifeFullListMode.OVERDUE -> overdueItems.filter { it.itemId !in selectedDayScheduledIds }
            LifeFullListMode.AGENDA -> sortScheduledRows(selectedDayRows)
            LifeFullListMode.UNSCHEDULED -> unscheduledItems
        }
    }
    val dueTimeByItem = remember(selectedDayRows) {
        selectedDayRows.associate { row -> row.itemId to row.dueTime }
    }

    var pendingAction by remember { mutableStateOf<FullListPendingAction?>(null) }

    Scaffold(
        topBar = {
            SecondaryTopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold, color = ModuleLife) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.life_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (rows.isEmpty()) {
            FullListEmptyState(
                mode = mode,
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                item {
                    FullListSummary(
                        mode = mode,
                        selectedDate = selectedDate,
                        total = rows.size,
                        modifier = Modifier.padding(top = 8.dp, bottom = 10.dp)
                    )
                }
                items(rows, key = { it.itemId }) { row ->
                    FullListRow(
                        item = row,
                        mode = mode,
                        dueTime = dueTimeByItem[row.itemId],
                        onOpenDetail = { onOpenDetail(row.itemId) },
                        onToggle = { calendarVm.toggleItemStatus(row.itemId) },
                        onReschedule = { calendarVm.rescheduleToToday(row.itemId) },
                        onRequestSwipeComplete = { toCompleted ->
                            pendingAction = FullListPendingAction.Complete(row.itemId, toCompleted)
                        },
                        onRequestDelete = { pendingAction = FullListPendingAction.Delete(row.itemId) }
                    )
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    pendingAction?.let { pending ->
        SwipeConfirmDialog(
            pending = pending.toPendingSwipe(),
            onConfirm = {
                when (pending) {
                    is FullListPendingAction.Delete -> calendarVm.deleteItem(pending.itemId)
                    // 用 setItemCompleted 而不是 toggle：对话框已按 toCompleted 承诺了方向，
                    // toggle 会在「手势与确认之间状态被改过」时做出与文案相反的结果
                    is FullListPendingAction.Complete -> calendarVm.setItemCompleted(pending.itemId, pending.toCompleted)
                }
                // 完整页也用手势 → 首页教学卡同样"学会即隐"
                calendarVm.dismissSwipeTip()
                pendingAction = null
            },
            onDismiss = { pendingAction = null }
        )
    }
}

/** 完整页沿用首页的滑动语义，确认弹窗仍复用 [SwipeConfirmDialog]。 */
private sealed interface FullListPendingAction {
    data class Delete(val itemId: Long) : FullListPendingAction
    data class Complete(val itemId: Long, val toCompleted: Boolean) : FullListPendingAction
}

private fun FullListPendingAction.toPendingSwipe(): PendingSwipe = when (this) {
    is FullListPendingAction.Delete -> PendingSwipe.Delete(itemId)
    is FullListPendingAction.Complete -> PendingSwipe.Complete(itemId, toCompleted)
}

@Composable
private fun FullListSummary(
    mode: LifeFullListMode,
    selectedDate: LocalDate,
    total: Int,
    modifier: Modifier = Modifier
) {
    val detail = when (mode) {
        // 逾期与待安排语义不同，不再共用「共 N 条」（审计 #31）
        LifeFullListMode.OVERDUE -> stringResource(R.string.life_board_overdue_total, total)
        LifeFullListMode.AGENDA -> stringResource(
            R.string.life_board_agenda_date,
            selectedDate.monthValue,
            selectedDate.dayOfMonth,
            weekdayShortRes(selectedDate.dayOfWeek)
        )
        LifeFullListMode.UNSCHEDULED -> stringResource(R.string.life_home_todo_total, total)
    }
    Text(
        detail,
        fontSize = TypeScale.bodyS,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
private fun FullListRow(
    item: LifeItemDao.LifeBoardItemRow,
    mode: LifeFullListMode,
    dueTime: Int?,
    onOpenDetail: () -> Unit,
    onToggle: () -> Unit,
    onReschedule: () -> Unit,
    onRequestSwipeComplete: (Boolean) -> Unit,
    onRequestDelete: () -> Unit
) {
    val completed = item.status == "COMPLETED"
    FullListSwipeRow(
        onDelete = onRequestDelete,
        onSwipeComplete = { onRequestSwipeComplete(!completed) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .clickable(onClick = onOpenDetail)
                .padding(end = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .fillMaxHeight()
                    .clickable(role = Role.Checkbox, onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (completed) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    stringResource(R.string.life_item_toggle_complete),
                    tint = if (completed) ModuleLife else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            CategoryChip(item.category)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                item.title,
                fontSize = TypeScale.bodyRead,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                color = if (completed) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            when (mode) {
                LifeFullListMode.AGENDA -> {
                    if (dueTime != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            formatDueTime(dueTime),
                            fontSize = TypeScale.labelS,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                LifeFullListMode.UNSCHEDULED -> {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        item.templateName,
                        fontSize = TypeScale.labelS,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                LifeFullListMode.OVERDUE -> {
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = onReschedule,
                        enabled = !completed,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text(stringResource(R.string.life_board_reschedule), fontSize = TypeScale.bodyS)
                    }
                }
            }
        }
    }
}

/**
 * 选中日的安排行。
 *
 * 完整页直接复用同一份 `boardRows` 投影，并用同一行的 `dueTime`
 * 排序与展示，避免依赖异步偏好校准后的 ViewModel 状态。
 * 日期过滤统一走 [scheduledOn]（LifeBoardRows.kt：含「每年重复」滚动），
 * 此前这里的私有 `filterByDueDate` 只认原始 `dueDate`，与首页口径漂移过一次。
 */

/** 完整页与首页使用同一排序口径：有时间的按 `dueTime` 升序，**无时间的置顶**（随时可做优先）。 */
internal fun sortScheduledRows(
    rows: List<LifeItemDao.LifeBoardItemRow>
): List<LifeItemDao.LifeBoardItemRow> =
    rows.sortedWith(compareBy(nullsFirst(naturalOrder())) { row -> row.dueTime })

private fun formatDueTime(dueTime: Int): String {
    val hour = (dueTime / 60).toString().padStart(2, '0')
    val minute = (dueTime % 60).toString().padStart(2, '0')
    return "$hour:$minute"
}

@Composable
private fun FullListSwipeRow(
    onDelete: () -> Unit,
    onSwipeComplete: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    false
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    onSwipeComplete()
                    false
                }
                SwipeToDismissBoxValue.Settled -> true
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = { SwipeTaskBackground(dismissState.dismissDirection) },
        content = content
    )
}

@Composable
private fun FullListEmptyState(mode: LifeFullListMode, modifier: Modifier = Modifier) {
    val title = when (mode) {
        LifeFullListMode.OVERDUE -> stringResource(R.string.life_board_overdue_empty)
        LifeFullListMode.AGENDA -> stringResource(R.string.life_board_empty)
        LifeFullListMode.UNSCHEDULED -> stringResource(R.string.life_home_todo_empty)
    }
    // 与首页空态同一套视觉语言（图标 + 标题），不再是裸文本
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        LifeEmptyState(
            icon = when (mode) {
                LifeFullListMode.OVERDUE -> Icons.Outlined.EventBusy
                LifeFullListMode.AGENDA -> Icons.AutoMirrored.Outlined.EventNote
                LifeFullListMode.UNSCHEDULED -> Icons.AutoMirrored.Outlined.EventNote
            },
            title = title,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
