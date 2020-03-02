package me.shedaniel.linkie

object YarnV2BlackList {
    val blacklist: MutableList<String> = mutableListOf()

    fun loadData() {
        YarnV2BlackList::class.java.getResourceAsStream("/blacklist.txt").bufferedReader().forEachLine {
            blacklist.add(it)
        }
    }
}