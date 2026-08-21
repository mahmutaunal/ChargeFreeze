package com.alpwarestudio.chargefreeze.data

import android.content.Context
import com.alpwarestudio.chargefreeze.domain.OriginalBatteryProtection
import androidx.core.content.edit

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("chargefreeze_session", Context.MODE_PRIVATE)

    fun saveOriginal(s: OriginalBatteryProtection) = prefs.edit {
        putBoolean("pending_restore", true)
            .putInt("mode", s.mode)
            .putInt("threshold", s.threshold)
            .putInt("recharge", s.rechargeLevel)
    }

    fun loadOriginal(): OriginalBatteryProtection? {
        if (!prefs.getBoolean("pending_restore", false)) return null
        return OriginalBatteryProtection(
            prefs.getInt("mode", 0), prefs.getInt("threshold", 80), prefs.getInt("recharge", 95)
        )
    }

    fun clear() = prefs.edit { clear() }
}
