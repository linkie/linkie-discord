package me.shedaniel.linkie

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

val json = Json(JsonConfiguration.Stable.copy(strictMode = false))

private val executor = Executors.newScheduledThreadPool(16)

fun startLoop() {
//    executor.scheduleAtFixedRate(::updateYarn, 0, 20, TimeUnit.MINUTES)
    executor.scheduleAtFixedRate(::updateMCP, 0, 20, TimeUnit.MINUTES)
}