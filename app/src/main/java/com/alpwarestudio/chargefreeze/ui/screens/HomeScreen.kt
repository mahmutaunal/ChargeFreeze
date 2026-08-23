package com.alpwarestudio.chargefreeze.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.os.BatteryManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alpwarestudio.chargefreeze.R
import com.alpwarestudio.chargefreeze.domain.BatterySnapshot
import com.alpwarestudio.chargefreeze.domain.FreezeState
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(vm: MainViewModel, onSettings: () -> Unit) {
    val battery by vm.batteryState.collectAsStateWithLifecycle()
    val freeze by vm.freeze.collectAsStateWithLifecycle()
    val supported = vm.controller.isSupported()
    val hasPermission = vm.controller.hasWritePermission()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HomeHeader(active = freeze.active, onSettings = onSettings)
            AnimatedContent(targetState = freeze.active, label = "freeze-content") { active ->
                if (active) {
                    ActiveSession(battery, freeze, vm::disable)
                } else {
                    IdleDashboard(
                        battery = battery,
                        supported = supported,
                        hasPermission = hasPermission,
                        recoveryRequired = freeze.recoveryRequired,
                        onEnable = vm::enable,
                        onRestore = vm::restore
                    )
                }
            }
            freeze.message?.let { StatusNotice(it, isError = freeze.recoveryRequired) }
        }
    }
}

