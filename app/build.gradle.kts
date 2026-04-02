plugins {
    application
    id("azkar-common")
    alias(libs.plugins.org.openjfx.javafxplugin)
    id("dev.hydraulic.conveyor") version "2.0"
}

version = "0.1.0"

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

    // Logging: Logback as SLF4J backend
    runtimeOnly(libs.ch.qos.logback.logback.classic)

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

tasks.jar {
    archiveFileName.set("azkar-app.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "com.azkar.Launcher"
    }

    // Include resources (FXML, CSS, etc.)
    from(sourceSets.main.get().output)
}

// ---------------------------------------------------------------------------
// Native packaging is handled by Hydraulic Conveyor.
// See conveyor.conf in this module for configuration.
//
// Convenience tasks (require Conveyor CLI on PATH):
//   ./gradlew :app:runConveyor       — run the app in packaged form
//   ./gradlew :app:makeWindowsApp    — build Windows app directory
//   ./gradlew :app:makeMacAmd64App   — build macOS app (Intel)
//   ./gradlew :app:makeMacAarch64App — build macOS app (Apple Silicon)
//   ./gradlew :app:makeLinuxApp      — build Linux app
// ---------------------------------------------------------------------------

val conveyorExecutable = if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
    "conveyor.cmd"
} else {
    "conveyor"
}

tasks.register<Exec>("runConveyor") {
    group = "conveyor"
    description = "Run the app in packaged form via Conveyor"
    dependsOn("jar", "writeConveyorConfig")
    workingDir = projectDir
    commandLine(conveyorExecutable, "run")
}

fun registerConveyorMakeTask(
    name: String,
    taskDescription: String,
    machine: String,
    outputSubDir: String,
    target: String
) {

    val conveyorOutputRoot = "build/installers"
    tasks.register<Exec>(name) {
        group = "conveyor"
        description = taskDescription
        dependsOn("jar", "writeConveyorConfig")
        workingDir = projectDir
        commandLine(
            conveyorExecutable,
            "-Kapp.machines=$machine",
            "make",
            "--output-dir=$conveyorOutputRoot/$outputSubDir",
            target
        )
        doLast {
            println("Conveyor output directory: ${projectDir.resolve("$conveyorOutputRoot/$outputSubDir")}")
        }
    }
}

registerConveyorMakeTask(
    name = "makeWindowsApp",
    taskDescription = "Build the Windows app directory via Conveyor",
    machine = "windows.amd64",
    outputSubDir = "windows-app",
    target = "windows-app"
)

registerConveyorMakeTask(
    name = "makeMacAmd64App",
    taskDescription = "Build the macOS app (Intel) via Conveyor",
    machine = "mac.amd64",
    outputSubDir = "mac-amd64-app",
    target = "mac-app"
)

registerConveyorMakeTask(
    name = "makeMacAarch64App",
    taskDescription = "Build the macOS app (Apple Silicon) via Conveyor",
    machine = "mac.aarch64",
    outputSubDir = "mac-aarch64-app",
    target = "mac-app"
)

registerConveyorMakeTask(
    name = "makeLinuxApp",
    taskDescription = "Build the Linux app via Conveyor",
    machine = "linux.amd64.glibc",
    outputSubDir = "linux-app",
    target = "linux-app"
)
