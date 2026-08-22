package com.alpwarestudio.chargefreeze.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.net.toUri
import com.alpwarestudio.chargefreeze.R
import com.alpwarestudio.chargefreeze.data.AppPreferences
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
    val permissionCommand = stringResource(R.string.permission_command)
    var language by remember { mutableStateOf(preferences.languageMode) }
    var usbPrompt by remember { mutableStateOf(preferences.startOnUsbConnect) }
    var detailedNotification by remember { mutableStateOf(preferences.showPersistentNotification) }
    var margin by remember { mutableIntStateOf(preferences.freezeMargin) }
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    ScreenContainer(title = stringResource(R.string.settings), onBack = onBack) {
        SettingsGroup(stringResource(R.string.general)) {
            ValueRow(
                Icons.Default.Language,
                stringResource(R.string.language),
                languageLabel(language),
                onClick = { dialog = SettingsDialog.LANGUAGE }
            )
            GroupDivider()
            ValueRow(
                Icons.Default.Palette,
                stringResource(R.string.appearance),
                themeLabel(themeMode),
                stringResource(R.string.appearance_subtitle),
                onClick = { dialog = SettingsDialog.THEME }
            )
            GroupDivider()
            SwitchRow(
                Icons.Default.Usb,
                stringResource(R.string.start_on_usb_connect),
                stringResource(R.string.start_on_usb_connect_subtitle),
                usbPrompt
            ) {
                usbPrompt = it
                preferences.startOnUsbConnect = it
                if (it) requestNotificationPermission()
            }
            GroupDivider()
            SwitchRow(
                Icons.Default.NotificationsActive,
                stringResource(R.string.show_persistent_notification),
                stringResource(R.string.persistent_notification_subtitle),
                detailedNotification
            ) {
                detailedNotification = it
                preferences.showPersistentNotification = it
                if (it) requestNotificationPermission()
            }
        }

        SettingsGroup(stringResource(R.string.freeze_section)) {
            ValueRow(
                Icons.Default.Tune,
                stringResource(R.string.freeze_margin),
                "$margin%",
                stringResource(R.string.freeze_margin_subtitle),
                onClick = { dialog = SettingsDialog.MARGIN }
            )
            GroupDivider()
            ValueRow(
                Icons.Default.Route,
                stringResource(R.string.strategy),
                stringResource(R.string.moving_threshold),
                onClick = null
            )
        }

        SettingsGroup(stringResource(R.string.access)) {
            val granted = vm.controller.hasWritePermission()
            ValueRow(
                if (granted) Icons.Default.VerifiedUser else Icons.Default.LockOpen,
                stringResource(R.string.write_secure_settings),
                stringResource(if (granted) R.string.granted else R.string.required),
                stringResource(if (granted) R.string.permission_granted_subtitle else R.string.permission_required_subtitle),
                valueColor = if (granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                onClick = if (granted) null else {
                    {
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("ADB", permissionCommand))
                    }
                }
            )
            if (!granted) {
                GroupDivider()
                ActionRow(Icons.Default.ContentCopy, stringResource(R.string.copy_adb_command)) {
                    context.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("ADB", permissionCommand))
                }
            }
        }

        SettingsGroup(stringResource(R.string.support_about)) {
            ActionRow(Icons.Default.BugReport, stringResource(R.string.diagnostics), onDiagnostics)
            GroupDivider()
            LinkRow(Icons.Default.Code, stringResource(R.string.project_github), PROJECT_URL)
            GroupDivider()
            LinkRow(Icons.Default.Language, stringResource(R.string.alpware_studio), ALPWARE_URL)
            GroupDivider()
            LinkRow(Icons.Default.PrivacyTip, stringResource(R.string.privacy_policy), PRIVACY_URL)
            GroupDivider()
            LinkRow(Icons.Default.Security, stringResource(R.string.security_policy), SECURITY_URL)
            GroupDivider()
            LinkRow(Icons.Default.Description, stringResource(R.string.open_source_license), LICENSE_URL)
            GroupDivider()
            ValueRow(
                Icons.Default.Info,
                stringResource(R.string.version),
                remember(context) {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
                },
                onClick = null
            )
        }

        Text(
            stringResource(R.string.settings_footer),
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    when (dialog) {
        SettingsDialog.LANGUAGE -> ChoiceDialog(
            stringResource(R.string.language),
            LanguageMode.entries.map { it to languageLabel(it) },
            language,
            { dialog = null }
        ) {
            language = it
            dialog = null
            onLanguageChanged(it)
        }
        SettingsDialog.THEME -> ChoiceDialog(
            stringResource(R.string.appearance),
            ThemeMode.entries.map { it to themeLabel(it) },
            themeMode,
            { dialog = null }
        ) {
            dialog = null
            onThemeChanged(it)
        }
        SettingsDialog.MARGIN -> ChoiceDialog(
            stringResource(R.string.freeze_margin),
            listOf(1, 2, 3, 5).map { it to "$it%" },
            margin,
            { dialog = null }
        ) {
            margin = it
            preferences.freezeMargin = it
            dialog = null
        }
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
    val diagnostics = remember(battery, freeze, supported, permission, protection) {
        buildString {
            appendLine("ChargeFreeze diagnostics")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Firmware build: ${firmwareBuild()}")
            appendLine("Backend: WRITE_SECURE_SETTINGS")
            appendLine("Permission: ${if (permission) "Granted" else "Required"}")
            appendLine("Samsung support: ${if (supported) "Supported" else "Unsupported"}")
            appendLine("Freeze active: ${freeze.active}")
            appendLine("Battery: ${battery.level}%")
            appendLine("Charging: ${battery.isCharging}")
            appendLine("Power source: ${battery.source}")
            appendLine("Protection mode: ${protection?.mode ?: "—"}")
            appendLine("Protection threshold: ${protection?.threshold ?: "—"}")
            appendLine("Recharge level: ${protection?.rechargeLevel ?: "—"}")
        }
    }

    ScreenContainer(title = stringResource(R.string.diagnostics), onBack = onBack) {
        SettingsGroup(null) {
            DiagnosticRow(stringResource(R.string.device), "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}", accent = true)
            GroupDivider()
            DiagnosticRow(stringResource(R.string.android), "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            GroupDivider()
            DiagnosticRow(stringResource(R.string.firmware_build), firmwareBuild())
            GroupDivider()
            DiagnosticRow(stringResource(R.string.backend), "WRITE_SECURE_SETTINGS")
            GroupDivider()
            DiagnosticRow(
                stringResource(R.string.permission),
                stringResource(if (permission) R.string.granted else R.string.required),
                badge = true,
                positive = permission
            )
            GroupDivider()
            DiagnosticRow(
                stringResource(R.string.samsung_support),
                stringResource(if (supported) R.string.supported else R.string.unsupported_short),
                badge = true,
                positive = supported
            )
            GroupDivider()
            DiagnosticRow(stringResource(R.string.strategy), stringResource(R.string.moving_threshold))
            GroupDivider()
            DiagnosticRow(stringResource(R.string.protection_threshold), protection?.threshold?.let { "$it%" } ?: "—")
            GroupDivider()
            DiagnosticRow(stringResource(R.string.recharge_level), protection?.rechargeLevel?.let { "$it%" } ?: "—")
            GroupDivider()
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
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.copy_diagnostics))
        }
    }
}

