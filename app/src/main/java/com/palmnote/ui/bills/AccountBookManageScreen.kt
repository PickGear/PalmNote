package com.palmnote.ui.bills

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.palmnote.ui.theme.AppIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.app.R
import androidx.hilt.navigation.compose.hiltViewModel
import com.palmnote.data.db.entity.AccountBook
import com.palmnote.data.db.entity.getDisplayName
import com.palmnote.data.db.entity.getDisplayDescription
import com.palmnote.ui.components.*
import com.palmnote.ui.theme.*



@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AccountBookManageScreen(
    onNavigateBack: () -> Unit,
    viewModel: BillViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingBook by remember { mutableStateOf<AccountBook?>(null) }
    var detailBook by remember { mutableStateOf<AccountBook?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var bookToDelete by remember { mutableStateOf<AccountBook?>(null) }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.account_book_manage_title),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editingBook = null; showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = Color.White,
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Filled.Add, stringResource(R.string.account_book_add))
            }
        }
    ) { padding ->
            LazyColumn(
                modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Text(stringResource(R.string.account_book_my_books), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${state.allAccountBooks.size}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.account_book_count_format, state.allAccountBooks.size), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }
            }

            // Bug fix: capture list in local val to avoid IndexOutOfBoundsException
            val books = state.allAccountBooks
            items(books.size, key = { books[it].id }) { index ->
                val book = books[index]
                AccountBookItem(
                    book = book,
                    onClick = { detailBook = book },
                    onToggleHidden = {
                        if (book.isHidden) viewModel.unhideAccountBook(book.id)
                        else viewModel.hideAccountBook(book.id)
                    }
                )
            }

            if (state.allAccountBooks.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.AccountBalance,
                        title = stringResource(R.string.account_book_empty),
                        subtitle = stringResource(R.string.account_book_empty_hint)
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    detailBook?.let { book ->
        AccountBookDetailDialog(
            book = book,
            onEdit = { editingBook = book; showAddDialog = true; detailBook = null },
            onDelete = {
                bookToDelete = book
                showDeleteDialog = true
                detailBook = null
            },
            onSetDefault = { viewModel.setDefaultBook(book.id); detailBook = null },
            onDismiss = { detailBook = null }
        )
    }

    if (showAddDialog) {
        AccountBookEditBottomSheet(
            book = editingBook,
            onSave = { name, icon, color, desc, bookType ->
                // Bug fix: use safe call instead of !! to avoid NullPointerException
                editingBook?.let { book ->
                    viewModel.updateAccountBook(book.copy(name = name, icon = icon, color = color, description = desc, bookType = bookType))
                } ?: viewModel.addAccountBook(name, icon, color, desc, bookType)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    if (showDeleteDialog) {
        bookToDelete?.let { book ->
            AppDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text(stringResource(R.string.account_book_delete_title)) },
                text = { Text(stringResource(R.string.account_book_delete_confirm, book.getDisplayName(context))) },
                confirmButton = {
                    TextButton(onClick = { viewModel.deleteAccountBookWithData(book.id); showDeleteDialog = false }) {
                        Text(stringResource(R.string.delete), color = ErrorLight)
                    }
                },
                dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) } }
            )
        }
    }
}

