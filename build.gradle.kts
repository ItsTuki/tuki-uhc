plugins {
    java
    alias(libs.plugins.run.paper)
}

group = "cl.itstuki.uhc"
version = "1.1.1"
description = "UHC por episodios totalmente configurable para Paper 26.3."

val minecraftVersion: String by project

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
    mavenCentral()
}

dependencies {
    compileOnly(libs.paper.api)
    // Integraciones opcionales: solo se usan si el servidor tiene esos plugins.
    compileOnly(libs.placeholderapi)
    compileOnly(libs.luckperms.api)
}

java {
    // Minecraft 26.x / Paper 26.x requieren Java 25
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release = 25
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    }

    processResources {
        val props = mapOf("version" to project.version.toString())
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    jar {
        archiveBaseName = "TukiUHC"
    }

    runServer {
        minecraftVersion(minecraftVersion)
        jvmArgs("-Dcom.mojang.eula.agree=true")
    }
}
