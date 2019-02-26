@file:JvmName("DemoServerBootstrap")

package com.isw.opcua.server

import org.apache.logging.log4j.core.config.Configurator
import java.io.File
import java.util.concurrent.CompletableFuture

fun main() {
    val userDir = File(System.getProperty("user.dir"))
    val dataDir = userDir.resolve("data")

    Configurator.initialize(
        null,
        dataDir
            .resolve("config")
            .resolve("log4j2.xml").path
    )

    val demoServer = DemoServer(dataDir).also { it.startup() }

    val future = CompletableFuture<Unit>()

    Runtime.getRuntime().addShutdownHook(Thread {
        demoServer.shutdown()
        future.complete(Unit)
    })

    future.get()
}
