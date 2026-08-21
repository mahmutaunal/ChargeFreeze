package com.alpwarestudio.chargefreeze.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.os.BatteryManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alpwarestudio.chargefreeze.R
import com.alpwarestudio.chargefreeze.domain.BatterySnapshot
import com.alpwarestudio.chargefreeze.domain.FreezeState

@Composable
fun HomeScreen(vm: MainViewModel, onSettings: () -> Unit) {
    val battery by vm.batteryState.collectAsStateWithLifecycle()
    val freeze by vm.freeze.collectAsStateWithLifecycle()
    val supported = vm.controller.isSupported()
    val hasPermission = vm.controller.hasWritePermission()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Header(supported = supported, hasPermission = hasPermission, active = freeze.active, onSettings = onSettings)

            AnimatedContent(targetState = freeze.active, label = "freeze-state") { active ->
                if (active) {
                    ActiveContent(battery = battery, freeze = freeze, onStop = vm::disable)
                } else {
                    IdleContent(
                        battery = battery,
                        supported = supported,
                        hasPermission = hasPermission,
                        onEnable = vm::enable
                    )
                }
            }

            freeze.message?.let {
                StatusMessage(text = it, error = true)
            }
        }
    }
}

@Composable
private fun Header(supported: Boolean, hasPermission: Boolean, active: Boolean, onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "ChargeFreeze",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        IconButton(onClick = onSettings) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.settings),
                tint = if (active || (supported && hasPermission))
                    MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun IdleContent(
    battery: BatterySnapshot,
    supported: Boolean,
    hasPermission: Boolean,
    onEnable: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BatteryRing(level = battery.level)

        MetricCard(
            icon = Icons.Default.Usb,
            title = if (battery.plugged) connectionTitle(battery) else stringResource(R.string.not_connected),
            subtitle = stringResource(R.string.power_source)
        )

        MetricCard(
            icon = Icons.Default.BatteryStd,
            title = if (battery.isCharging) stringResource(R.string.charging) else stringResource(R.string.not_charging),
            subtitle = stringResource(R.string.charging_status)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SmallMetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Thermostat,
                label = stringResource(R.string.temperature),
                value = "%.1f °C".format(battery.temperatureC)
            )
            SmallMetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Favorite,
                label = stringResource(R.string.health),
                value = batteryHealthLabel(battery.health)
            )
        }

        when {
            !supported -> StatusMessage(stringResource(R.string.unsupported), error = false)
            !hasPermission -> PermissionCard()
            else -> EnableButton(onClick = onEnable)
        }
    }
}

@Composable
private fun ActiveContent(
    battery: BatterySnapshot,
    freeze: FreezeState,
    onStop: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AcUnit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(31.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        stringResource(R.string.freeze_active),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.freeze_started),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        DetailTable(
            rows = listOf(
                DetailRow(Icons.Default.Schedule, stringResource(R.string.started_at), formatStartTime(freeze.startedAtMillis)),
                DetailRow(Icons.Default.Usb, stringResource(R.string.start_level), "${freeze.startLevel ?: battery.level}%"),
                DetailRow(Icons.Default.BatteryStd, stringResource(R.string.current_level), "${battery.level}%"),
                DetailRow(Icons.Default.Bolt, stringResource(R.string.charge_status), stringResource(if (battery.isCharging) R.string.charging else R.string.paused)),
                DetailRow(Icons.Default.Usb, stringResource(R.string.usb_power), stringResource(if (battery.plugged) R.string.connected else R.string.disconnected)),
                DetailRow(Icons.Default.AcUnit, stringResource(R.string.strategy), stringResource(R.string.moving_threshold))
            )
        )

        Button(
            onClick = onStop,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        ) {
            Icon(Icons.Default.PauseCircle, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.stop_freeze), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BatteryRing(level: Int) {
    val progress = level.coerceIn(0, 100) / 100f
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 12.dp.toPx()
                val inset = stroke / 2f
                drawArc(
                    color = track,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = primary,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$level",
                        fontSize = 60.sp,
                        lineHeight = 62.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Text(
                        text = "%",
                        fontSize = 27.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Text(
                    stringResource(R.string.battery_level),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun MetricCard(icon: ImageVector, title: String, subtitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SmallMetricCard(modifier: Modifier, icon: ImageVector, label: String, value: String) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EnableButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Icon(Icons.Default.AcUnit, contentDescription = null)
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.enable_freeze), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PermissionCard() {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.permission_required), fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.permission_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = {
                val text = context.getString(R.string.permission_command)
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("ADB", text))
            }) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.copy_command))
            }
        }
    }
}

@Composable
private fun StatusMessage(text: String, error: Boolean) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (error) MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private data class DetailRow(val icon: ImageVector, val label: String, val value: String)

@Composable
private fun DetailTable(rows: List<DetailRow>) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column {
            rows.forEachIndexed { index, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(row.icon, contentDescription = null, modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text(row.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(row.value, fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
                }
                if (index != rows.lastIndex) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                    )
                }
            }
        }
    }
}

@Composable
private fun connectionTitle(battery: BatterySnapshot): String = when (battery.source) {
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

@Composable
private fun formatStartTime(millis: Long?): String = if (millis == null) {
    stringResource(R.string.this_session)
} else {
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))
}
