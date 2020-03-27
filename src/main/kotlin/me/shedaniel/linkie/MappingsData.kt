package me.shedaniel.linkie

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

val json = Json(JsonConfiguration.Stable.copy(ignoreUnknownKeys = true, isLenient = true))

private val executor = Executors.newScheduledThreadPool(16)

fun startLoop() {
    executor.scheduleAtFixedRate(::updateYarn, 0, 30, TimeUnit.MINUTES)
    executor.scheduleAtFixedRate(::updateMCP, 0, 30, TimeUnit.MINUTES)
    executor.scheduleAtFixedRate(System::gc, 10, 300, TimeUnit.SECONDS)
}
