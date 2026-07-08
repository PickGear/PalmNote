package com.palmnote

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.lock.AppLockManager
import com.palmnote.ui.lock.AppLockScreen
import com.palmnote.ui.lock.AppLockState
import com.palmnote.ui.navigation.PalmNoteNavHost
import com.palmnote.ui.theme.LocalSwitchColor
import com.palmnote.ui.theme.PalmNoteTheme
import com.palmnote.ui.theme.PrimaryGreenLight
import androidx.compose.ui.res.stringResource
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import com.palmnote.R

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    @Inject
    lateinit var appLockManager: AppLockManager

    private val lockObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_STOP) {
            if (appLockManager.isLockEnabled() && appLockManager.hasPin()) {
                appLockManager.lock()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        ProcessLifecycleOwner.get().lifecycle.addObserver(lockObserver)

        if (appLockManager.isLockEnabled() && appLockManager.hasPin()) {
            appLockManager.lock()
        }

        setContent {
            val themeMode by preferencesManager.themeMode.collectAsState(initial = "SYSTEM")
            val switchColorHex by preferencesManager.switchColor.collectAsState(initial = "#2D4A3E")
            val isDarkTheme = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }
            val switchColor = try { Color(android.graphics.Color.parseColor(switchColorHex)) } catch (_: Exception) { Color(0xFF2D4A3E) }
            val lockState by appLockManager.lockState.collectAsState()
            val privacyAgreed by preferencesManager.privacyAgreed.collectAsState(initial = false)
            val showPrivacyDialog = !privacyAgreed
            val scope = rememberCoroutineScope()

            PalmNoteTheme(darkTheme = isDarkTheme) {
                CompositionLocalProvider(LocalSwitchColor provides switchColor) {
                    if (showPrivacyDialog) {
                        var showPolicy by remember { mutableStateOf(false) }
                        var showTerms by remember { mutableStateOf(false) }

                        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .systemBarsPadding()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Spacer(Modifier.height(48.dp))

                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryGreenLight.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.Spa,
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp),
                                        tint = PrimaryGreenLight
                                    )
                                }

                                Spacer(Modifier.height(20.dp))

                                Text(
                                    stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )

                                Spacer(Modifier.height(24.dp))

                                Text(
                                    stringResource(R.string.privacy_dialog_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )

                                Spacer(Modifier.height(12.dp))

                                Text(
                                    stringResource(R.string.privacy_dialog_text),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Start
                                )

                                Spacer(Modifier.height(24.dp))

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                Spacer(Modifier.height(20.dp))

                                Text(
                                    stringResource(R.string.privacy_dialog_view_links),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Start
                                )

                                Spacer(Modifier.height(8.dp))

                                OutlinedButton(
                                    onClick = { showPolicy = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Outlined.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.about_privacy_policy))
                                }

                                Spacer(Modifier.height(8.dp))

                                OutlinedButton(
                                    onClick = { showTerms = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.about_terms_of_service))
                                }

                                Spacer(Modifier.height(8.dp))

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                Spacer(Modifier.height(24.dp))

                                Button(
                                    onClick = {
                                        scope.launch { preferencesManager.setPrivacyAgreed(true) }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Text(
                                        stringResource(R.string.privacy_dialog_agree),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }

                                Spacer(Modifier.height(12.dp))

                                TextButton(
                                    onClick = { finishAffinity() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        stringResource(R.string.privacy_dialog_disagree),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(Modifier.height(24.dp))
                            }
                        }

                        if (showPolicy) {
                            FullDocumentOverlay(
                                title = stringResource(R.string.about_privacy_policy),
                                lines = com.palmnote.ui.settings.getPrivacyPolicyLines(),
                                onBack = { showPolicy = false }
                            )
                        }

                        if (showTerms) {
                            FullDocumentOverlay(
                                title = stringResource(R.string.about_terms_of_service),
                                lines = com.palmnote.ui.settings.getTermsOfServiceLines(),
                                onBack = { showTerms = false }
                            )
                        }
                    } else {
                        if (Build.VERSION.SDK_INT >= 33) {
                            val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                            LaunchedEffect(Unit) { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        }

                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            when (lockState) {
                                is AppLockState.Locked, is AppLockState.NeedSetup -> {
                                    AppLockScreen(
                                        appLockManager = appLockManager,
                                        onUnlocked = { appLockManager.unlock() }
                                    )
                                }
                                is AppLockState.Unlocked -> {
                                    PalmNoteNavHost()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lockObserver)
    }
}

@Composable
private fun FullDocumentOverlay(title: String, lines: List<String>, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                lines.forEach { line ->
                    val isZh = java.util.Locale.getDefault().language == "zh"
                    val isHeading = if (isZh) {
                        listOf("一、", "二、", "三、", "四、", "五、", "六、", "七、", "八、", "九、", "十").any { line.startsWith(it) }
                    } else {
                        line.matches(Regex("^\\d+\\..*"))
                    } || line.endsWith(title)
                    Text(
                        text = line,
                        style = if (isHeading) MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
