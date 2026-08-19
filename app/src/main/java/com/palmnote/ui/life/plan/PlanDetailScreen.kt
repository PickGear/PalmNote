package com.palmnote.ui.life.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.palmnote.app.R
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.domain.util.DateUtils
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.components.SecondaryTopAppBar
import com.palmnote.ui.components.toComposeColor
import com.palmnote.ui.life.common.ItemDetailViewModel
import com.palmnote.ui.theme.ModuleLife
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun PlanDetailScreen(
    item: LifeItem,
    template: LifeTemplate,
    subtasks: List<LifeItem>,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    viewModel: ItemDetailViewModel
) {
    val context = LocalContext.current
    val planColor = template.color.toComposeColor(ModuleLife)
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val planStart = remember(item.createdAt) { DateUtils.millisToLocalDate(item.createdAt) }
    val planEnd = item.dueDate?.let { DateUtils.millisToLocalDate(it) }
    val archived = item.status == "ARCHIVED"
    val total = subtasks.size
    val doneCount = planDoneCount(subtasks, planStart, planEnd, today)
    val progress = if (total == 0) 0f else doneCount.toFloat() / total
    val allDone = total > 0 && doneCount == total
    val overEnd = planEnd != null && today.isAfter(planEnd) && !allDone
    val streak = streakCount(subtasks, planStart, planEnd, today)
    val todayScheduled = subtasks.filter { occursOn(it, today, planStart, planEnd) }
    val todayDone = todayScheduled.count { isCheckedOn(it, today) }
    val remaining = remainingTasks(subtasks, planStart, planEnd, today)

    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AppDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.life_item_detail_delete_confirm), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.life_item_detail_delete_hint)) },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    Scaffold(
        topBar = {
            SecondaryTopAppBar(
                title = { Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.life_back)) }
                },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, stringResource(R.string.edit)) }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.life_item_more))
                        }
                        DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.life_item_delete)) },
                                onClick = { showMoreMenu = false; showDeleteDialog = true }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .alpha(if (archived) 0.6f else 1f)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            if (archived) {
                ArchivedBadge()
                Spacer(modifier = Modifier.height(8.dp))
            } else if (overEnd) {
                OverEndBanner(
                    end = planEnd,
                    progress = progress,
                    remaining = remaining,
                    onArchive = { viewModel.archiveActive() },
                    onMarkDone = { viewModel.markAllDoneAndArchive() }
                )
                Spacer(modifier = Modifier.height(8.dp))
            } else if (allDone) {
                AllDoneBanner(progress = progress)
                Spacer(modifier = Modifier.height(8.dp))
            }

            PlanHeader(
                context = context,
                planStart = planStart,
                planEnd = planEnd,
                today = today,
                todayDone = todayDone,
                todayTotal = todayScheduled.size,
                streak = streak,
                progress = progress,
                planColor = planColor
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (subtasks.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                    stringResource(R.string.life_plan_no_subtasks),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                }
            } else {
                PlanGroups(
                    subtasks = subtasks,
                    context = context,
                    planColor = planColor,
                    onToggle = { viewModel.toggleSubtask(it) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                PlanDayView(subtasks = subtasks, planStart = planStart, planEnd = planEnd, today = today)
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = planColor)
                ) {
                    Text(stringResource(R.string.life_plan_add_subtask), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.life_plan_edit), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showAddDialog) {
        AddSubtaskDialog(
            zone = zone,
            onDismiss = { showAddDialog = false },
            onAdd = { title, kind, due -> viewModel.addSubtask(title, kind, due); showAddDialog = false }
        )
    }
}

@Composable
private fun ArchivedBadge() {
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
        stringResource(R.string.life_plan_archived),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    }
}

