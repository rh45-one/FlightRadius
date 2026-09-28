package com.flightradius.app.domain

/** Injectable time source so time-dependent logic is testable. */
fun interface TimeSource {
    fun nowMs(): Long
}

object SystemTimeSource : TimeSource {
    override fun nowMs(): Long = System.currentTimeMillis()
}
