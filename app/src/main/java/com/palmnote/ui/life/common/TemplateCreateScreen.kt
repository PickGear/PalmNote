package com.palmnote.ui.life.common

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.palmnote.R
import com.palmnote.ui.components.SecondaryTopAppBar
import com.palmnote.ui.components.toComposeColor
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.ui.theme.*
import kotlinx.serialization.json.*
import java.util.UUID

data class TemplateField(
    val key: String = UUID.randomUUID().toString().take(8),
    var label: String = "",
    var type: String = "TEXT",
    var required: Boolean = false,
    var options: List<String> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateCreateScreen(
    onBack: () -> Unit,
    onCreated: (Long) -> Unit,
    viewModel: TemplateCreateViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    var step by remember { mutableIntStateOf(0) }
    var templateType by remember { mutableStateOf("PLAN") }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("EditNote") }
    var selectedColor by remember { mutableStateOf("#7C8CF0") }
    var fields by remember { mutableStateOf(listOf(TemplateField())) }
    var selectedLayout by remember { mutableStateOf("LIST") }

    val icons = listOf("EditNote", "CheckCircle", "Savings", "ShoppingCart", "Flight", "MenuBook", "School", "Timer", "CalendarMonth", "Favorite", "Cake", "Notifications", "AutoStories", "Mood", "BarChart", "Settings")
    val colors = listOf("#EC407A", "#F07070", "#FF7043", "#FFCA28", "#66BB6A", "#50C890", "#26A69A", "#00ACC1", "#42A5F5", "#5C6BC0", "#AB47BC", "#7C8CF0", "#78909C", "#8D6E63", "#E53935", "#1E88E5")

    val createdId: Long? by viewModel.createdTemplateId.collectAsStateWithLifecycle()
    LaunchedEffect(createdId) {
        createdId?.let { onCreated(it) }
    }

    Scaffold(
        topBar = {
            SecondaryTopAppBar(
                title = { Text(if (step == 0) stringResource(R.string.life_template_step_type) else if (step == 1) stringResource(R.string.life_template_step_basic) else if (step == 2) stringResource(R.string.life_template_step_fields) else stringResource(R.string.life_template_step_layout), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { if (step > 0) step-- else onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.life_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp)) {
            LinearProgressIndicator(progress = { (step + 1) / 4f }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), color = ModuleLife)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.life_template_step_format, step + 1), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(16.dp))

            AnimatedContent(targetState = step, transitionSpec = { fadeIn() + slideInHorizontally() togetherWith fadeOut() + slideOutHorizontally() }) { currentStep ->
                when (currentStep) {
                    0 -> TypeStep(selectedType = templateType, onSelect = { templateType = it })
                    1 -> BasicInfoStep(name = name, onNameChange = { name = it }, description = description, onDescChange = { description = it }, selectedIcon = selectedIcon, onIconSelect = { selectedIcon = it }, selectedColor = selectedColor, onColorSelect = { selectedColor = it }, icons = icons, colors = colors)
                    2 -> FieldStep(fields = fields, onFieldsChange = { fields = it })
                    3 -> LayoutStep(selectedLayout = selectedLayout, onSelect = { selectedLayout = it })
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (step < 3) step++
                    else {
                        viewModel.createTemplate(
                            name = name,
                            category = templateType,
                            description = description,
                            icon = selectedIcon,
                            color = selectedColor,
                            fields = fields,
                            layout = selectedLayout
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ModuleLife),
                enabled = if (step == 0) true else if (step == 1) name.isNotBlank() else true
            ) {
                Text(if (step < 3) stringResource(R.string.life_template_next) else stringResource(R.string.life_template_create), fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun TypeStep(selectedType: String, onSelect: (String) -> Unit) {
    val types = listOf(
        Triple("PLAN", stringResource(R.string.life_template_type_plan), stringResource(R.string.life_template_type_plan_desc)),
        Triple("TIME", stringResource(R.string.life_template_type_time), stringResource(R.string.life_template_type_time_desc)),
        Triple("RECORD", stringResource(R.string.life_template_type_record), stringResource(R.string.life_template_type_record_desc))
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        types.forEach { (key, title, subtitle) ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(key) },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = if (selectedType == key) ModuleLife.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface),
                border = if (selectedType == key) ButtonDefaults.outlinedButtonBorder(enabled = true).copy(1.dp, brush = androidx.compose.ui.graphics.SolidColor(ModuleLife)) else null
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(48.dp).background(if (selectedType == key) ModuleLife.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Icon(if (key == "PLAN") Icons.Default.Star else if (key == "TIME") Icons.Default.CalendarMonth else Icons.Default.AutoStories, null, tint = if (selectedType == key) ModuleLife else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(title, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                        Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun BasicInfoStep(name: String, onNameChange: (String) -> Unit, description: String, onDescChange: (String) -> Unit, selectedIcon: String, onIconSelect: (String) -> Unit, selectedColor: String, onColorSelect: (String) -> Unit, icons: List<String>, colors: List<String>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { OutlinedTextField(value = name, onValueChange = onNameChange, label = { Text(stringResource(R.string.life_template_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(value = description, onValueChange = onDescChange, label = { Text(stringResource(R.string.life_template_desc)) }, modifier = Modifier.fillMaxWidth()) }
        item { Text(stringResource(R.string.life_template_select_icon), fontWeight = FontWeight.Medium, fontSize = 14.sp) }
        item { LazyVerticalGrid(columns = GridCells.Fixed(8), modifier = Modifier.height(120.dp)) { items(icons) { icon -> Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(if (selectedIcon == icon) ModuleLife.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onIconSelect(icon) }, contentAlignment = Alignment.Center) { Icon(iconFromName(icon), null, tint = if (selectedIcon == icon) ModuleLife else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) } } } }
        item { Text(stringResource(R.string.life_template_select_color), fontWeight = FontWeight.Medium, fontSize = 14.sp) }
        item { LazyVerticalGrid(columns = GridCells.Fixed(6), modifier = Modifier.height(80.dp)) { items(colors) { color -> Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(color.toComposeColor(ModuleLife)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onColorSelect(color) }.then(if (selectedColor == color) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier), contentAlignment = Alignment.Center) { if (selectedColor == color) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) } } } }
    }
}

@Composable
private fun FieldStep(fields: List<TemplateField>, onFieldsChange: (List<TemplateField>) -> Unit) {
    val fieldTypes = listOf("TEXT" to stringResource(R.string.life_template_field_text), "NUMBER" to stringResource(R.string.life_template_field_number), "DATE" to stringResource(R.string.life_template_field_date), "SELECT" to stringResource(R.string.life_template_field_select), "DECIMAL" to stringResource(R.string.life_template_field_decimal))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(fields.size) { index ->
            Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.life_template_field_index, index + 1), fontWeight = FontWeight.Medium, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        if (fields.size > 1) IconButton(onClick = { onFieldsChange(fields.toMutableList().apply { removeAt(index) }) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp)) }
                    }
                    OutlinedTextField(value = fields[index].label, onValueChange = { onFieldsChange(fields.toMutableList().apply { this[index] = this[index].copy(label = it) }) }, label = { Text(stringResource(R.string.life_template_field_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        fieldTypes.forEach { (key, label) -> FilterChip(selected = fields[index].type == key, onClick = { onFieldsChange(fields.toMutableList().apply { this[index] = this[index].copy(type = key) }) }, label = { Text(label, fontSize = 11.sp) }) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = fields[index].required, onCheckedChange = { onFieldsChange(fields.toMutableList().apply { this[index] = this[index].copy(required = it) }) })
                        Text(stringResource(R.string.life_template_field_required), fontSize = 13.sp)
                    }
                }
            }
        }
        item { OutlinedButton(onClick = { onFieldsChange(fields + TemplateField()) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(modifier = Modifier.width(8.dp)); Text(stringResource(R.string.life_template_add_field)) } }
    }
}

@Composable
private fun LayoutStep(selectedLayout: String, onSelect: (String) -> Unit) {
    val layouts = listOf(
        Triple("LIST", stringResource(R.string.life_template_layout_list), Icons.Default.Menu),
        Triple("TIMELINE", stringResource(R.string.life_template_layout_timeline), Icons.Default.Timeline),
        Triple("CALENDAR", stringResource(R.string.life_template_layout_calendar), Icons.Default.CalendarMonth),
        Triple("STATS", stringResource(R.string.life_template_layout_stats), Icons.Default.BarChart)
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        layouts.forEach { (key, label, icon) ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onSelect(key) }, shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = if (selectedLayout == key) ModuleLife.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = if (selectedLayout == key) ModuleLife else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(label, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                }
            }
        }
    }
}
