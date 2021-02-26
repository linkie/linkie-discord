package me.shedaniel.linkie.discord.listener.listeners

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import me.shedaniel.linkie.discord.MessageCreator
import me.shedaniel.linkie.discord.listener.ChannelListener
import me.shedaniel.linkie.utils.Version
import me.shedaniel.linkie.utils.toVersion
import me.shedaniel.linkie.utils.tryToVersion
import org.dom4j.io.SAXReader
import java.io.InputStream
import java.net.URL
import java.net.URLConnection

open class MavenPomVersionListener : ChannelListener<MavenPomVersionListener.PomVersionData> {
    override val serializer: KSerializer<PomVersionData> = PomVersionData.serializer()
    private val mavenListeners = mutableMapOf<String, MavenListener>()

    protected fun listen(
        id: String,
        mavenPomURL: String,
        messageSender: (version: String, message: MessageCreator) -> Unit,
    ) = listen(id, URL(mavenPomURL), messageSender)

    protected fun listen(
        id: String,
        mavenPomURL: URL,
        messageSender: (version: String, message: MessageCreator) -> Unit,
    ) {
        mavenListeners[id] = MavenListener(mavenPomURL, messageSender)
    }

    override suspend fun updateData(data: PomVersionData?, message: MessageCreator): PomVersionData {
        val newData = data ?: PomVersionData()

        runBlocking {
            mavenListeners.forEach { (id, listener) ->
                launch {
                    val newVersions = mutableSetOf<Version>()
                    val containsKey = newData.mavens.containsKey(id)
                    val mavenData = newData.mavens.getOrPut(id, ::mutableSetOf)
                    val rootElement = SAXReader().read(listener.mavenPomURL.readText().byteInputStream()).rootElement
                    rootElement
                        .element("versioning")
                        .element("versions")
                        .elementIterator("version")
                        .asSequence()
                        .map { it.text }
                        .forEach { version ->
                            if (mavenData.add(version) && data != null && containsKey) {
                                if (version.tryToVersion() == null) {
                                    listener.messageSender(version, message)
                                } else {
                                    newVersions.add(version.toVersion())
                                }
                            }
                        }

                    if (newVersions.isNotEmpty()) {
                        val latest = newVersions.maxOrNull()!!.toString()
                        listener.messageSender(latest, message)
                    }
                }
            }
        }

        return newData
    }

    @Serializable
    data class PomVersionData(
        val mavens: MutableMap<String, MutableSet<String>> = mutableMapOf(),
    )

    private fun URL.readText(): String {
        val connection: URLConnection = openConnection()
        connection.addRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/50.0.2661.11 Safari/537.36")
        connection.connect()
        val stream: InputStream = connection.getInputStream()
        return stream.use { it.readBytes().decodeToString() }
    }
}

data class MavenListener(
    val mavenPomURL: URL,
    val messageSender: (version: String, message: MessageCreator) -> Unit,
)