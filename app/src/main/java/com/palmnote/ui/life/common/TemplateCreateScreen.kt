package com.palmnote.ui.life.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.palmnote.app.R
import com.palmnote.ui.components.SecondaryTopAppBar
import com.palmnote.ui.components.toComposeColor
import androidx.hilt.navigation.compose.hiltViewModel
import com.palmnote.domain.model.FieldType
import com.palmnote.ui.theme.*
import java.util.UUID

data class TemplateField(
    val key: String = UUID.randomUUID().toString().take(8),
    var label: String = "",
    var type: String = "TEXT",
    var required: Boolean = false,
    var options: List<String> = emptyList(),
    var showInCard: Boolean = false,
    var showAsProgress: Boolean = false
)

private val templateFieldsSaver: Saver<List<TemplateField>, List<String>> = Saver(
    save = { fields ->
        fields.map { f ->
            listOf(
                f.key, f.label, f.type, f.required.toString(),
                f.options.joinToString(","), f.showInCard.toString(), f.showAsProgress.toString()
            ).joinToString("\u0001")
        }
    },
    restore = { raw ->
        raw.map { chunk ->
            val parts = chunk.split("\u0001")
            TemplateField(
                key = parts.getOrElse(0) { "" },
                label = parts.getOrElse(1) { "" },
                type = parts.getOrElse(2) { "TEXT" },
                required = parts.getOrElse(3) { "false" }.toBoolean(),
                options = parts.getOrElse(4) { "" }.split(",").filter { it.isNotBlank() },
                showInCard = parts.getOrElse(5) { "false" }.toBoolean(),
                showAsProgress = parts.getOrElse(6) { "false" }.toBoolean()
            )
        }
    }
)

