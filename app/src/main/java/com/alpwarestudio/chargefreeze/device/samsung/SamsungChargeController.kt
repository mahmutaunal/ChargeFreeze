package com.alpwarestudio.chargefreeze.device.samsung

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.alpwarestudio.chargefreeze.data.AppPreferences
import com.alpwarestudio.chargefreeze.domain.ChargeController
import com.alpwarestudio.chargefreeze.domain.OriginalBatteryProtection

/**
 * Samsung backend discovered on recent One UI builds.
 *
 * This intentionally uses Samsung's existing Battery Protection settings instead of writing
 * kernel sysfs nodes. Samsung can change these undocumented keys between firmware releases,
 * therefore every write is verified and all original values are restored when the session ends.
 */
class SamsungChargeController(private val context: Context) : ChargeController {
    override val name = "Samsung Battery Protection"

    private val resolver get() = context.contentResolver
    private val modeKey = "protect_battery"
    private val thresholdKey = "battery_protection_threshold"
    private val rechargeKey = "battery_protection_recharge_level"

    override fun isSupported(): Boolean =
        Build.MANUFACTURER.equals("samsung", ignoreCase = true) &&
                Settings.Global.getString(resolver, thresholdKey) != null

    override fun hasWritePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED

    override fun readOriginalState(): OriginalBatteryProtection? = runCatching {
        OriginalBatteryProtection(
            mode = Settings.Global.getInt(resolver, modeKey),
            threshold = Settings.Global.getInt(resolver, thresholdKey),
            rechargeLevel = Settings.Global.getInt(resolver, rechargeKey)
        )
    }.getOrNull()

    override fun beginFreeze(level: Int): Result<Int> = runCatching {
        check(isSupported()) { "Unsupported Samsung firmware" }
        check(hasWritePermission()) { "WRITE_SECURE_SETTINGS is not granted" }
        // Keep a small margin below the current level. This avoids immediate charge oscillation.
        val target = (level - AppPreferences(context).freezeMargin).coerceIn(MIN_THRESHOLD, 100)
        writeVerified(modeKey, MODE_MAXIMUM)
        writeVerified(thresholdKey, target)
        target
    }

    override fun maintainFreeze(level: Int, currentThreshold: Int): Result<Int> = runCatching {
        // Moving-threshold fallback: before the battery crosses the active threshold, move it down.
        val desired = (level - AppPreferences(context).freezeMargin).coerceAtLeast(MIN_THRESHOLD)
        if (level <= currentThreshold + 1 && desired < currentThreshold) {
            writeVerified(thresholdKey, desired)
            desired
        } else currentThreshold
    }

    override fun restore(state: OriginalBatteryProtection): Result<Unit> = runCatching {
        writeVerified(thresholdKey, state.threshold)
        writeVerified(rechargeKey, state.rechargeLevel)
        writeVerified(modeKey, state.mode)
    }

    private fun writeVerified(key: String, value: Int) {
        check(Settings.Global.putInt(resolver, key, value)) { "Failed to write $key" }
        check(
            Settings.Global.getInt(
                resolver,
                key,
                Int.MIN_VALUE
            ) == value
        ) { "Verification failed for $key" }
    }

    private companion object {
        const val MIN_THRESHOLD = 20
        const val MODE_MAXIMUM = 3 // Observed on current Samsung firmware; verified after writing.
    }
}