@Composable
private fun HomeHeader(active: Boolean, onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("ChargeFreeze", style = MaterialTheme.typography.headlineSmall)
            if (active) {
                Text(
                    stringResource(R.string.freeze_active),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (active) {
            Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(42.dp)) {
            Icon(
                Icons.Default.Settings,
                stringResource(R.string.settings),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun IdleDashboard(
    battery: BatterySnapshot,
    supported: Boolean,
    hasPermission: Boolean,
    recoveryRequired: Boolean,
    onEnable: () -> Unit,
    onRestore: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BatteryGauge(battery.level)
        StatusCard(
            Icons.Default.Usb,
            if (battery.plugged) connectionTitle(battery) else stringResource(R.string.not_connected),
            stringResource(R.string.power_source),
            MaterialTheme.colorScheme.primary
        )
        StatusCard(
            if (battery.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
            stringResource(if (battery.isCharging) R.string.charging else R.string.not_charging),
            stringResource(R.string.charging_status),
            if (battery.isCharging) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CompactMetric(
                Modifier.weight(1f), Icons.Default.Thermostat,
                stringResource(R.string.temperature), "%.1f °C".format(battery.temperatureC),
                MaterialTheme.colorScheme.tertiary
            )
            CompactMetric(
                Modifier.weight(1f), Icons.Default.Favorite,
                stringResource(R.string.health), batteryHealthLabel(battery.health),
                MaterialTheme.colorScheme.secondary
            )
        }
        when {
            recoveryRequired -> OutlineAction(
                stringResource(R.string.restore_original_settings),
                Icons.Default.WarningAmber,
                onRestore
            )
            !supported -> StatusNotice(stringResource(R.string.unsupported), false)
            !hasPermission -> PermissionPanel()
            else -> FilledAction(
                stringResource(R.string.enable_freeze),
                Icons.Default.AcUnit,
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.onPrimary,
                onEnable
            )
        }
    }
}

@Composable
private fun ActiveSession(battery: BatterySnapshot, freeze: FreezeState, onStop: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ActiveBanner(freeze)
        DetailPanel(
            listOf(
                DetailItem(Icons.Default.Schedule, stringResource(R.string.started_at), formatStartTime(freeze.startedAtMillis)),
                DetailItem(Icons.Default.Usb, stringResource(R.string.start_level), "${freeze.startLevel ?: battery.level}%"),
                DetailItem(Icons.Default.BatteryStd, stringResource(R.string.current_level), "${battery.level}%"),
                DetailItem(
                    Icons.Default.Bolt,
                    stringResource(R.string.charge_status),
                    stringResource(if (battery.isCharging) R.string.charging else R.string.paused),
                    if (battery.isCharging) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                ),
                DetailItem(
                    Icons.Default.Usb,
                    stringResource(R.string.usb_power),
                    stringResource(if (battery.plugged) R.string.connected else R.string.disconnected)
                ),
                DetailItem(
                    Icons.Default.AcUnit,
                    stringResource(R.string.strategy),
                    stringResource(R.string.moving_threshold),
                    MaterialTheme.colorScheme.tertiary
                )
            )
        )
        FilledAction(
            stringResource(R.string.stop_freeze),
            Icons.Default.PauseCircle,
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.onError,
            onStop
        )
    }
}

@Composable
private fun ActiveBanner(freeze: FreezeState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.large)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp).background(
                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                CircleShape
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AcUnit,
                null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                stringResource(R.string.freeze_active),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                "${stringResource(R.string.freeze_started)} • ${formatStartTime(freeze.startedAtMillis)}",
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun BatteryGauge(level: Int) {
    val progress by animateFloatAsState(
        level.coerceIn(0, 100) / 100f,
        tween(650),
        label = "battery-progress"
    )
    val track = MaterialTheme.colorScheme.outlineVariant
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(178.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 9.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    track, -90f, 360f, false, Offset(inset, inset), arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    Brush.sweepGradient(listOf(primary, secondary, primary)),
                    -90f, 360f * progress, false, Offset(inset, inset), arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$level",
                        fontSize = 46.sp,
                        lineHeight = 49.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Text("%", fontSize = 21.sp, modifier = Modifier.padding(bottom = 5.dp))
                }
                Text(
                    stringResource(R.string.battery_level),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun StatusCard(icon: ImageVector, title: String, subtitle: String, accent: Color) {
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(15.dp)),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(34.dp).background(accent.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CompactMetric(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color
) {
    Card(
        modifier = modifier.height(88.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(15.dp)),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    value,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private data class DetailItem(
    val icon: ImageVector,
    val label: String,
    val value: String,
    val accent: Color? = null
)

@Composable
private fun DetailPanel(rows: List<DetailItem>) {
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(17.dp)),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    row.icon, null,
                    tint = row.accent ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(11.dp))
                Text(row.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    row.value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = row.accent ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
            if (index < rows.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun FilledAction(
    text: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Icon(icon, null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(9.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun OutlineAction(text: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(15.dp))
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PermissionPanel() {
    val context = LocalContext.current
    val command = stringResource(R.string.permission_command)
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(15.dp)),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.permission_required), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.permission_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(11.dp))
                    .clickable(role = Role.Button) {
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("ADB", command))
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.ContentCopy, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text(stringResource(R.string.copy_command), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun StatusNotice(text: String, isError: Boolean) {
    val accent = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .border(1.dp, accent.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.WarningAmber, null, tint = accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = accent, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun connectionTitle(snapshot: BatterySnapshot): String = when (snapshot.source) {
    "USB" -> stringResource(R.string.usb_connected)
    "AC" -> stringResource(R.string.ac_connected)
    "Wireless" -> stringResource(R.string.wireless_connected)
    else -> stringResource(R.string.connected)
}

@Composable
private fun batteryHealthLabel(health: Int): String = when (health) {
    BatteryManager.BATTERY_HEALTH_GOOD -> stringResource(R.string.health_good)
    BatteryManager.BATTERY_HEALTH_OVERHEAT -> stringResource(R.string.health_overheat)
    BatteryManager.BATTERY_HEALTH_DEAD -> stringResource(R.string.health_dead)
    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> stringResource(R.string.health_over_voltage)
    BatteryManager.BATTERY_HEALTH_COLD -> stringResource(R.string.health_cold)
    else -> stringResource(R.string.health_unknown)
}

private fun formatStartTime(millis: Long?): String =
    millis?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) } ?: "—"
