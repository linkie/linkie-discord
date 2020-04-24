package me.shedaniel.linkie

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import me.shedaniel.linkie.utils.tryToVersion
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

abstract class Namespace(val id: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return id == (other as Namespace).id
    }

    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = id

    private val cachedMappings = CopyOnWriteArrayList<MappingsContainer>()
    private val mappingsProviders = mutableMapOf<(String) -> Boolean, (String) -> MappingsContainer>()
    val json = Json(JsonConfiguration.Stable.copy(ignoreUnknownKeys = true, isLenient = true))
    var reloading = false

    suspend fun reset() {
        reloading = true
        cachedMappings.clear()
        reloadData()
        val jobs = getDefaultLoadedVersions().map {
            GlobalScope.launch(Dispatchers.IO) {
                createAndAdd(it)
            }
        }
        jobs.forEach { it.join() }
        reloading = false
    }

    abstract fun getDefaultLoadedVersions(): List<String>
    abstract fun getAllVersions(): List<String>
    abstract fun reloadData()
    open fun getMaximumCachedVersion(): Int = 3
    abstract fun getDefaultVersion(command: String?, channelId: Long?): String
    fun getAllSortedVersions(): List<String> =
            getAllVersions().sortedWith(Comparator.nullsFirst(compareBy { it.tryToVersion() })).asReversed()

    protected fun registerProvider(versionPredicate: (String) -> Boolean, containerProvider: (String) -> MappingsContainer) {
        mappingsProviders[versionPredicate] = containerProvider
    }

    operator fun get(version: String): MappingsContainer? = cachedMappings.firstOrNull { it.version == version.toLowerCase(Locale.ROOT) }

    fun create(version: String): MappingsContainer? {
        val entry = mappingsProviders.entries.firstOrNull { it.key(version) } ?: return null
        return entry.value(version)
    }

    fun createAndAdd(version: String): MappingsContainer? =
            create(version)?.also { cachedMappings.add(it).limitCachedData() }

    private fun <T> T.limitCachedData(): T {
        if (cachedMappings.size > getMaximumCachedVersion()) {
            val defaultLoadedVersions = getDefaultLoadedVersions()
            cachedMappings.firstOrNull { it.version !in defaultLoadedVersions }?.let { cachedMappings.remove(it) }
        }
        return this
    }

    fun getOrCreate(version: String): MappingsContainer? =
            get(version) ?: createAndAdd(version)

    fun getProvider(version: String): MappingsProvider {
        val container = get(version)
        if (container != null) {
            return MappingsProvider.of(version, container)
        }
        val entry = mappingsProviders.entries.firstOrNull { it.key(version) } ?: return MappingsProvider.ofEmpty()
        return MappingsProvider.ofSupplier(version, false) { entry.value(version).also { cachedMappings.add(it).limitCachedData() } }
    }

    fun getDefaultProvider(command: String?, channelId: Long?): MappingsProvider {
        val version = getDefaultVersion(command, channelId)
        return getProvider(version)
    }

    open fun supportsMixin(): Boolean = false
    open fun supportsAT(): Boolean = false
    open fun supportsAW(): Boolean = false
    open fun supportsFieldDescription(): Boolean = true

    data class MappingsProvider(var version: String?, var cached: Boolean?, var mappingsContainer: (() -> MappingsContainer)?) {
        companion object {
            fun of(version: String, mappingsContainer: MappingsContainer?): MappingsProvider =
                    if (mappingsContainer == null)
                        ofSupplier(version, null, null)
                    else ofSupplier(version, true) { mappingsContainer }

            fun ofSupplier(version: String?, cached: Boolean?, mappingsContainer: (() -> MappingsContainer)?): MappingsProvider =
                    MappingsProvider(version, cached, mappingsContainer)

            fun ofEmpty(): MappingsProvider =
                    MappingsProvider(null, null, null)
        }

        fun isEmpty(): Boolean = version == null || cached == null || mappingsContainer == null

        fun injectDefaultVersion(mappingsProvider: MappingsProvider) {
            if (isEmpty() && !mappingsProvider.isEmpty()) {
                version = mappingsProvider.version
                cached = mappingsProvider.cached
                mappingsContainer = mappingsProvider.mappingsContainer
            }
        }
    }
}