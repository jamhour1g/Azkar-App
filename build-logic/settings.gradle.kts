pluginManagement {
    repositories {
        if (System.getenv("CI") == "true") {
            maven("https://maven-central.storage-download.googleapis.com/maven2/")
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