@Composable
private fun AccountBookItem(
    book: AccountBook,
    onClick: () -> Unit,
    onToggleHidden: () -> Unit
) {
    val context = LocalContext.current
    val bookColor = try {
        Color(android.graphics.Color.parseColor(book.color))
    } catch (_: Exception) { Color.Gray }

    ModuleCard(
        tint = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(bookColor),
                contentAlignment = Alignment.Center
            ) {
                val bookIcon = book.icon.imageVector
                Icon(bookIcon, contentDescription = null, modifier = Modifier.size(24.dp), tint = Color.White)
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        book.getDisplayName(context),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    if (book.isDefault) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = AccentOrange.copy(alpha = 0.1f)
                        ) {
                        Text(stringResource(R.string.bill_default), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall, color = AccentOrange)
                        }
                    }
                }
                if (book.getDisplayDescription(context).isNotEmpty()) {
                    Text(book.getDisplayDescription(context), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            CapsuleSwitch(
                checked = !book.isHidden,
                onCheckedChange = { onToggleHidden() },
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun AccountBookDetailDialog(
    book: AccountBook,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val bookColor = try {
        Color(android.graphics.Color.parseColor(book.color))
    } catch (_: Exception) { Color.Gray }

    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(book.getDisplayName(context)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.large).background(bookColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(book.icon.imageVector, contentDescription = null, modifier = Modifier.size(28.dp), tint = book.icon.tint)
                    }
                    Column {
                        Text(book.getDisplayName(context), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(book.bookType, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
                if (book.getDisplayDescription(context).isNotEmpty()) DetailRow(stringResource(R.string.account_book_desc), book.getDisplayDescription(context))
                DetailRow(stringResource(R.string.bill_type), book.bookType)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!book.isAllBooks) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = ErrorLight) }
                }
                if (!book.isDefault) {
                    TextButton(onClick = { onSetDefault(); onDismiss() }) { Text(stringResource(R.string.account_book_set_default), color = StatusActive) }
                }
                TextButton(onClick = onEdit) { Text(stringResource(R.string.edit), color = AccentOrange) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AccountBookEditBottomSheet(
    book: AccountBook?,
    onSave: (name: String, icon: AppIcon, color: String, description: String, bookType: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(book?.name ?: "") }
    var icon by remember { mutableStateOf(book?.icon ?: AppIcon.MenuBook) }
    var color by remember { mutableStateOf(book?.color ?: "#2D4A3E") }
    var description by remember { mutableStateOf(book?.description ?: "") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var showTemplatePicker by remember { mutableStateOf(book == null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    AppBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Text(
            if (book != null) stringResource(R.string.account_book_edit) else stringResource(R.string.account_book_new),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        if (showTemplatePicker && book == null) {
            Text(stringResource(R.string.account_book_select_type), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AccountBook.BOOK_TEMPLATES.forEach { template ->
                Surface(
                    onClick = {
                        name = template.name; icon = template.icon; color = template.color; description = template.description
                        showTemplatePicker = false
                    },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(shape = CircleShape, color = template.color.toComposeColor(Color.Gray), modifier = Modifier.size(40.dp)) {
                            val templateIcon = template.icon.imageVector
                        Box(contentAlignment = Alignment.Center) { Icon(templateIcon, contentDescription = null, modifier = Modifier.size(24.dp), tint = Color.White) }
                        }
                        Column {
                            Text(template.getDisplayName(context), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(template.getDisplayDescription(context), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            TextButton(onClick = { showTemplatePicker = false }) { Text(stringResource(R.string.account_book_custom)) }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it; nameError = null },
                    label = { Text(stringResource(R.string.account_book_name)) }, modifier = Modifier.weight(1f),
                    isError = nameError != null, singleLine = true
                )
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text(stringResource(R.string.account_book_desc)) }, modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Text(stringResource(R.string.account_book_icon), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
            IconPickerGrid(selectedIcon = icon, onSelected = { newIcon -> icon = newIcon }, modifier = Modifier.height(160.dp), columns = 5)

            Text(stringResource(R.string.account_book_color), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
            ColorPicker(selectedColor = color, onColorSelected = { color = it })

            if (book == null) {
                TextButton(onClick = { showTemplatePicker = true }) { Text(stringResource(R.string.account_book_back_to_template)) }
            }

            val nameRequiredError = stringResource(R.string.account_book_name_required)
            AppSaveButton(
                onClick = {
                    if (name.isBlank()) { nameError = nameRequiredError; return@AppSaveButton }
                    val bookType = book?.bookType ?: AccountBook.BOOK_TEMPLATES.find { it.name == name }?.type ?: "CUSTOM"
                    onSave(name.trim(), icon, color, description.trim(), bookType)
                },
                enabled = name.isNotBlank()
            )
        }
    }
}
