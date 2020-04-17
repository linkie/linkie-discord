package me.shedaniel.linkie

object YarnV2BlackList {
    val blacklist: MutableList<String> = mutableListOf()
    val blacklistString = """
        1.14 Pre-Release 1+build.10
        1.14 Pre-Release 1+build.11
        1.14 Pre-Release 1+build.12
        1.14 Pre-Release 1+build.2
        1.14 Pre-Release 1+build.3
        1.14 Pre-Release 1+build.4
        1.14 Pre-Release 1+build.5
        1.14 Pre-Release 1+build.6
        1.14 Pre-Release 1+build.7
        1.14 Pre-Release 1+build.8
        1.14 Pre-Release 1+build.9
        1.14 Pre-Release 2+build.1
        1.14 Pre-Release 2+build.10
        1.14 Pre-Release 2+build.11
        1.14 Pre-Release 2+build.12
        1.14 Pre-Release 2+build.13
        1.14 Pre-Release 2+build.14
        1.14 Pre-Release 2+build.2
        1.14 Pre-Release 2+build.3
        1.14 Pre-Release 2+build.4
        1.14 Pre-Release 2+build.5
        1.14 Pre-Release 2+build.6
        1.14 Pre-Release 2+build.7
        1.14 Pre-Release 2+build.8
        1.14 Pre-Release 2+build.9
        1.14 Pre-Release 3+build.1
        1.14 Pre-Release 3+build.2
        1.14 Pre-Release 3+build.3
        1.14 Pre-Release 3+build.4
        1.14 Pre-Release 4+build.1
        1.14 Pre-Release 4+build.2
        1.14 Pre-Release 4+build.3
        1.14 Pre-Release 4+build.4
        1.14 Pre-Release 4+build.5
        1.14 Pre-Release 4+build.6
        1.14 Pre-Release 4+build.7
        1.14 Pre-Release 5+build.1
        1.14 Pre-Release 5+build.2
        1.14 Pre-Release 5+build.3
        1.14 Pre-Release 5+build.4
        1.14 Pre-Release 5+build.5
        1.14 Pre-Release 5+build.6
        1.14 Pre-Release 5+build.7
        1.14 Pre-Release 5+build.8
        1.14+build.1
        1.14+build.10
        1.14+build.11
        1.14+build.12
        1.14+build.13
        1.14+build.14
        1.14+build.15
        1.14+build.16
        1.14+build.17
        1.14+build.18
        1.14+build.19
        1.14+build.2
        1.14+build.20
        1.14+build.21
        1.14+build.3
        1.14+build.4
        1.14+build.5
        1.14+build.6
        1.14+build.7
        1.14+build.8
        1.14+build.9
        1.14.1 Pre-Release 1+build.1
        1.14.1 Pre-Release 1+build.2
        1.14.1 Pre-Release 1+build.3
        1.14.1 Pre-Release 1+build.4
        1.14.1 Pre-Release 1+build.5
        1.14.1 Pre-Release 1+build.6
        1.14.1 Pre-Release 2+build.1
        1.14.1 Pre-Release 2+build.2
        1.14.1 Pre-Release 2+build.3
        1.14.1 Pre-Release 2+build.4
        1.14.1 Pre-Release 2+build.5
        1.14.1 Pre-Release 2+build.6
        1.14.1+build.1
        1.14.1+build.10
        1.14.1+build.2
        1.14.1+build.3
        1.14.1+build.4
        1.14.1+build.5
        1.14.1+build.6
        1.14.1+build.7
        1.14.1+build.8
        1.14.1+build.9
        1.14.2 Pre-Release 1+build.1
        1.14.2 Pre-Release 2+build.1
        1.14.2 Pre-Release 2+build.2
        1.14.2 Pre-Release 2+build.3
        1.14.2 Pre-Release 2+build.4
        1.14.2 Pre-Release 2+build.5
        1.14.2 Pre-Release 2+build.6
        1.14.2 Pre-Release 3+build.2
        1.14.2 Pre-Release 3+build.3
        1.14.2 Pre-Release 4+build.1
        1.14.2+build.1
        1.14.2+build.2
        1.14.2+build.3
        1.14.2+build.4
        1.14.2+build.5
        1.14.2+build.6
        1.14.2+build.7
        1.14.3+build.1
        1.14.3+build.10
        1.14.3+build.11
        1.14.3+build.12
        1.14.3+build.13
        1.14.3+build.2
        1.14.3+build.3
        1.14.3+build.4
        1.14.3+build.5
        1.14.3+build.6
        1.14.3+build.7
        1.14.3+build.8
        1.14.3-pre1+build.1
        1.14.3-pre1+build.2
        1.14.3-pre1+build.3
        1.14.3-pre1+build.4
        1.14.3-pre1+build.5
        1.14.3-pre1+build.6
        1.14.3-pre2+build.1
        1.14.3-pre2+build.10
        1.14.3-pre2+build.11
        1.14.3-pre2+build.12
        1.14.3-pre2+build.13
        1.14.3-pre2+build.14
        1.14.3-pre2+build.15
        1.14.3-pre2+build.16
        1.14.3-pre2+build.17
        1.14.3-pre2+build.18
        1.14.3-pre2+build.2
        1.14.3-pre2+build.3
        1.14.3-pre2+build.4
        1.14.3-pre2+build.5
        1.14.3-pre2+build.6
        1.14.3-pre2+build.7
        1.14.3-pre2+build.8
        1.14.3-pre2+build.9
        1.14.3-pre3+build.1
        1.14.3-pre4+build.1
        1.14.3-pre4+build.2
        1.14.3-pre4+build.3
        18w49a.1
        18w49a.10
        18w49a.11
        18w49a.12
        18w49a.13
        18w49a.14
        18w49a.15
        18w49a.16
        18w49a.17
        18w49a.18
        18w49a.2
        18w49a.20
        18w49a.21
        18w49a.22
        18w49a.3
        18w49a.4
        18w49a.5
        18w49a.6
        18w49a.7
        18w49a.8
        18w49a.9
        18w50a.1
        18w50a.10
        18w50a.100
        18w50a.11
        18w50a.12
        18w50a.13
        18w50a.14
        18w50a.15
        18w50a.16
        18w50a.17
        18w50a.18
        18w50a.19
        18w50a.2
        18w50a.20
        18w50a.21
        18w50a.22
        18w50a.23
        18w50a.24
        18w50a.25
        18w50a.26
        18w50a.27
        18w50a.28
        18w50a.29
        18w50a.3
        18w50a.30
        18w50a.31
        18w50a.32
        18w50a.33
        18w50a.34
        18w50a.35
        18w50a.36
        18w50a.37
        18w50a.38
    """.trimIndent()

    fun loadData() {
        blacklistString.split('\n').forEach { blacklist.add(it) }
    }
}