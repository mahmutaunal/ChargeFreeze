package com.alpwarestudio.chargefreeze.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alpwarestudio.chargefreeze.R
import com.alpwarestudio.chargefreeze.data.AppPreferences
import com.alpwarestudio.chargefreeze.data.FreezeStrategy
import com.alpwarestudio.chargefreeze.data.LanguageMode
import com.alpwarestudio.chargefreeze.data.ThemeMode
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    preferences: AppPreferences,
    themeMode: ThemeMode,
    onThemeChanged: (ThemeMode) -> Unit,
    onLanguageChanged: (LanguageMode) -> Unit,
    onBack: () -> Unit,
    onDiagnostics: () -> Unit
) {
    val context = LocalContext.current
    var language by remember { mutableStateOf(preferences.languageMode) }
    var usbStart by remember { mutableStateOf(preferences.startOnUsbConnect) }
    var persistentNotification by remember { mutableStateOf(preferences.showPersistentNotification) }
    var margin by remember { mutableIntStateOf(preferences.freezeMargin) }
    var strategy by remember { mutableStateOf(preferences.strategy) }
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            SettingsTopBar(title = stringResource(R.string.settings), onBack = onBack)

            SettingsSection(title = stringResource(R.string.general)) {
                SettingsValueRow(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.language),
                    value = languageLabel(language),
                    onClick = { dialog = SettingsDialog.LANGUAGE }
                )
                SettingsValueRow(
                    icon = Icons.Default.Palette,
                    title = stringResource(R.string.appearance),
                    subtitle = stringResource(R.string.appearance_subtitle),
                    value = themeLabel(themeMode),
                    onClick = { dialog = SettingsDialog.THEME }
                )
                SettingsSwitchRow(
                    icon = Icons.Default.Usb,
                    title = stringResource(R.string.start_on_usb_connect),
                    subtitle = stringResource(R.string.start_on_usb_connect_subtitle),
                    checked = usbStart,
                    onCheckedChange = {
                        usbStart = it
                        preferences.startOnUsbConnect = it
                        if (it) requestNotificationPermissionIfNeeded()
                    }
                )
                SettingsSwitchRow(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.show_persistent_notification),
                    subtitle = stringResource(R.string.persistent_notification_subtitle),
                    checked = persistentNotification,
                    onCheckedChange = {
                        persistentNotification = it
                        preferences.showPersistentNotification = it
                        if (it) requestNotificationPermissionIfNeeded()
                    }
                )
            }

            SettingsSection(title = stringResource(R.string.freeze_section)) {
                SettingsValueRow(
                    title = stringResource(R.string.freeze_margin),
                    subtitle = stringResource(R.string.freeze_margin_subtitle),
                    value = "$margin%",
                    onClick = { dialog = SettingsDialog.MARGIN }
                )
                SettingsValueRow(
                    title = stringResource(R.string.strategy),
                    subtitle = stringResource(R.string.strategy_subtitle),
                    value = strategyLabel(strategy),
                    onClick = { dialog = SettingsDialog.STRATEGY }
                )
            }

            SettingsSection(title = stringResource(R.string.access)) {
                val granted = vm.controller.hasWritePermission()
                SettingsValueRow(
                    icon = Icons.Default.LockOpen,
                    title = stringResource(R.string.write_secure_settings),
                    subtitle = stringResource(if (granted) R.string.permission_granted_subtitle else R.string.permission_required_subtitle),
                    value = stringResource(if (granted) R.string.granted else R.string.required),
                    onClick = if (granted) null else {
                        {
                            val command = context.getString(R.string.permission_command)
                            context.getSystemService(ClipboardManager::class.java)
                                .setPrimaryClip(ClipData.newPlainText("ADB", command))
                        }
                    }
                )
                if (!granted) {
                    SettingsActionRow(
                        icon = Icons.Default.ContentCopy,
                        title = stringResource(R.string.copy_adb_command),
                        onClick = {
                            val command = context.getString(R.string.permission_command)
                            context.getSystemService(ClipboardManager::class.java)
                                .setPrimaryClip(ClipData.newPlainText("ADB", command))
                        }
                    )
                }
            }

            SettingsSection(title = stringResource(R.string.support_about)) {
                SettingsActionRow(Icons.Default.BugReport, stringResource(R.string.diagnostics), onDiagnostics)
                SettingsLinkRow(stringResource(R.string.project_github), PROJECT_URL)
                SettingsLinkRow(stringResource(R.string.alpware_studio), ALPWARE_URL)
                SettingsLinkRow(stringResource(R.string.privacy_policy), PRIVACY_URL)
                SettingsLinkRow(stringResource(R.string.security_policy), SECURITY_URL)
                SettingsLinkRow(stringResource(R.string.open_source_license), LICENSE_URL)
                SettingsValueRow(
                    title = stringResource(R.string.version),
                    value = remember(context) {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
                    },
                    onClick = null
                )
            }

            Spacer(Modifier.height(22.dp))
            Text(
                text = stringResource(R.string.settings_footer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))
        }
    }

    when (dialog) {
        SettingsDialog.LANGUAGE -> ChoiceDialog(
            title = stringResource(R.string.language),
            choices = LanguageMode.entries.map { it to languageLabel(it) },
            selected = language,
            onDismiss = { dialog = null },
            onSelect = {
                language = it
                dialog = null
                onLanguageChanged(it)
            }
        )
        SettingsDialog.THEME -> ChoiceDialog(
            title = stringResource(R.string.appearance),
            choices = ThemeMode.entries.map { it to themeLabel(it) },
            selected = themeMode,
            onDismiss = { dialog = null },
            onSelect = {
                dialog = null
                onThemeChanged(it)
            }
        )
        SettingsDialog.MARGIN -> ChoiceDialog(
            title = stringResource(R.string.freeze_margin),
            choices = listOf(1, 2, 3, 5).map { it to "$it%" },
            selected = margin,
            onDismiss = { dialog = null },
            onSelect = {
                margin = it
                preferences.freezeMargin = it
                dialog = null
            }
        )
        SettingsDialog.STRATEGY -> ChoiceDialog(
            title = stringResource(R.string.strategy),
            choices = listOf(FreezeStrategy.AUTO, FreezeStrategy.MOVING_THRESHOLD).map { it to strategyLabel(it) },
            selected = strategy,
            onDismiss = { dialog = null },
            onSelect = {
                strategy = it
                preferences.strategy = it
                dialog = null
            }
        )
        null -> Unit
    }
}

