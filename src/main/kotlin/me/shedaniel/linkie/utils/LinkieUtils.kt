package me.shedaniel.linkie.utils

import me.shedaniel.linkie.MappingsContainer
import java.io.IOException
import java.io.StringReader
import java.util.*
import java.util.regex.Pattern
import kotlin.Comparator
import kotlin.math.min


fun <T> Iterable<T>.dropAndTake(drop: Int, take: Int): List<T> =
        drop(drop).take(take)

private fun editDistance(s11: String, s22: String): Int {
    val costs = IntArray(s22.length + 1)
    for (i in 0..s11.length) {
        var lastValue = i
        for (j in 0..s22.length) {
            if (i == 0)
                costs[j] = j
            else {
                if (j > 0) {
                    var newValue = costs[j - 1]
                    if (s11[i - 1] != s22[j - 1])
                        newValue = min(min(newValue, lastValue), costs[j]) + 1
                    costs[j - 1] = lastValue
                    lastValue = newValue
                }
            }
        }
        if (i > 0)
            costs[s22.length] = lastValue
    }
    return costs[s22.length]
}

fun String?.similarityOnNull(other: String?): Double = if (this == null || other == null) 0.0 else similarity(other)

fun String.similarity(other: String): Double {
    val s11 = this.onlyClass().toLowerCase()
    val s22 = other.onlyClass().toLowerCase()
    var longer = s11
    var shorter = s22
    if (s11.length < s22.length) { // longer should always have greater length
        longer = s22
        shorter = s11
    }
    val longerLength = longer.length
    return if (longerLength == 0) {
        1.0 /* both strings are zero length */
    } else (longerLength - editDistance(longer, shorter)) / longerLength.toDouble()
}

fun String.onlyClass(c: Char = '/'): String {
    val indexOf = lastIndexOf(c)
    return if (indexOf < 0) this else substring(indexOf + 1)
}

fun String?.containsOrMatchWildcard(searchTerm: String): MatchResult {
    return when {
        this == null -> MatchResult(false)
        searchTerm.contains('/') -> MatchResult(contains(searchTerm, true), searchTerm, this)
        else -> MatchResult(onlyClass().contains(searchTerm.onlyClass(), true), searchTerm.onlyClass(), onlyClass())
    }
}

data class MatchResult(val matched: Boolean, val matchStr: String? = null, val selfTerm: String? = null)

class Version(val major: Int, val minor: Int, val patch: Int, val snapshot: String? = null) : Comparable<Version> {
    constructor(major: Int, minor: Int, snapshot: String? = null) : this(major, minor, 0, snapshot)

    private val version = versionOf(major, minor, patch)

    private fun versionOf(major: Int, minor: Int, patch: Int): Long {
        return major.toLong().shl(16) + minor.toLong().shl(8) + patch.toLong()
    }

    override fun toString(): String = (if (patch == 0) "$major.$minor" else "$major.$minor.$patch") + (snapshot?.let { "-$it" } ?: "")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val otherVersion = (other as? Version) ?: return false
        return this.version == otherVersion.version && this.snapshot == otherVersion.snapshot
    }

    override fun hashCode(): Int = Objects.hash(version, snapshot)

    @Suppress("RemoveExplicitTypeArguments")
    override fun compareTo(other: Version): Int = Comparator.comparingLong<Version> { it.version }.thenComparing(compareBy(nullsLast<String>()) { it.snapshot }).compare(this, other)

    fun isAtLeast(major: Int, minor: Int): Boolean = // this.version >= versionOf(major, minor, 0)
            this.major > major || (this.major == major &&
                    this.minor >= minor)

    fun isAtLeast(major: Int, minor: Int, patch: Int): Boolean = // this.version >= versionOf(major, minor, patch)
            this.major > major || (this.major == major &&
                    (this.minor > minor || this.minor == minor &&
                            this.patch >= patch))
}

val snapshotRegex = Pattern.compile("(?:Snapshot )?(\\d+)w0?(0|[1-9]\\d*)([a-z])")

