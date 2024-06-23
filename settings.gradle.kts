@file:Suppress("UnstableApiUsage")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    rulesMode = RulesMode.FAIL_ON_PROJECT_RULES
    repositories {
        mavenCentral()
    }
}

rootProject.name = "schulferien-nrw-ical-aggregator"
