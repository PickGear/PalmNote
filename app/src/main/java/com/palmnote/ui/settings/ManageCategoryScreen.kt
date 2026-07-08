package com.palmnote.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.palmnote.ui.components.CompactTopAppBar
import com.palmnote.ui.components.ModuleCard
import com.palmnote.R
import com.palmnote.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageCategoryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToWallet: () -> Unit,
    onNavigateToAccountBook: () -> Unit,
    onNavigateToCategory: () -> Unit
) {
    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.settings_category_manage),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_navigate_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { SectionHeader(stringResource(R.string.settings_account_category), Icons.Outlined.Category, InfoBlue) }
            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    SettingsMenuItem(icon = Icons.Outlined.AccountBalanceWallet, title = stringResource(R.string.settings_wallet_manage), subtitle = stringResource(R.string.settings_wallet_manage_subtitle), tint = AccentOrange, onClick = onNavigateToWallet)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                    SettingsMenuItem(icon = Icons.Outlined.Book, title = stringResource(R.string.settings_bill_manage), subtitle = stringResource(R.string.settings_bill_manage_subtitle), tint = PrimaryGreenLight, onClick = onNavigateToAccountBook)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                    SettingsMenuItem(icon = Icons.Outlined.Category, title = stringResource(R.string.settings_category_manage), subtitle = stringResource(R.string.settings_category_manage_subtitle), tint = InfoBlue, onClick = onNavigateToCategory)
                }
            }
        }
    }
}
