package me.shedaniel.linkie

import java.io.InputStream
import java.io.InputStreamReader
import java.net.URL
import java.util.zip.ZipInputStream

fun MappingsContainer.loadTsrgFromURLZip(url: URL) {
    val stream = ZipInputStream(url.openStream())
    while (true) {
        val entry = stream.nextEntry ?: break
        if (!entry.isDirectory && entry.name.split("/").lastOrNull() == "joined.tsrg") {
            loadTsrgFromInputStream(stream)
            break
        }
    }
}

private fun MappingsContainer.loadTsrgFromInputStream(stream: InputStream) {
    var lastClass: String? = null
    InputStreamReader(stream).forEachLine {
        val split = it.trimIndent().split(" ")
        if (!it.startsWith('\t')) {
            val obf = split[0]
            val named = split[1]
            getOrCreateClass(named).apply {
                obfName.merged = obf
            }
            lastClass = named
        } else {
            val clazz = lastClass?.let(this::getOrCreateClass) ?: return@forEachLine
            when (split.size) {
                2 -> {
                    val obf = split[0]
                    val tsrg = split[1]
                    clazz.apply {
                        // TODO Figure out the desc by getting it from yarn
                        getOrCreateField(tsrg, "").apply {
                            obfName.merged = obf
                            obfDesc.merged = ""
                        }
                    }
                }
                3 -> {
                    val obf = split[0]
                    val obfDesc = split[1]
                    val tsrg = split[2]
                    clazz.apply {
                        getOrCreateMethod(tsrg, "").apply {
                            obfName.merged = obf
                            this.obfDesc.merged = obfDesc
                        }
                    }
                }
            }
        }
    }
}

fun MappingsContainer.loadMCPFromURLZip(url: URL) {
    val stream = ZipInputStream(url.openStream())
    while (true) {
        val entry = stream.nextEntry ?: break
        if (!entry.isDirectory) {
            when (entry.name.split("/").lastOrNull()) {
                "fields.csv" -> loadMCPFieldsCSVFromInputStream(stream)
                "methods.csv" -> loadMCPMethodsCSVFromInputStream(stream)
            }
        }
    }
    stream.close()
}

private fun MappingsContainer.loadMCPFieldsCSVFromInputStream(stream: InputStream) {
    val map = mutableMapOf<String, String>()
    stream.bufferedReader().lineSequence().forEach {
        val split = it.split(',')
        map[split[0]] = split[1]
    }
    classes.forEach {
        it.fields.forEach {
            map[it.intermediaryName]?.apply {
                it.mappedName = this
                it.mappedDesc = ""
            }
        }
    }
}

private fun MappingsContainer.loadMCPMethodsCSVFromInputStream(stream: InputStream) {
    val map = mutableMapOf<String, String>()
    stream.bufferedReader().lineSequence().forEach {
        val split = it.split(',')
        map[split[0]] = split[1]
    }
    classes.forEach {
        it.methods.forEach {
            map[it.intermediaryName]?.apply {
                it.mappedName = this
                it.mappedDesc = ""
            }
        }
    }
}
