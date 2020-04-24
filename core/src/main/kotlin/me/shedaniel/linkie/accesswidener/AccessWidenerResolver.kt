package me.shedaniel.linkie.accesswidener

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.content
import me.shedaniel.linkie.Class
import me.shedaniel.linkie.getClassByObfName
import me.shedaniel.linkie.getFieldByObfName
import me.shedaniel.linkie.getMethodByObfName
import me.shedaniel.linkie.namespaces.MojangNamespace
import me.shedaniel.linkie.namespaces.YarnNamespace
import me.shedaniel.linkie.utils.mapFieldIntermediaryDescToNamed
import me.shedaniel.linkie.utils.mapMethodIntermediaryDescToNamed
import net.fabricmc.stitch.merge.JarMerger
import org.objectweb.asm.*
import java.io.File
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.properties.Delegates

object AccessWidenerResolver {
    private val tmpFolder = File(System.getProperty("user.dir"), ".tmpaccesswidener")

    init {
        tmpFolder.deleteRecursively()
    }

    fun resolveVersion(version: String): StringBuilder {
        val mappingsContainer = YarnNamespace.getProvider(version).mappingsContainer!!.invoke()
        val versionManifest = MojangNamespace.versionJsonMap[version]!!
        val versionJson = MojangNamespace.json.parseJson(URL(versionManifest).readText()).jsonObject
        val clientSha1 = versionJson["downloads"]!!.jsonObject["client"]!!.jsonObject["sha1"]!!.content
        val clientUrl = URL(versionJson["downloads"]!!.jsonObject["client"]!!.jsonObject["url"]!!.content)
        val clientJar = File(tmpFolder, clientSha1.substring(0, 2) + "/$clientSha1.jar")
        val serverSha1 = versionJson["downloads"]!!.jsonObject["server"]!!.jsonObject["sha1"]!!.content
        val serverUrl = URL(versionJson["downloads"]!!.jsonObject["server"]!!.jsonObject["url"]!!.content)
        val serverJar = File(tmpFolder, serverSha1.substring(0, 2) + "/$serverSha1.jar")
        val mergedJar = File(tmpFolder, "$version/$version-merged.jar")
        if (!mergedJar.exists()) {
            runBlocking {
                launch(Dispatchers.IO) {
                    clientJar.parentFile.mkdirs()
                    if (!clientJar.exists()) {
                        clientUrl.openStream().use { downloadStream ->
                            clientJar.outputStream().use {
                                downloadStream.copyTo(it)
                            }
                        }
                    }
                }
                launch(Dispatchers.IO) {
                    serverJar.parentFile.mkdirs()
                    if (!serverJar.exists()) {
                        serverUrl.openStream().use { downloadStream ->
                            serverJar.outputStream().use {
                                downloadStream.copyTo(it)
                            }
                        }
                    }
                }
            }
            mergedJar.mkdirs()
            val merger = JarMerger(clientJar, serverJar, mergedJar)
            merger.use { it.merge() }
        }

        val builder = StringBuilder()
        builder.append("accessWidener\tv1\tnamed\n")
        class AccessWidenerVisitor : ClassVisitor(Opcodes.ASM8) {
            var ignore by Delegates.notNull<Boolean>()
            var isInterface by Delegates.notNull<Boolean>()
            var linkieClass by Delegates.notNull<Class>()
            var className by Delegates.notNull<String>()

            override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
                ignore = access and Opcodes.ACC_SYNTHETIC != 0 || access and Opcodes.ACC_ANNOTATION != 0
                if (ignore) return
                isInterface = access and Opcodes.ACC_INTERFACE != 0
                linkieClass = mappingsContainer.getClassByObfName(name)!!
                className = linkieClass.let { it.mappedName ?: it.intermediaryName }
                if (access and Opcodes.ACC_FINAL != 0) {
                    builder.append("extendable class $className\n")
                } else if (access and Opcodes.ACC_PUBLIC == 0) {
                    builder.append("accessible class $className\n")
                }
                super.visit(version, access, name, signature, superName, interfaces)
            }

            override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                if (ignore || access and Opcodes.ACC_SYNTHETIC != 0) return super.visitMethod(access, name, descriptor, signature, exceptions)
                if (access and Opcodes.ACC_PUBLIC == 0) {
                    val method = linkieClass.getMethodByObfName(name)
                    if (method != null) {
                        builder.append("extendable method $className ${method.let { it.mappedName ?: it.intermediaryName }} " +
                                method.intermediaryDesc.mapMethodIntermediaryDescToNamed(mappingsContainer) + "\n")
                        builder.append("accessible method $className ${method.let { it.mappedName ?: it.intermediaryName }} " +
                                method.intermediaryDesc.mapMethodIntermediaryDescToNamed(mappingsContainer) + "\n")
                    }
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions)
            }

            override fun visitField(access: Int, name: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
                if (ignore || access and Opcodes.ACC_SYNTHETIC != 0) return null
                if (access and Opcodes.ACC_PUBLIC == 0) {
                    val field = linkieClass.getFieldByObfName(name)
                    if (field != null) {
                        builder.append("accessible field $className ${field.let { it.mappedName ?: it.intermediaryName }} " +
                                field.intermediaryDesc.mapFieldIntermediaryDescToNamed(mappingsContainer) + "\n")
                        if (!isInterface || access and Opcodes.ACC_STATIC == 0) {
                            builder.append("mutable field $className ${field.let { it.mappedName ?: it.intermediaryName }} " +
                                    field.intermediaryDesc.mapFieldIntermediaryDescToNamed(mappingsContainer) + "\n")
                        }
                    }
                }
                return null
            }
        }

        val visitor = AccessWidenerVisitor()
        val zipInputStream = ZipInputStream(mergedJar.inputStream())
        while (true) {
            val entry = zipInputStream.nextEntry ?: break
            if (entry.name.endsWith(".class")) {
                val reader = ClassReader(zipInputStream.readBytes())
                reader.accept(visitor, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
            }
        }
        return builder
    }
}