@file:Suppress("unused")

package me.shedaniel.linkie

class InvalidPermissionException : RuntimeException {
    constructor()
    constructor(message: String): super(message)
}

class InvalidUsageException : RuntimeException {
    constructor()
    constructor(message: String): super(message)
}