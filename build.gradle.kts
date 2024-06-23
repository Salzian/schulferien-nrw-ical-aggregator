plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

group = "dev.salzian"

version = "1.0-SNAPSHOT"

dependencies {
    implementation(libs.jsoup)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.bundles.ktor.client)
    implementation(libs.ical4j)

    testImplementation(kotlin("test"))
}

application { mainClass.set("MainKt") }

tasks.test { useJUnitPlatform() }

val javaLanguageVersion = JavaLanguageVersion.of(21)

java {
    toolchain {
        languageVersion.set(javaLanguageVersion)
        vendor.set(JvmVendorSpec.BELLSOFT)
    }
}

kotlin { jvmToolchain(javaLanguageVersion.asInt()) }
