@file:JvmName("MiloDemoServer")

package com.digitalpetri.opcua.server

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.core.util.StatusPrinter
import io.github.soc.directories.ProjectDirectories
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.eclipse.milo.opcua.stack.core.NodeIds
import org.eclipse.milo.opcua.stack.core.Stack
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.security.Security
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis


fun main() {
    val startNanos = System.nanoTime()

    // start running this static initializer ASAP, it measurably affects startup time.
    Thread { NodeIds.Boolean }.start()

    val directories = ProjectDirectories.from(
        "com",
        "digitalpetri",
        "MiloDemoServer"
    )

    // Use the system property "configDir" if it is set, otherwise use the default.
    // note: intentionally using `directories.dataDir` here, not `directories.configDir`
    val configDirPath = System.getProperty("configDir", directories.dataDir)
    val configDir = File(configDirPath).apply {
        if (!exists()) {
            assert(mkdirs())
        }
    }

    configDir.resolve("logback.xml").apply {
        if (!exists()) {
            Files.copy(
                DemoServer::class.java
                    .classLoader
                    .getResourceAsStream("default-logback.xml")!!,
                toPath()
            )
        }
        assert(exists())

        configureLogback(this)
    }

    // Required for Aes256_Sha256_RsaPss
    Security.addProvider(BouncyCastleProvider())

    Stack.ConnectionLimits.RATE_LIMIT_ENABLED = true

    val demoServer = DemoServer(configDir).also { it.startup() }

    val startupTimeMillis = TimeUnit.MILLISECONDS.convert(
        System.nanoTime() - startNanos,
        TimeUnit.NANOSECONDS
    )

    LoggerFactory.getLogger(DemoServer::class.java).apply {
        info("Eclipse Milo Demo Server started in ${startupTimeMillis}ms")
        info("  config dir: $configDir")
    }

    val future = CompletableFuture<Unit>()

    Runtime.getRuntime().addShutdownHook(object : Thread("ShutdownHook") {
        override fun run() {
            println("shutting down server...")
            val elapsed = measureTimeMillis { demoServer.shutdown() }
            println("...server shutdown complete in ${elapsed}ms")
            future.complete(Unit)
        }
    })

    future.get()
}

private fun configureLogback(logbackXml: File) {
    val context = LoggerFactory.getILoggerFactory() as LoggerContext

    try {
        val configurator = JoranConfigurator()
        configurator.context = context
        context.reset()

        println(System.getProperty("user.dir"))

        configurator.doConfigure(logbackXml)
    } catch (e: Exception) {
        System.err.println("Error configuring logback: $e")
    }

    StatusPrinter.printInCaseOfErrorsOrWarnings(context)
}
