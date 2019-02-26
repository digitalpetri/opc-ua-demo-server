@file:JvmName("MiloDemoServer")

package com.isw.opcua.server

import org.apache.logging.log4j.core.config.Configurator
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CompletableFuture

fun main() {
    val userDir = File(System.getProperty("user.dir"))

    val dataDir = userDir.resolve("data").apply {
        if (!exists()) {
            assert(mkdirs())
        }
    }

    val configDir = dataDir.resolve("config").apply {
        if (!exists()) {
            assert(mkdirs())
        }
    }

    configDir.resolve("log4j2.xml").apply {
        if (!exists()) {
            Files.copy(
                DemoServer::class.java
                    .classLoader
                    .getResourceAsStream("default-log4j2.xml"),
                toPath()
            )
        }
        assert(exists())

        Configurator.initialize(null, path)
    }

    val demoServer = DemoServer(dataDir).also { it.startup() }

    val future = CompletableFuture<Unit>()

    Runtime.getRuntime().addShutdownHook(Thread {
        demoServer.shutdown()
        future.complete(Unit)
    })

    future.get()
}
