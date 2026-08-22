package com.alpwarestudio.chargefreeze.domain

/** Pure policy shared by the Samsung backend and unit tests. */
object FreezePolicy {
    const val MIN_THRESHOLD = 20

    fun targetFor(level: Int, margin: Int): Int {
        require(level in 0..100) { "Invalid battery level: $level" }
        require(margin in 1..5) { "Invalid freeze margin: $margin" }
        check(level > MIN_THRESHOLD) { "Battery level is too low to freeze safely" }
        return (level - margin).coerceIn(MIN_THRESHOLD, 100)
    }

    fun nextThreshold(level: Int, currentThreshold: Int, margin: Int): Int {
        if (level !in 0..100 || currentThreshold !in MIN_THRESHOLD..100) {
            return currentThreshold
        }
        val desired = (level - margin).coerceAtLeast(MIN_THRESHOLD)
        return if (level <= currentThreshold + 1 && desired < currentThreshold) desired
        else currentThreshold
    }
}