@Composable
fun DiagnosticsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val battery by vm.batteryState.collectAsStateWithLifecycle()
    val freeze by vm.freeze.collectAsStateWithLifecycle()
    val supported = vm.controller.isSupported()
    val permission = vm.controller.hasWritePermission()
    val protection = vm.controller.readOriginalState()
    val prefs = remember { AppPreferences(context) }
    val diagnostics = remember(battery, freeze, supported, permission, protection) {
        buildString {
            appendLine("ChargeFreeze diagnostics")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("One UI: ${readOneUiVersion()}")
            appendLine("Backend: WRITE_SECURE_SETTINGS")
            appendLine("Permission: ${if (permission) "Granted" else "Required"}")
            appendLine("Samsung support: ${if (supported) "Supported" else "Unsupported"}")
            appendLine("Configured strategy: ${prefs.strategy.value}")
            appendLine("Freeze active: ${freeze.active}")
            appendLine("Battery: ${battery.level}%")
            appendLine("Charging: ${battery.isCharging}")
            appendLine("Power source: ${battery.source}")
            appendLine("Protection mode: ${protection?.mode ?: "—"}")
            appendLine("Protection threshold: ${protection?.threshold ?: "—"}")
            appendLine("Recharge level: ${protection?.rechargeLevel ?: "—"}")
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            SettingsTopBar(title = stringResource(R.string.diagnostics), onBack = onBack)

            Card(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                DiagnosticRow(stringResource(R.string.device), "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}", accent = true)
                DiagnosticRow(stringResource(R.string.android), "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                DiagnosticRow(stringResource(R.string.one_ui), readOneUiVersion())
                DiagnosticRow(stringResource(R.string.backend), "WRITE_SECURE_SETTINGS")
                DiagnosticRow(stringResource(R.string.permission), stringResource(if (permission) R.string.granted else R.string.required))
                DiagnosticRow(stringResource(R.string.samsung_support), stringResource(if (supported) R.string.supported else R.string.unsupported_short), positive = supported)
                DiagnosticRow(stringResource(R.string.strategy), strategyLabel(prefs.strategy))
                DiagnosticRow(stringResource(R.string.protection_threshold), protection?.threshold?.let { "$it%" } ?: "—")
                DiagnosticRow(stringResource(R.string.recharge_level), protection?.rechargeLevel?.let { "$it%" } ?: "—")
                DiagnosticRow(
                    stringResource(R.string.last_action),
                    if (freeze.active) stringResource(R.string.freeze_active) else stringResource(R.string.read_settings),
                    trailing = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date())
                )
            }

            OutlinedButton(
                onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("ChargeFreeze diagnostics", diagnostics))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .height(58.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(Modifier.size(10.dp))
                Text(stringResource(R.string.copy_diagnostics), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SettingsTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        content()
    }
}

@Composable
private fun SettingsValueRow(
    title: String,
    value: String,
    icon: ImageVector? = null,
    subtitle: String? = null,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = if (subtitle == null) 17.dp else 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector? = null,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
}

@Composable
private fun SettingsActionRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(14.dp))
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
}

