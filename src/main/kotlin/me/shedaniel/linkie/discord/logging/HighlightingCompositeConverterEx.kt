package me.shedaniel.linkie.discord.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.pattern.color.ANSIConstants
import ch.qos.logback.core.pattern.color.ForegroundCompositeConverterBase

class HighlightingCompositeConverterEx : ForegroundCompositeConverterBase<ILoggingEvent>() {
    override fun getForegroundColorCode(event: ILoggingEvent?): String {
        return when (event?.level) {
            Level.ERROR -> ANSIConstants.RED_FG
            Level.WARN -> ANSIConstants.YELLOW_FG
            Level.INFO -> ANSIConstants.CYAN_FG
            Level.DEBUG -> ANSIConstants.WHITE_FG
            Level.TRACE -> ANSIConstants.WHITE_FG
            else -> ANSIConstants.DEFAULT_FG
        }
    }
}