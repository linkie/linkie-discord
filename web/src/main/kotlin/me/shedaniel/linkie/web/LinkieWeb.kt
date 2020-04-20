package me.shedaniel.linkie.web

import me.shedaniel.linkie.Namespaces
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class LinkieWeb

fun main(args: Array<String>) {
    Namespaces.startLoop()
    runApplication<LinkieWeb>(*args)
}