@Composable
private fun SettingsLinkRow(title: String, url: String) {
    val context = LocalContext.current
    SettingsValueRow(
        title = title,
        value = "↗",
        icon = Icons.Default.OpenInNew,
        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    )
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
    accent: Boolean = false,
    positive: Boolean = false,
    trailing: String? = null
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal
                )
                if (!positive) {
                    Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
            }
            if (positive) {
                Text(
                    value,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else if (trailing != null) {
                Text(trailing, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    choices: List<Pair<T, String>>,
    selected: T,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                choices.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, modifier = Modifier.weight(1f))
                        if (value == selected) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun languageLabel(mode: LanguageMode): String = when (mode) {
    LanguageMode.SYSTEM -> stringResource(R.string.system_default)
    LanguageMode.ENGLISH -> "English"
    LanguageMode.TURKISH -> "Türkçe"
}

@Composable
private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> stringResource(R.string.system_default)
    ThemeMode.LIGHT -> stringResource(R.string.light)
    ThemeMode.DARK -> stringResource(R.string.dark)
}

@Composable
private fun strategyLabel(strategy: FreezeStrategy): String = when (strategy) {
    FreezeStrategy.AUTO -> stringResource(R.string.auto_recommended)
    FreezeStrategy.NATIVE_HOLD -> stringResource(R.string.native_hold)
    FreezeStrategy.MOVING_THRESHOLD -> stringResource(R.string.moving_threshold)
}

private fun readOneUiVersion(): String = runCatching {
    val systemProperties = Class.forName("android.os.SystemProperties")
    val get = systemProperties.getMethod("get", String::class.java)
    val version = get.invoke(null, "ro.build.version.oneui") as? String
    version?.takeIf { it.isNotBlank() } ?: "—"
}.getOrDefault("—")

private enum class SettingsDialog { LANGUAGE, THEME, MARGIN, STRATEGY }

private const val PROJECT_URL = "https://github.com/mahmutaunal/ChargeFreeze"
private const val ALPWARE_URL = "https://alpwarestudio.com"
private const val PRIVACY_URL = "$PROJECT_URL/blob/main/PRIVACY.md"
private const val SECURITY_URL = "$PROJECT_URL/blob/main/SECURITY.md"
private const val LICENSE_URL = "$PROJECT_URL/blob/main/LICENSE"
