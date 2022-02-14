/*
 * Copyright (c) 2019, 2020, 2021 shedaniel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.shedaniel.linkie.discord.lang

import me.shedaniel.linkie.discord.utils.CommandContext
import java.util.*

object I18n {
    var defaultLocale = "en-US"
    private val locales = mutableMapOf<String, Locale>()

    fun translate(locale: String, key: String, vararg args: Any): String {
        val s = _translate(locale, key)
        return try {
            s.format(*args)
        } catch (e: Exception) {
            s
        }
    }

    fun _translate(locale: String, key: String): String {
        return rawTranslate(locale, key) ?: rawTranslate(defaultLocale, key) ?: key
    }

    fun rawTranslate(locale: String, key: String): String? {
        return locales.getOrPut(locale) {
            I18n::class.java.classLoader.getResource("lang/$locale.properties")?.readText()?.let {
                Locale(locale, Properties().apply { load(it.reader()) }.let {
                    it.entries.associate { it.key.toString() to it.value.toString() }
                })
            } ?: Locale(locale, mapOf())
        }.translations[key]
    }

    data class Locale(
        val name: String,
        val translations: Map<String, String>,
    )
}

fun String.i18n(ctx: CommandContext, vararg args: Any): String {
    return i18n(ctx.locale, *args)
}

fun String.i18n(locale: String?, vararg args: Any): String {
    return I18n.translate(locale ?: I18n.defaultLocale, this, *args)
}