fun String.toVersion(): Version {
    val matcher = snapshotRegex.matcher(this)
    if (matcher.matches()) {
        val year = matcher.group(1).toInt()
        val week = matcher.group(2).toInt()
        if (year == 20 && week >= 6) {
            return Version(1, 16, snapshot = "alpha.$year.w.$week")
        } else if (year == 19 && week >= 34) {
            return Version(1, 15, snapshot = "alpha.$year.w.$week")
        } else if (year == 18 && week >= 43 || year == 19 && week <= 14) {
            return Version(1, 14, snapshot = "alpha.$year.w.$week")
        } else if (year == 18 && week >= 30 && week <= 33) {
            return Version(1, 13, 1, snapshot = "alpha.$year.w.$week")
        } else if (year == 17 && week >= 43 || year == 18 && week <= 22) {
            return Version(1, 13, snapshot = "alpha.$year.w.$week")
        } else if (year == 17 && week == 31) {
            return Version(1, 12, 1, snapshot = "alpha.$year.w.$week")
        } else if (year == 17 && week >= 6 && week <= 18) {
            return Version(1, 12, snapshot = "alpha.$year.w.$week")
        } else if (year == 16 && week == 50) {
            return Version(1, 11, 1, snapshot = "alpha.$year.w.$week")
        } else if (year == 16 && week >= 32 && week <= 44) {
            return Version(1, 11, snapshot = "alpha.$year.w.$week")
        } else if (year == 16 && week >= 20 && week <= 21) {
            return Version(1, 10, snapshot = "alpha.$year.w.$week")
        } else if (year == 16 && week >= 14 && week <= 15) {
            return Version(1, 9, 3, snapshot = "alpha.$year.w.$week")
        } else if (year == 15 && week >= 31 || year == 16 && week <= 7) {
            return Version(1, 9, snapshot = "alpha.$year.w.$week")
        } else if (year == 14 && week >= 2 && week <= 34) {
            return Version(1, 8, snapshot = "alpha.$year.w.$week")
        } else if (year == 13 && week >= 47 && week <= 49) {
            return Version(1, 7, 4, snapshot = "alpha.$year.w.$week")
        } else if (year == 13 && week >= 36 && week <= 43) {
            return Version(1, 7, 2, snapshot = "alpha.$year.w.$week")
        } else if (year == 13 && week >= 16 && week <= 26) {
            return Version(1, 6, snapshot = "alpha.$year.w.$week")
        } else if (year == 13 && week >= 11 && week <= 12) {
            return Version(1, 5, 1, snapshot = "alpha.$year.w.$week")
        } else if (year == 13 && week >= 1 && week <= 10) {
            return Version(1, 5, snapshot = "alpha.$year.w.$week")
        } else if (year == 12 && week >= 49 && week <= 50) {
            return Version(1, 4, 6, snapshot = "alpha.$year.w.$week")
        } else if (year == 12 && week >= 32 && week <= 42) {
            return Version(1, 4, 2, snapshot = "alpha.$year.w.$week")
        } else if (year == 12 && week >= 15 && week <= 30) {
            return Version(1, 3, 1, snapshot = "alpha.$year.w.$week")
        } else if (year == 12 && week >= 3 && week <= 8) {
            return Version(1, 2, 1, snapshot = "alpha.$year.w.$week")
        } else if (year == 11 && week >= 47 || year == 12 && week <= 1) {
            return Version(1, 1, snapshot = "alpha.$year.w.$week")
        }
    }
    if (contains('-')) {
        val byDash = split('-')
        val byDot = byDash.first().split('.')

        return when (byDot.size) {
            0 -> Version(0, 0, snapshot = byDash.drop(1).joinToString("-"))
            1 -> Version(byDot[0].toInt(), 0, snapshot = byDash.drop(1).joinToString("-"))
            2 -> Version(byDot[0].toInt(), byDot[1].toInt(), snapshot = byDash.drop(1).joinToString("-"))
            3 -> Version(byDot[0].toInt(), byDot[1].toInt(), byDot[2].toInt(), snapshot = byDash.drop(1).joinToString("-"))
            else -> throw IllegalStateException()
        }
    }
    val byDot = split('.')

    return when (byDot.size) {
        0 -> Version(0, 0)
        1 -> Version(byDot[0].toInt(), 0)
        2 -> Version(byDot[0].toInt(), byDot[1].toInt())
        3 -> Version(byDot[0].toInt(), byDot[1].toInt(), byDot[2].toInt())
        else -> throw IllegalStateException()
    }
}

fun String.tryToVersion(): Version? {
    return try {
        toVersion()
    } catch (e: Exception) {
        null
    }
}

fun String.mapIntermediaryDescToNamed(mappingsContainer: MappingsContainer): String {
    if (startsWith('(') && contains(')')) {
        val split = split(')')
        val parametersOG = split[0].substring(1).toCharArray()
        val returnsOG = split[1].toCharArray()
        val parametersUnmapped = mutableListOf<String>()
        val returnsUnmapped = mutableListOf<String>()

        var lastT: String? = null
        for (char in parametersOG) {
            when {
                lastT != null && char == ';' -> {
                    parametersUnmapped.add(lastT)
                    lastT = null
                }
                lastT != null -> {
                    lastT += char
                }
                char == 'L' -> {
                    lastT = ""
                }
                else -> parametersUnmapped.add(char.toString())
            }
        }
        for (char in returnsOG) {
            when {
                lastT != null && char == ';' -> {
                    returnsUnmapped.add(lastT)
                    lastT = null
                }
                lastT != null -> {
                    lastT += char
                }
                char == 'L' -> {
                    lastT = ""
                }
                else -> returnsUnmapped.add(char.toString())
            }
        }
        return "(" + parametersUnmapped.joinToString("") {
            if (it.length != 1) {
                "L${mappingsContainer.getClass(it)?.mappedName ?: it};"
            } else
                it
        } + ")" + returnsUnmapped.joinToString("") {
            if (it.length != 1) {
                "L${mappingsContainer.getClass(it)?.mappedName ?: it};"
            } else
                it
        }
    }
    return this
}

fun String.remapMethodDescriptor(classMappings: (String) -> String): String {
    return try {
        val reader = StringReader(this)
        val result = StringBuilder()
        var started = false
        var insideClassName = false
        val className = StringBuilder()
        while (true) {
            val c: Int = reader.read()
            if (c == -1) {
                break
            }
            if (c == ';'.toInt()) {
                insideClassName = false
                result.append(classMappings(className.toString()))
            }
            if (insideClassName) {
                className.append(c.toChar())
            } else {
                result.append(c.toChar())
            }
            if (c == '('.toInt()) {
                started = true
            }
            if (started && c == 'L'.toInt()) {
                insideClassName = true
                className.setLength(0)
            }
        }
        result.toString()
    } catch (e: IOException) {
        throw AssertionError(e)
    }
}
