package me.shedaniel.linkie.discord

import java.time.Duration
import java.util.*
import kotlin.concurrent.timerTask

@Suppress("MemberVisibilityCanBePrivate", "unused")
class ValueKeeper<T> constructor(val timeToKeep: Duration, var value: Optional<T>, val getter: () -> T) {
    companion object {
        private val timer = Timer()
    }

    private var task: TimerTask? = null

    constructor(timeToKeep: Duration, value: T, getter: () -> T) : this(timeToKeep, Optional.of(value), getter)
    constructor(timeToKeep: Duration, getter: () -> T) : this(timeToKeep, getter(), getter)

    init {
        schedule()
    }

    fun get(): T = value.orElseGet { getter().also { value = Optional.of(it); schedule() } }

    fun clear() {
        value = Optional.empty()
        System.gc()
    }

    fun schedule() {
        task?.cancel()
        task = timerTask { clear() }
        timer.schedule(task, timeToKeep.toMillis())
    }
}