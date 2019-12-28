package me.shedaniel.linkie

import kotlinx.serialization.Serializable

data class MappingsContainer(
        val version: String,
        val classes: MutableList<Class> = mutableListOf(),
        val name: String = "Yarn"
) {
    fun getClass(intermediaryName: String): Class? =
            classes.firstOrNull { it.intermediaryName == intermediaryName }

    fun getOrCreateClass(intermediaryName: String): Class =
            getClass(intermediaryName) ?: Class(intermediaryName).also { classes.add(it) }

    fun prettyPrint() {
        classes.forEach {
            it.apply {
                println("$intermediaryName: $mappedName")
                methods.forEach {
                    it.apply {
                        println("  $intermediaryName $intermediaryDesc: $mappedName $mappedDesc")
                    }
                }
                fields.forEach {
                    it.apply {
                        println("  $intermediaryName $intermediaryDesc: $mappedName $mappedDesc")
                    }
                }
                println()
            }
        }
    }
}

fun MappingsContainer.getClassByObfName(obf: String): Class? {
    classes.forEach {
        if (it.obfName.isMerged()) {
            if (it.obfName.merged.equals(obf, ignoreCase = true))
                return it
        } else if (it.obfName.client.equals(obf, ignoreCase = true))
            return it
        else if (it.obfName.server.equals(obf, ignoreCase = true))
            return it
    }
    return null
}

data class Class(
        val intermediaryName: String,
        val obfName: Obf = Obf(),
        var mappedName: String? = null,
        val methods: MutableList<Method> = mutableListOf(),
        val fields: MutableList<Field> = mutableListOf()
) {
    fun getMethod(intermediaryName: String): Method? =
            methods.firstOrNull { it.intermediaryName == intermediaryName }

    fun getOrCreateMethod(intermediaryName: String, intermediaryDesc: String): Method =
            getMethod(intermediaryName) ?: Method(intermediaryName, intermediaryDesc).also { methods.add(it) }

    fun getField(intermediaryName: String): Field? =
            fields.firstOrNull { it.intermediaryName == intermediaryName }

    fun getOrCreateField(intermediaryName: String, intermediaryDesc: String): Field =
            getField(intermediaryName) ?: Field(intermediaryName, intermediaryDesc).also { fields.add(it) }
}

data class Method(
        val intermediaryName: String,
        val intermediaryDesc: String,
        val obfName: Obf = Obf(),
        val obfDesc: Obf = Obf(),
        var mappedName: String? = null,
        var mappedDesc: String? = null
)

data class Field(
        val intermediaryName: String,
        val intermediaryDesc: String,
        val obfName: Obf = Obf(),
        val obfDesc: Obf = Obf(),
        var mappedName: String? = null,
        var mappedDesc: String? = null
)

data class Obf(
        var client: String? = null,
        var server: String? = null,
        var merged: String? = null
) {
    fun list(): List<String> {
        val list = mutableListOf<String>()
        if (client != null) list.add(client!!)
        if (server != null) list.add(server!!)
        if (merged != null) list.add(merged!!)
        return list
    }

    fun isMerged(): Boolean = merged != null
    fun isEmpty(): Boolean = list().isEmpty()
}

@Serializable
data class YarnBuild(
        val gameVersion: String,
        val separator: String,
        val build: Int,
        val maven: String,
        val version: String,
        val stable: Boolean
)