@Composable
private fun ScreenContainer(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            TopBar(title, onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content
            )
        }
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), modifier = Modifier.size(21.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SettingsGroup(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (title != null) {
            Text(
                title,
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            content = content
        )
    }
}

@Composable
private fun ValueRow(
    icon: ImageVector,
    title: String,
    value: String,
    subtitle: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = if (subtitle == null) 13.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LeadingIcon(icon)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, textAlign = TextAlign.End)
        if (onClick != null) {
            Spacer(Modifier.width(3.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LeadingIcon(icon)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LeadingIcon(icon)
        Spacer(Modifier.width(11.dp))
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, url: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) {
                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LeadingIcon(icon)
        Spacer(Modifier.width(11.dp))
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(17.dp)
        )
    }
}

@Composable
private fun LeadingIcon(icon: ImageVector) {
    Box(
        Modifier.size(30.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 55.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
    accent: Boolean = false,
    badge: Boolean = false,
    positive: Boolean = false,
    trailing: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!badge) Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
        when {
            badge -> {
                val color = if (positive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                Text(
                    value,
                    modifier = Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    color = color,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            trailing != null -> Text(trailing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
        shape = RoundedCornerShape(20.dp),
        title = { Text(title) },
        text = {
            Column {
                choices.forEach { (value, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(value) }.padding(vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, modifier = Modifier.weight(1f))
                        if (value == selected) {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
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

private fun firmwareBuild(): String = Build.DISPLAY.takeIf { it.isNotBlank() } ?: "—"

private enum class SettingsDialog { LANGUAGE, THEME, MARGIN }

private const val PROJECT_URL = "https://github.com/mahmutaunal/ChargeFreeze"
private const val ALPWARE_URL = "https://alpwarestudio.com"
private const val PRIVACY_URL = "$PROJECT_URL/blob/main/PRIVACY.md"
private const val SECURITY_URL = "$PROJECT_URL/blob/main/SECURITY.md"
private const val LICENSE_URL = "$PROJECT_URL/blob/main/LICENSE"
