package com.palmnote.ui.life.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.palmnote.app.R
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.components.SecondaryTopAppBar
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.ui.theme.Spacing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 动态表单字段值（String→String）的 rememberSaveable Saver */
private val stringMapSaver = mapSaver<MutableMap<String, String>>(
    save = { map -> map.entries.associate { it.key to it.value } },
    restore = { map -> mutableStateMapOf<String, String>().apply { map.forEach { (k, v) -> put(k, v as? String ?: "") } } }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicFormScreen(
    template: LifeTemplate,
    existingItem: LifeItem? = null,
    onSave: (title: String, fieldsData: String) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    viewModel: CreateItemViewModel? = null
) {
    val isEdit = existingItem != null
    var title by rememberSaveable { mutableStateOf(existingItem?.title ?: "") }
    val fieldValues = rememberSaveable(saver = stringMapSaver) { mutableStateMapOf<String, String>() }
    val json = remember { Json { ignoreUnknownKeys = true } }
    var saving by rememberSaveable { mutableStateOf(false) }
    var saveSuccess by rememberSaveable { mutableStateOf(false) }
    var saveError by rememberSaveable { mutableStateOf(false) }
    var saveErrorMessage by rememberSaveable { mutableStateOf("") }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var initialized by rememberSaveable { mutableStateOf(false) }
    val vmState by viewModel?.uiState?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(CreateItemUiState()) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(vmState.saveError) {
        if (vmState.saveError != null) {
            saving = false
            saveError = true
            saveErrorMessage = vmState.saveError!!
        }
    }

    val fields: List<FieldDef> = remember {
        try {
            val arr = json.decodeFromString<JsonArray>(template.fieldsConfig)
            arr.map { el ->
                val obj = el.jsonObject
                FieldDef(
                    key = obj["key"]?.jsonPrimitive?.content ?: "",
                    label = obj["label"]?.jsonPrimitive?.content ?: "",
                    type = obj["type"]?.jsonPrimitive?.content ?: "TEXT",
                    required = obj["required"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
                    unit = obj["unit"]?.jsonPrimitive?.content ?: "",
                    defaultValue = obj["defaultValue"]?.jsonPrimitive?.content ?: "",
                    options = try { (obj["options"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList() } catch (_: Exception) { emptyList() },
                    min = obj["min"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                    max = obj["max"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 100.0,
                    step = obj["step"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 1.0
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    val originalFieldValues = remember { mutableStateMapOf<String, String>() }

    val hasUnsavedChanges by remember {
        derivedStateOf {
            title != (existingItem?.title ?: "") ||
            fieldValues.any { (key, value) ->
                if (isEdit) {
                    value != (originalFieldValues[key] ?: "")
                } else {
                    val field = fields.find { it.key == key }
                    field != null && value != field.defaultValue
                }
            }
        }
    }

    LaunchedEffect(existingItem) {
        originalFieldValues.clear()
        if (existingItem != null) {
            try {
                val data = json.decodeFromString<JsonObject>(existingItem.fieldsData)
                data.forEach { (key, value) ->
                    if (value is JsonPrimitive) {
                        val strValue = value.content
                        originalFieldValues[key] = strValue
                        // 仅在首次进入时预填 DB 值；旋转/进程恢复后保留 rememberSaveable 的编辑
                        if (!initialized) fieldValues[key] = strValue
                    }
                }
            } catch (e: Exception) { android.util.Log.w("DynamicForm", "fieldsData parse failed", e) }
        }
        initialized = true
    }

    BackHandler(enabled = hasUnsavedChanges && !saving) {
        showDiscardDialog = true
    }

    if (showDiscardDialog) {
        AppDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.life_confirm_discard), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.life_confirm_discard_hint)) },
            confirmButton = { TextButton(onClick = { showDiscardDialog = false; onBack() }) { Text(stringResource(R.string.life_discard), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDiscardDialog = false }) { Text(stringResource(R.string.life_continue_editing)) } }
        )
    }

    val handleBack = {
        if (hasUnsavedChanges && !saving) showDiscardDialog = true else onBack()
    }

    Scaffold(
        topBar = {
            SecondaryTopAppBar(
                title = { Text(if (isEdit) stringResource(R.string.life_edit_title, template.displayName()) else stringResource(R.string.life_create_title, template.displayName()), fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = handleBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.life_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = Spacing.md).verticalScroll(rememberScrollState())) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            OutlinedTextField(
                value = title, onValueChange = { title = it; saveError = false },
                placeholder = { Text(stringResource(R.string.life_input_title)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), focusedBorderColor = MaterialTheme.colorScheme.outline)
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            fields.forEach { field ->
                FieldInput(field, fieldValues[field.key] ?: field.defaultValue, onValueChange = { fieldValues[field.key] = it; saveError = false })
                Spacer(modifier = Modifier.height(Spacing.sm))
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
            val canSave = title.isNotBlank() && fields.filter { it.required }.all { (fieldValues[it.key] ?: it.defaultValue).isNotBlank() }
            Button(
                onClick = {
                    if (!saving && !saveSuccess) {
                        saving = true
                        saveError = false
                        saveErrorMessage = ""
                        try {
                            val jsonObj = JsonObject(fieldValues.entries.associate { it.key to JsonPrimitive(it.value) })
                            onSave(title, jsonObj.toString())
                            saving = false
                            saveSuccess = true
                        } catch (e: Exception) {
                            saveError = true
                            saveErrorMessage = e.message ?: context.getString(R.string.life_form_save_failed)
                            saving = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.medium,
                enabled = canSave && !saving && !saveSuccess
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else if (saveSuccess) {
                    Icon(Icons.Default.Check, context.getString(R.string.life_form_save_success), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(context.getString(R.string.life_form_saved), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                } else {
                    Text(if (isEdit) context.getString(R.string.life_form_save_changes) else context.getString(R.string.life_form_save), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
            if (saveError) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(saveErrorMessage, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(Spacing.xl))
        }
    }
}
