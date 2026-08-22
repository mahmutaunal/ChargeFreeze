package com.alpwarestudio.chargefreeze.domain

data class BatterySnapshot(
    val level: Int = 0,
    val temperatureC: Float = 0f,
    val isCharging: Boolean = false,
    val plugged: Boolean = false,
    val source: String = "—",
    val health: Int = 0
)

data class OriginalBatteryProtection(
    val mode: Int,
    val threshold: Int,
    val rechargeLevel: Int
)

data class FreezeState(
    val active: Boolean = false,
    val startLevel: Int? = null,
    val currentThreshold: Int? = null,
    val startedAtMillis: Long? = null,
    val recoveryRequired: Boolean = false,
    val message: String? = null
)

enum class SessionPhase {
    PREPARING,
    ACTIVE,
    RESTORING,
    RECOVERY_REQUIRED
}

data class FreezeSession(
    val phase: SessionPhase,
    val original: OriginalBatteryProtection,
    val startLevel: Int,
    val currentThreshold: Int,
    val startedAtMillis: Long,
    val message: String? = null
)
