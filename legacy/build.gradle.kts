plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "cl.itstuki.uhc"
version = "1.3.0"
description = "UHC por episodios para Spigot/Paper 1.8.8 – 26.3 (edición Legacy)."

repositories {
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
    mavenCentral()
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("net.luckperms:api:5.4")
    // Textos con MiniMessage, títulos, action bar y tab en cualquier versión.
    implementation("net.kyori:adventure-platform-bukkit:4.4.1")
    implementation("net.kyori:adventure-text-minimessage:4.24.0")
    implementation("net.kyori:adventure-text-serializer-legacy:4.24.0")
    implementation("net.kyori:adventure-text-serializer-plain:4.24.0")
    // Materiales y sonidos con el nombre correcto en cada versión.
    implementation("com.github.cryptomorin:XSeries:14.0.0")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        // Bytecode de Java 8: corre en servidores 1.8 con Java 8 y en los más nuevos.
        options.release = 8
        options.compilerArgs.addAll(listOf("-Xlint:-options"))
    }

    processResources {
        val props = mapOf("version" to project.version.toString())
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    shadowJar {
        archiveBaseName = "TukiUHC-Legacy"
        archiveClassifier = ""
    mergeServiceFiles()
        relocate("net.kyori", "cl.itstuki.uhc.lib.kyori")
        relocate("com.cryptomorin.xseries", "cl.itstuki.uhc.lib.xseries")
        minimize {
            exclude(dependency("net.kyori:.*"))
        }
        exclude("META-INF/versions/**")
    }

    build {
        dependsOn(shadowJar)
    }

    jar {
        enabled = false
    }
}
