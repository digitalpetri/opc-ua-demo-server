import org.jetbrains.kotlin.gradle.plugin.extraProperties
import java.text.SimpleDateFormat
import java.util.*

plugins {
    kotlin("jvm") version "1.9.10"
    id("org.beryx.runtime") version "1.12.7"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.digitalpetri.opcua"
version = "1.0-SNAPSHOT"

val miloVersion: String = "1.0.0-SNAPSHOT"
val packageName: String = "milo-demo-server"

application {
    applicationName = "milo-demo-server"
    mainClass = "com.digitalpetri.opcua.server.MiloDemoServer"
}

runtime {
    options.addAll("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages", "--bind-services")

    modules.addAll(
        "jdk.management",
        "java.management",
        "java.sql",
        "java.naming",
        "java.logging",
        "java.xml",
        "jdk.unsupported"
    )

    val platformSuffix = if (project.hasProperty("platform")) {
        val platform: String by project
        "-$platform"
    } else {
        ""
    }

    imageDir = file("${layout.buildDirectory.get()}/milo-demo-server")
    imageZip = file("${layout.buildDirectory.get()}/milo-demo-server${platformSuffix}.zip")
}

tasks.shadowJar {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    minimize {
        exclude(dependency("ch.qos.logback:logback-classic"))
        exclude(dependency("com.sksamuel.hoplite:.*:.*"))
        exclude(dependency("org.bouncycastle:.*:.*"))
        exclude(dependency("org.jetbrains.kotlin:kotlin-reflect:.*"))
        exclude(dependency("org.glassfish.jaxb:.*:.*"))
    }
}

tasks.distZip {
    dependsOn("clean", "runtimeZip")
}

tasks.jar {
    fun date(): String? {
        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
        df.timeZone = TimeZone.getTimeZone("UTC")
        return df.format(Date())
    }
    manifest {
        attributes["Main-Class"] = "com.digitalpetri.opcua.server.MiloDemoServer"
        attributes["Implementation-Title"] = "milo-demo-server"
        attributes["X-Stack-Version"] = miloVersion
        attributes["X-SDK-Version"] = miloVersion
        attributes["X-Server-Build-Date"] = date()
        attributes["X-Server-Build-Number"] = "0" // TODO git hash
        attributes["X-Server-Software-Version"] = project.version
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    implementation("org.eclipse.milo:milo-sdk-client:$miloVersion")
    implementation("org.eclipse.milo:milo-sdk-server:$miloVersion")
    implementation("org.eclipse.milo:milo-dtd-manager:$miloVersion")

    implementation("com.digitalpetri.opcua:milo-object-impls:1.0.0-SNAPSHOT")

    implementation("com.google.guava:guava:31.1-jre")

    implementation("ch.qos.logback:logback-classic:1.4.11")

    implementation("com.sksamuel.hoplite:hoplite-core:2.8.0.RC3")
    implementation("com.sksamuel.hoplite:hoplite-json:2.8.0.RC3")
    implementation("io.github.soc:directories:12")
}

repositories {
    mavenLocal()
    mavenCentral()
}
