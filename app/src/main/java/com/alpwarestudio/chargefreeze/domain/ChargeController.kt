package com.alpwarestudio.chargefreeze.domain

interface ChargeController {
    val name: String
    fun isSupported(): Boolean
    fun hasWritePermission(): Boolean
    fun readOriginalState(): OriginalBatteryProtection?
    fun beginFreeze(level: Int): Result<Int>
    fun maintainFreeze(level: Int, currentThreshold: Int): Result<Int>
    fun restore(state: OriginalBatteryProtection): Result<Unit>
}
