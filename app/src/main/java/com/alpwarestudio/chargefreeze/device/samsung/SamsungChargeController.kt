package com.alpwarestudio.chargefreeze.device.samsung

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.alpwarestudio.chargefreeze.data.AppPreferences
import com.alpwarestudio.chargefreeze.domain.ChargeController
import com.alpwarestudio.chargefreeze.domain.FreezePolicy
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

    override fun isSupported(): Boolean {
        if (!Build.MANUFACTURER.equals("samsung", ignoreCase = true)) return false
        val mode = Settings.Global.getString(resolver, modeKey)?.toIntOrNull() ?: return false
        val threshold = Settings.Global.getString(resolver, thresholdKey)?.toIntOrNull() ?: return false
        val recharge = Settings.Global.getString(resolver, rechargeKey)?.toIntOrNull() ?: return false
        return mode >= 0 && threshold in FreezePolicy.MIN_THRESHOLD..100 && recharge in 0..100
    }

    override fun hasWritePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED

    override fun readOriginalState(): OriginalBatteryProtection? = runCatching {
        val state = OriginalBatteryProtection(
            mode = Settings.Global.getInt(resolver, modeKey),
            threshold = Settings.Global.getInt(resolver, thresholdKey),
            rechargeLevel = Settings.Global.getInt(resolver, rechargeKey)
        )
        check(state.mode >= 0 && state.threshold in FreezePolicy.MIN_THRESHOLD..100)
        check(state.rechargeLevel in 0..100)
        state
    }.getOrNull()

    override fun beginFreeze(level: Int): Result<Int> = runCatching {
        check(isSupported()) { "Unsupported Samsung firmware" }
        check(hasWritePermission()) { "WRITE_SECURE_SETTINGS is not granted" }
        // Keep a small margin below the current level. This avoids immediate charge oscillation.
        // Write the threshold before entering Maximum mode. Samsung Device Care follows the same
        // effective sequence: when Maximum is activated, BatteryService consumes the stored
        // battery_protection_threshold and cuts charging if the current level is above it.
        val target = FreezePolicy.targetFor(level, AppPreferences(context).freezeMargin)
        writeVerified(thresholdKey, target)
        writeVerified(modeKey, MODE_MAXIMUM)
        target
    }

    override fun maintainFreeze(level: Int, currentThreshold: Int): Result<Int> = runCatching {
        check(isSupported()) { "Unsupported Samsung firmware" }
        check(hasWritePermission()) { "WRITE_SECURE_SETTINGS is not granted" }
        check(Settings.Global.getInt(resolver, modeKey, Int.MIN_VALUE) == MODE_MAXIMUM) {
            "Battery protection mode was changed outside ChargeFreeze"
        }
        check(Settings.Global.getInt(resolver, thresholdKey, Int.MIN_VALUE) == currentThreshold) {
            "Battery protection threshold was changed outside ChargeFreeze"
        }
        val desired = FreezePolicy.nextThreshold(
            level,
            currentThreshold,
            AppPreferences(context).freezeMargin
        )
        if (desired < currentThreshold) {
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
        const val MODE_MAXIMUM = 1 // Verified from Samsung Device Care: Maximum click changes protect_battery 3 -> 1.
    }
}