@Composable
private fun OverEndBanner(
    end: LocalDate?,
    progress: Float,
    remaining: List<Pair<String, Int>>,
    onArchive: () -> Unit,
    onMarkDone: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                val endText = end?.let {
                    DateUtils.formatDisplayDate(context, it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
                } ?: ""
                Text(
                    stringResource(R.string.life_plan_overdue_banner, endText, (progress * 100).toInt()),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            if (remaining.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                val remainingItems = remaining.map { (title, n) -> stringResource(R.string.life_plan_times, title, n) }
                val remainingText = stringResource(R.string.life_plan_remaining) + " " + remainingItems.joinToString(" · ")
                Text(
                    remainingText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onArchive, modifier = Modifier.weight(1f).height(36.dp)) {
                    Text(stringResource(R.string.life_plan_archive), fontSize = 12.sp)
                }
                Button(
                    onClick = onMarkDone,
                    modifier = Modifier.weight(1f).height(36.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.life_plan_mark_done), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun AllDoneBanner(progress: Float) {
    val context = LocalContext.current
    val pct = (progress * 100).toInt()
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = ModuleLife.copy(alpha = 0.14f))
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Check, null, tint = ModuleLife, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.life_plan_all_done, pct), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ModuleLife)
        }
    }
}

@Composable
@Suppress("LongMethod")
private fun PlanHeader(
    context: android.content.Context,
    planStart: LocalDate,
    planEnd: LocalDate?,
    today: LocalDate,
    todayDone: Int,
    todayTotal: Int,
    streak: Int,
    progress: Float,
    planColor: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val rangeStart = DateUtils.formatDisplayDate(context, planStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
            val rangeEnd = planEnd?.let {
                DateUtils.formatDisplayDate(context, it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarMonth, null, tint = planColor, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    rangeStart + if (rangeEnd != null) "  -  $rangeEnd" else "",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                if (streak > 0) {
                    Icon(Icons.Default.LocalFireDepartment, null, tint = planColor, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.life_plan_streak, streak),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            if (todayTotal > 0) {
                Text(
                    stringResource(R.string.life_plan_today_progress, todayDone, todayTotal),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = planColor,
                trackColor = planColor.copy(alpha = 0.14f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.life_plan_done_overall, (progress * 100).toInt()),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = planColor
                )
                Spacer(modifier = Modifier.weight(1f))
                if (planEnd != null && today.isAfter(planEnd)) {
                    Text(stringResource(R.string.life_plan_ended), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
} else {
                    Text(
                        stringResource(R.string.life_plan_in_progress),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanGroups(
    subtasks: List<LifeItem>,
    context: android.content.Context,
    planColor: androidx.compose.ui.graphics.Color,
    onToggle: (LifeItem) -> Unit
) {
    val daily = subtasks.filter { it.subtaskKind() == SubtaskKind.DAILY }
    val weekly = subtasks.filter { it.subtaskKind() == SubtaskKind.WEEKLY }
    val monthly = subtasks.filter { it.subtaskKind() == SubtaskKind.MONTHLY }
    val milestone = subtasks.filter { it.subtaskKind() == SubtaskKind.MILESTONE }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        if (daily.isNotEmpty()) {
            GroupSection(stringResource(R.string.life_plan_group_daily))
            daily.forEach { SubtaskRow(it, context, planColor, onToggle = onToggle) }
        }
        if (weekly.isNotEmpty()) {
            GroupSection(stringResource(R.string.life_plan_group_weekly))
            weekly.forEach { SubtaskRow(it, context, planColor, onToggle = onToggle) }
        }
        if (monthly.isNotEmpty()) {
            GroupSection(stringResource(R.string.life_plan_group_monthly))
            monthly.forEach { SubtaskRow(it, context, planColor, onToggle = onToggle) }
        }
        if (milestone.isNotEmpty()) {
            GroupSection(stringResource(R.string.life_plan_group_milestone))
            milestone.forEach { SubtaskRow(it, context, planColor, onToggle = onToggle) }
        }
    }
}

@Composable
private fun GroupSection(title: String) {
    Text(
        title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
    )
}

@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod")
private fun SubtaskRow(
    sub: LifeItem,
    context: android.content.Context,
    planColor: androidx.compose.ui.graphics.Color,
    onToggle: (LifeItem) -> Unit
) {
    val anchor = sub.anchorDate()
    val checked = isCheckedOn(sub, LocalDate.now())
    val kindLine = when (sub.subtaskKind()) {
        SubtaskKind.DAILY -> sub.dueTime?.let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" }
        SubtaskKind.WEEKLY -> anchor?.let { a ->
            stringResource(R.string.life_plan_every_week, a.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()))
        }
        SubtaskKind.MONTHLY -> anchor?.let { a -> stringResource(R.string.life_plan_every_month, a.dayOfMonth) }
        SubtaskKind.MILESTONE -> sub.dueDate?.let { d ->
            stringResource(R.string.life_plan_due, DateUtils.formatDisplayDate(context, d))
        }
    }
    val doneLabel = if (sub.subtaskKind() == SubtaskKind.MILESTONE && sub.status == "COMPLETED") {
        stringResource(R.string.life_plan_done)
    } else {
        null
    }
    val subtitle = listOfNotNull(kindLine, doneLabel).joinToString(" · ")

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).clickable { onToggle(sub) },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = if (checked) planColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(if (checked) planColor else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(50))
                    .border(1.5.dp, if (checked) planColor else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) {
                if (sub.subtaskKind() == SubtaskKind.MILESTONE) {
                    Icon(
                        Icons.Default.Flag,
                        null,
                        tint = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                } else if (checked) {
                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    sub.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (checked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanDayView(
    subtasks: List<LifeItem>,
    planStart: LocalDate,
    planEnd: LocalDate?,
    today: LocalDate
) {
    val first = if (planStart.isAfter(today)) today else if (today.minusDays(13).isAfter(planStart)) today.minusDays(13) else planStart
    val days = buildList {
        var d = first
        while (!d.isAfter(today)) {
            add(d)
            d = d.plusDays(1)
        }
    }
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        GroupSection(stringResource(R.string.life_plan_day_view))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                days.forEach { day ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.width(72.dp)) {
                            Text(
                                buildString {
                                    append(day.monthValue).append('/').append(day.dayOfMonth)
                                    if (day == today) append("  ").append(stringResource(R.string.life_plan_today))
                                },
                                fontSize = 12.sp,
                                fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal,
                                color = if (day == today) ModuleLife else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            subtasks.forEach { sub ->
                                DayMarker(sub = sub, day = day, planStart = planStart, planEnd = planEnd)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayMarker(sub: LifeItem, day: LocalDate, planStart: LocalDate, planEnd: LocalDate?) {
    val scheduled = occursOn(sub, day, planStart, planEnd)
    val checked = scheduled && isCheckedOn(sub, day)
    val milestone = sub.subtaskKind() == SubtaskKind.MILESTONE
    Text(
        when {
            !scheduled -> "-"
            checked && milestone -> "\u2605"
            checked -> "\u2713"
            milestone -> "\u25CF"
            else -> "\u25CB"
        },
        fontSize = 13.sp,
        fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
        color = when {
            !scheduled -> MaterialTheme.colorScheme.outlineVariant
            checked -> ModuleLife
            milestone -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod")
@Composable
fun AddSubtaskDialog(zone: ZoneId, onDismiss: () -> Unit, onAdd: (String, SubtaskKind, Long?) -> Unit) {
    var title by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(SubtaskKind.DAILY) }
    var dueDate by remember { mutableStateOf<LocalDate?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    val kindOptions = listOf(
        SubtaskKind.DAILY to stringResource(R.string.life_plan_group_daily),
        SubtaskKind.WEEKLY to stringResource(R.string.life_plan_group_weekly),
        SubtaskKind.MONTHLY to stringResource(R.string.life_plan_group_monthly),
        SubtaskKind.MILESTONE to stringResource(R.string.life_plan_group_milestone)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.life_plan_add_subtask), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.life_plan_subtask_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    kindOptions.forEach { (key, label) ->
                        FilterChip(
                            selected = kind == key,
                            onClick = { kind = key },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }
                if (kind == SubtaskKind.MILESTONE) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }
                            .padding(10.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            dueDate?.let { stringResource(R.string.life_plan_milestone_on, it.monthValue, it.dayOfMonth) }
                                ?: stringResource(R.string.life_plan_pick_date),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = title.trim()
                if (trimmed.isNotEmpty()) {
                    val dueMillis = if (kind == SubtaskKind.MILESTONE) {
                        dueDate?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
                    } else {
                        null
                    }
                    onAdd(trimmed, kind, dueMillis)
                }
            }) { Text(stringResource(R.string.life_plan_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )

    if (showDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = dueDate?.atStartOfDay(zone)?.toInstant()?.toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { millis ->
                        dueDate = java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) } }
        ) {
            DatePicker(state = dateState)
        }
    }
}
