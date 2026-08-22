package com.alpwarestudio.chargefreeze.data

import android.content.Context
import android.annotation.SuppressLint
import com.alpwarestudio.chargefreeze.domain.OriginalBatteryProtection
import com.alpwarestudio.chargefreeze.domain.FreezeSession
import com.alpwarestudio.chargefreeze.domain.FreezePolicy
import com.alpwarestudio.chargefreeze.domain.SessionPhase

@SuppressLint("UseKtx") // Direct commit() result is a safety signal before privileged writes.
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("chargefreeze_session", Context.MODE_PRIVATE)

    @Synchronized
    fun savePreparing(s: OriginalBatteryProtection, startLevel: Int): Boolean =
        write(
            FreezeSession(
                phase = SessionPhase.PREPARING,
                original = s,
                startLevel = startLevel,
                currentThreshold = startLevel,
                startedAtMillis = System.currentTimeMillis()
            )
        )

    @Synchronized
    fun load(): FreezeSession? {
        if (!prefs.getBoolean("pending_restore", false)) return null
        val original = OriginalBatteryProtection(
            mode = prefs.getInt("mode", Int.MIN_VALUE),
            threshold = prefs.getInt("threshold", Int.MIN_VALUE),
            rechargeLevel = prefs.getInt("recharge", Int.MIN_VALUE)
        )
        if (original.mode == Int.MIN_VALUE ||
            original.threshold !in FreezePolicy.MIN_THRESHOLD..100 ||
            original.rechargeLevel !in 0..100
        ) return null

        val phase = runCatching {
            SessionPhase.valueOf(prefs.getString("phase", SessionPhase.RECOVERY_REQUIRED.name)!!)
        }.getOrDefault(SessionPhase.RECOVERY_REQUIRED)
        return FreezeSession(
            phase = phase,
            original = original,
            startLevel = prefs.getInt("start_level", original.threshold).coerceIn(0, 100),
            currentThreshold = prefs.getInt("current_threshold", original.threshold).coerceIn(0, 100),
            startedAtMillis = prefs.getLong("started_at", System.currentTimeMillis()),
            message = prefs.getString("message", null)
        )
    }

    @Synchronized
    fun markActive(threshold: Int): Boolean {
        val current = load() ?: return false
        return write(current.copy(phase = SessionPhase.ACTIVE, currentThreshold = threshold, message = null))
    }

    @Synchronized
    fun updateThreshold(threshold: Int): Boolean {
        val current = load() ?: return false
        return write(current.copy(currentThreshold = threshold))
    }

    @Synchronized
    fun markRestoring(): Boolean {
        val current = load() ?: return false
        return write(current.copy(phase = SessionPhase.RESTORING))
    }

    @Synchronized
    fun markRecoveryRequired(message: String): Boolean {
        val current = load() ?: return false
        return write(current.copy(phase = SessionPhase.RECOVERY_REQUIRED, message = message))
    }

    @Synchronized
    fun clear(): Boolean = prefs.edit().clear().commit()

    private fun write(session: FreezeSession): Boolean = prefs.edit()
        .putBoolean("pending_restore", true)
        .putString("phase", session.phase.name)
        .putInt("mode", session.original.mode)
        .putInt("threshold", session.original.threshold)
        .putInt("recharge", session.original.rechargeLevel)
        .putInt("start_level", session.startLevel)
        .putInt("current_threshold", session.currentThreshold)
        .putLong("started_at", session.startedAtMillis)
        .putString("message", session.message)
        .commit()
}
