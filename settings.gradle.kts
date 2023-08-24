rootProject.name = "hapi-change-capture"
pluginManagement {
    val springBootPluginVersion: String by settings
    val kotlinVersion: String by settings
    val springDependencyManagement: String by settings

    plugins {
        id("org.springframework.boot") version springBootPluginVersion
        id("io.spring.dependency-management") version springDependencyManagement
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.spring") version kotlinVersion
        id("org.jetbrains.dokka") version kotlinVersion

    }
}