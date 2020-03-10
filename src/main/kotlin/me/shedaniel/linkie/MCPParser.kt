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

fun MappingsContainer.loadSrgFromURLZip(url: URL) {
    val stream = ZipInputStream(url.openStream())
    while (true) {
        val entry = stream.nextEntry ?: break
        if (!entry.isDirectory && entry.name.split("/").lastOrNull() == "joined.srg") {
            loadSrgFromInputStream(stream)
            break
        }
    }
}

private fun MappingsContainer.loadSrgFromInputStream(stream: InputStream) {
    val lines = InputStreamReader(stream).readLines().groupBy { it.split(' ')[0] }
    lines["CL:"]?.forEach { classLine ->
        val split = classLine.substring(4).split(" ")
        val obf = split[0]
        val named = split[1]
        getOrCreateClass(named).apply {
            obfName.merged = obf
        }
    }
    lines["FD:"]?.forEach { fieldLine ->
        val split = fieldLine.substring(4).split(" ")
        val obfClass = split[0].substring(0, split[0].lastIndexOf('/'))
        val obf = split[0].substring(obfClass.length + 1)
        val namedClass = split[1].substring(0, split[1].lastIndexOf('/'))
        val intermediary = split[1].substring(namedClass.length + 1)
        getClass(namedClass)?.apply {
            getOrCreateField(intermediary, "").apply {
                obfName.merged = obf
                obfDesc.merged = ""
            }
        }.also {
            if (it == null)
                println(namedClass)
        }
    }
    lines["MD:"]?.forEach { fieldLine ->
        val split = fieldLine.substring(4).split(" ")
        val obfClass = split[0].substring(0, split[0].lastIndexOf('/'))
        val obf = split[0].substring(obfClass.length + 1)
        val obfDesc = split[1]
        val namedClass = split[2].substring(0, split[2].lastIndexOf('/'))
        val intermediary = split[2].substring(namedClass.length + 1)
        val namedDesc = split[3]
        getClass(namedClass)?.apply {
            getOrCreateMethod(intermediary, namedDesc).also { method ->
                method.obfName.merged = obf
                method.obfDesc.merged = obfDesc
            }
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
                        getOrCreateMethod(tsrg, "").also { method ->
                            method.obfName.merged = obf
                            method.obfDesc.merged = obfDesc
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