@Suppress("LongMethod", "CyclomaticComplexMethod")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TemplateCreateScreen(
    onBack: () -> Unit,
    onCreated: (Long) -> Unit,
    viewModel: TemplateCreateViewModel = hiltViewModel()
) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var customCategoryInput by rememberSaveable { mutableStateOf("") }
    var selectedIcon by rememberSaveable { mutableStateOf("savings") }
    var selectedColor by rememberSaveable { mutableStateOf("#7C8CF0") }
    var fields by rememberSaveable(stateSaver = templateFieldsSaver) { mutableStateOf(listOf(TemplateField())) }

    val icons = listOf(
        "savings", "shopping_cart", "checklist", "calendar_month", "mood", "book",
        "timer", "cake", "assessment", "barchart", "favorite", "star", "home", "settings", "school", "flight"
    )
    val colors = listOf("#EC407A", "#F07070", "#FF7043", "#FFCA28", "#66BB6A", "#50C890", "#26A69A", "#00ACC1", "#42A5F5", "#5C6BC0", "#AB47BC", "#7C8CF0", "#78909C", "#8D6E63", "#E53935", "#1E88E5")

    val suggestedCategories = listOf(
        stringResource(R.string.life_category_plan),
        stringResource(R.string.life_category_time),
        stringResource(R.string.life_category_record),
        stringResource(R.string.life_category_work),
        stringResource(R.string.life_category_study),
        stringResource(R.string.life_category_health),
        stringResource(R.string.life_category_family)
    )

    val createdId: Long? by viewModel.createdTemplateId.collectAsStateWithLifecycle()
    LaunchedEffect(createdId) {
        createdId?.let { onCreated(it) }
    }

    val canSave = name.isNotBlank() && category.isNotBlank() && fields.any { it.label.isNotBlank() }

    Scaffold(
        topBar = {
            SecondaryTopAppBar(
                title = { Text(stringResource(R.string.life_template_new), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.life_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.createTemplate(
                                name = name.trim(),
                                category = category.trim(),
                                description = description.trim(),
                                icon = selectedIcon,
                                color = selectedColor,
                                fields = fields.filter { it.label.isNotBlank() }
                            )
                        },
                        enabled = canSave,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(stringResource(R.string.save), fontWeight = FontWeight.Medium)
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
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            SectionHeader(stringResource(R.string.life_template_step_basic))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.life_template_name)) },
                placeholder = { Text(stringResource(R.string.life_template_name_hint), fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.life_template_desc)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(stringResource(R.string.life_template_category), fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestedCategories.forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat, fontSize = 12.sp) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = customCategoryInput,
                onValueChange = { customCategoryInput = it },
                label = { Text(stringResource(R.string.life_template_category_custom)) },
                placeholder = { Text(stringResource(R.string.life_template_category_custom_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val text = customCategoryInput.trim()
                        if (text.isNotEmpty()) category = text
                        customCategoryInput = ""
                    }
                )
            )
            if (category.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    stringResource(R.string.life_template_category_selected, category),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(stringResource(R.string.life_template_section_fields))

            fields.forEachIndexed { index, field ->
                FieldEditorCard(
                    index = index,
                    field = field,
                    canDelete = fields.size > 1,
                    onFieldChange = { updated -> fields = fields.toMutableList().also { list -> list[index] = updated } },
                    onDelete = { fields = fields.filterIndexed { i, _ -> i != index } }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            OutlinedButton(
                onClick = { fields = fields + TemplateField() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.life_template_add_field))
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(stringResource(R.string.life_template_select_icon))
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                icons.forEach { icon ->
                    val interactionSource = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selectedIcon == icon) ModuleLife.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .then(
                                if (selectedIcon == icon) Modifier.border(1.5.dp, ModuleLife, RoundedCornerShape(10.dp)) else Modifier
                            )
                            .clickable(interactionSource = interactionSource, indication = null) { selectedIcon = icon },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            iconFromName(icon),
                            null,
                            tint = if (selectedIcon == icon) ModuleLife else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(stringResource(R.string.life_template_select_color))
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                colors.forEach { color ->
                    val interactionSource = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color.toComposeColor(ModuleLife))
                            .then(
                                if (selectedColor == color) {
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                } else {
                                    Modifier
                                }
                            )
                            .clickable(interactionSource = interactionSource, indication = null) { selectedColor = color },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedColor == color) {
                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ModuleLife, modifier = Modifier.padding(bottom = 8.dp))
}

@Suppress("LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldEditorCard(
    index: Int,
    field: TemplateField,
    canDelete: Boolean,
    onFieldChange: (TemplateField) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.life_template_field_index, index + 1),
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                if (canDelete) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, stringResource(R.string.delete), modifier = Modifier.size(16.dp))
                    }
                }
            }
            OutlinedTextField(
                value = field.label,
                onValueChange = { onFieldChange(field.copy(label = it)) },
                label = { Text(stringResource(R.string.life_template_field_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            var expanded by remember { mutableStateOf(false) }
            val typeOptions = fieldTypeOptions()
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = typeOptions[field.type] ?: field.type,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.life_template_field_type)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    typeOptions.entries.forEach { (type, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            enabled = type != "IMAGE",
                            onClick = {
                                onFieldChange(field.copy(type = type))
                                expanded = false
                            }
                        )
                    }
                }
            }

            if (field.type == "SELECT" || field.type == "MULTI_SELECT") {
                Spacer(modifier = Modifier.height(8.dp))
                var optionsText by rememberSaveable(field.key) { mutableStateOf(field.options.joinToString(",")) }
                OutlinedTextField(
                    value = optionsText,
                    onValueChange = {
                        optionsText = it
                        onFieldChange(field.copy(options = it.split(",").map(String::trim).filter(String::isNotEmpty)))
                    },
                    label = { Text(stringResource(R.string.life_template_field_options_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = field.required, onCheckedChange = { onFieldChange(field.copy(required = it)) })
                Text(stringResource(R.string.life_template_field_required), fontSize = 13.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Checkbox(checked = field.showInCard, onCheckedChange = { onFieldChange(field.copy(showInCard = it)) })
                Text(stringResource(R.string.life_template_field_show_in_card), fontSize = 13.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Checkbox(checked = field.showAsProgress, onCheckedChange = { onFieldChange(field.copy(showAsProgress = it)) })
                Text(stringResource(R.string.life_template_field_show_as_progress), fontSize = 13.sp)
            }
        }
    }
}

@Suppress("CyclomaticComplexMethod")
@Composable
private fun fieldTypeOptions(): Map<String, String> {
    val text = stringResource(R.string.life_template_field_text)
    val number = stringResource(R.string.life_template_field_number)
    val date = stringResource(R.string.life_template_field_date)
    val select = stringResource(R.string.life_template_field_select)
    val shortText = stringResource(R.string.life_template_field_short_text)
    val currency = stringResource(R.string.life_template_field_currency)
    val datetime = stringResource(R.string.life_template_field_datetime)
    val richText = stringResource(R.string.life_template_field_rich_text)
    val file = stringResource(R.string.life_template_field_file)
    val time = stringResource(R.string.life_template_field_time)
    val boolean = stringResource(R.string.life_template_field_boolean)
    val multiSelect = stringResource(R.string.life_template_field_multi_select)
    val rating = stringResource(R.string.life_template_field_rating)
    val slider = stringResource(R.string.life_template_field_slider)
    val percentage = stringResource(R.string.life_template_field_percentage)
    val url = stringResource(R.string.life_template_field_url)
    val email = stringResource(R.string.life_template_field_email)
    val phone = stringResource(R.string.life_template_field_phone)
    val color = stringResource(R.string.life_template_field_color)
    val location = stringResource(R.string.life_template_field_location)
    val duration = stringResource(R.string.life_template_field_duration)
    val image = stringResource(R.string.life_template_field_image)
    val options = mutableMapOf<String, String>()
    FieldType.entries.forEach { type ->
        options[type.name] = when (type) {
            FieldType.TEXT -> text
            FieldType.NUMBER -> number
            FieldType.DATE -> date
            FieldType.SELECT -> select
            FieldType.SHORT_TEXT -> shortText
            FieldType.CURRENCY -> currency
            FieldType.DATETIME -> datetime
            FieldType.RICH_TEXT -> richText
            FieldType.FILE -> file
            FieldType.TIME -> time
            FieldType.BOOLEAN -> boolean
            FieldType.MULTI_SELECT -> multiSelect
            FieldType.RATING -> rating
            FieldType.SLIDER -> slider
            FieldType.PERCENTAGE, FieldType.PERCENT -> percentage
            FieldType.URL -> url
            FieldType.EMAIL -> email
            FieldType.PHONE -> phone
            FieldType.COLOR -> color
            FieldType.LOCATION -> location
            FieldType.DURATION -> duration
            FieldType.IMAGE -> image
        }
    }
    return options
}
