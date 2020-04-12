package me.shedaniel.linkie

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.launch
import me.shedaniel.linkie.namespaces.MCPNamespace
import me.shedaniel.linkie.namespaces.POMFNamespace
import me.shedaniel.linkie.namespaces.SpigotNamespace
import me.shedaniel.linkie.namespaces.YarnNamespace

object Namespaces {
    val namespaces = mutableMapOf<String, Namespace>()

    private fun registerNamespace(namespace: Namespace) =
            namespace.also { namespaces[it.id] = it }

    init {
        registerNamespace(YarnNamespace)
        registerNamespace(SpigotNamespace)
        registerNamespace(POMFNamespace)
        registerNamespace(MCPNamespace)
    }

    operator fun get(id: String) = namespaces[id]!!

    fun startLoop() {
        val tickerChannel = ticker(delayMillis = 1800000, initialDelayMillis = 0)
        CoroutineScope(Dispatchers.Default).launch {
            for (event in tickerChannel) {
                namespaces.map { (_, namespace) ->
                    launch { namespace.reset() }
                }.forEach { it.join() }
                System.gc()
            }
        }
    }
}