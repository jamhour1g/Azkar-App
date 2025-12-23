plugins {
    application
    id("azkar-common")
    alias(libs.plugins.org.openjfx.javafxplugin)
    alias(libs.plugins.org.beryx.jlink)
}

dependencies {

    // JavaFX
    implementation(libs.org.openjfx.javafx.controls)
    implementation(libs.org.openjfx.javafx.fxml)
    implementation(libs.org.openjfx.javafx.media)
    implementation(libs.org.openjfx.javafx.web)

    // UI Libraries
    implementation(libs.org.kordamp.ikonli.ikonli.javafx)
    implementation(libs.org.kordamp.ikonli.ikonli.fontawesome6.pack)
    implementation(libs.org.controlsfx.controlsfx)

    // Installer
    implementation(libs.com.install4j.install4j.runtime)

}

javafx {
    version = libs.org.openjfx.javafx.web.get().version

    val moduleNames = listOf(
        libs.org.openjfx.javafx.web.get().module.name,
        libs.org.openjfx.javafx.controls.get().module.name,
        libs.org.openjfx.javafx.fxml.get().module.name,
        libs.org.openjfx.javafx.media.get().module.name
    ).map { it.replace("-", ".") }
        .toTypedArray()

    modules(*moduleNames)

}

application {
    mainModule = "com.azkar.app"
    mainClass = "com.azkar.Launcher"
}

jlink {
    imageZip = layout.buildDirectory.file("/distributions/app-${javafx.platform.classifier}.zip")
    options = listOf(
        "--strip-debug",
        "--compress", "2",
        "--no-header-files",
        "--no-man-pages"
    )
    launcher {
        name = "app"
    }
}

tasks.jar {
    archiveFileName.set("azkar-app.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // Include resources (FXML, CSS, etc.)
    from(sourceSets.main.get().output)
}

tasks.register<Jar>("fatJar") {
    archiveFileName.set("azkar-components-fat.jar")
    group = "build"
    description = "Builds a JAR including dependencies for Scene Builder"

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })

    with(tasks.jar.get())
}