import azkar.buildlogic.AzkarInstallerPlatform
import dev.nucleusframework.desktop.application.tasks.AbstractElectronBuilderPackageTask
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

val installerPlatform = AzkarInstallerPlatform.current()

val verifyJdkShipsJmods =
    tasks.register("verifyJdkShipsJmods") {
        val javaHome = providers.systemProperty("java.home")
        inputs.property("javaHome", javaHome)
        doLast {
            val home = Path.of(javaHome.get())
            val jmods = home.resolve("jmods")
            val hasModules =
                Files.isDirectory(jmods) &&
                    Files.list(jmods).use { modules ->
                        modules.anyMatch { it.fileName.toString().endsWith(".jmod") }
                    }
            check(hasModules) {
                "The JDK at $home has no usable 'jmods' directory, so ProGuard cannot resolve " +
                    "the JDK's own classes. Temurin 24+ dropped 'jmods' (JEP 493); build with " +
                    "Zulu 25 instead. See docs/releasing.md."
            }
        }
    }

tasks.matching { it.name == "proguardReleaseJars" }.configureEach {
    dependsOn(verifyJdkShipsJmods)
}

val verifyPackagingTools =
    tasks.register("verifyPackagingTools") {
        dependsOn(verifyJdkShipsJmods)
        doLast {
            check(installerPlatform.id in setOf("linux-x64", "windows-x64", "macos-x64", "macos-arm64")) {
                "Unsupported installer platform: ${installerPlatform.id}"
            }
        }
    }

val verifyRpmBuildTools =
    tasks.register("verifyRpmBuildTools") {
        onlyIf { installerPlatform.os == "linux" }
        doLast {
            check(
                runCatching {
                    providers
                        .exec { commandLine("rpmbuild", "--version") }
                        .result
                        .get()
                        .exitValue == 0
                }.getOrDefault(false),
            ) {
                "RPM packaging requires rpmbuild. On Ubuntu/Debian, install it with: sudo apt install rpm"
            }
        }
    }

tasks.matching { it.name.startsWith("packageRelease") }.configureEach {
    dependsOn(verifyPackagingTools)
}
tasks.matching { it.name == "packageReleaseRpm" }.configureEach {
    dependsOn(verifyRpmBuildTools)
}

tasks.withType<AbstractElectronBuilderPackageTask>().configureEach {
    doFirst { delete(destinationDir) }
}

afterEvaluate {
    tasks.named<AbstractElectronBuilderPackageTask>("packageReleaseRpm") {
        packageVersion.set(project.version.toString())
    }
}

tasks
    .matching { it.name in setOf("createRuntimeImage", "createReleaseDistributable", "proguardReleaseJars") }
    .configureEach { mustRunAfter(verifyPackagingTools, verifyRpmBuildTools) }

val smokeTestPackagedRuntime =
    tasks.register("smokeTestPackagedRuntime") {
        group = "verification"
        description = "Opens and migrates an isolated database using the bundled release runtime."
        dependsOn("createReleaseDistributable")
        doLast {
            val appDir =
                layout.buildDirectory
                    .dir("compose/binaries/main-release/app")
                    .get()
                    .asFile
            val launcher =
                fileTree(appDir) {
                    include("*/bin/azkar", "*.app/Contents/MacOS/*", "*/azkar.exe")
                }.singleFile
            val profile = Files.createTempDirectory("azkar-package-smoke-")
            val log =
                layout.buildDirectory
                    .file("reports/packaging/smoke-test.log")
                    .get()
                    .asFile
            log.parentFile.mkdirs()
            try {
                Files.createFile(profile.resolve(".packaging-smoke-test"))
                val process =
                    ProcessBuilder(launcher.absolutePath, "--packaging-smoke-test")
                        .redirectErrorStream(true)
                        .redirectOutput(log)
                        .apply {
                            environment()["JAVA_TOOL_OPTIONS"] =
                                "\"-Duser.home=$profile\" \"-Dazkar.packaging.smoke.profile=$profile\" -Djava.awt.headless=true"
                        }.start()
                try {
                    check(process.waitFor(90, TimeUnit.SECONDS)) { "Packaged runtime timed out; see $log" }
                    check(process.exitValue() == 0 && "AZKAR_PACKAGING_SMOKE_OK" in log.readText()) {
                        "Packaged runtime failed; see $log"
                    }
                } finally {
                    process.destroyForcibly().waitFor()
                }
            } finally {
                profile.toFile().deleteRecursively()
            }
        }
    }

val stageInstallers =
    tasks.register<Sync>("stageInstallers") {
        group = "distribution"
        description = "Builds, checks and stages final installers under build/installers/${installerPlatform.id}."
        dependsOn("packageReleaseDistributionForCurrentOS", smokeTestPackagedRuntime)
        val releaseVersion = project.version.toString()
        val channel = if ("-beta." in releaseVersion) "beta" else "latest"
        val formats =
            when (installerPlatform.os) {
                "linux" -> listOf("deb" to "deb", "rpm" to "rpm", "AppImage" to "AppImage")
                "windows" -> listOf("nsis" to "exe")
                else -> listOf("dmg" to "dmg", "zip" to "zip")
            }
        val output = rootProject.layout.buildDirectory.dir("installers/${installerPlatform.id}")
        into(output)
        from(layout.buildDirectory.dir("compose/binaries/main-release")) {
            formats.forEach { (directory, extension) ->
                include("$directory/azkar-$releaseVersion-${installerPlatform.id}.$extension")
                include("$directory/azkar-$releaseVersion-${installerPlatform.id}.$extension.blockmap")
                include("$directory/$channel*.yml")
            }
        }
        doFirst {
            val packaged =
                layout.buildDirectory
                    .dir("compose/binaries/main-release")
                    .get()
                    .asFile
            formats.forEach { (directory, extension) ->
                check(packaged.resolve("$directory/azkar-$releaseVersion-${installerPlatform.id}.$extension").isFile) {
                    "Missing $extension installer for ${installerPlatform.id}"
                }
                check(
                    packaged
                        .resolve(directory)
                        .listFiles()
                        .orEmpty()
                        .any { it.extension == "yml" && it.name.startsWith(channel) },
                ) { "Missing update metadata for $directory" }
            }
            if (installerPlatform.os == "linux") {
                fun inspectPackage(vararg arguments: String) =
                    providers
                        .exec { commandLine(*arguments) }
                        .standardOutput.asText
                        .get()
                        .trim()
                val expectedVersion = releaseVersion.replace('-', '~')
                val deb = packaged.resolve("deb/azkar-$releaseVersion-${installerPlatform.id}.deb").absolutePath
                val rpm = packaged.resolve("rpm/azkar-$releaseVersion-${installerPlatform.id}.rpm").absolutePath
                val rpmDatabase =
                    layout.buildDirectory
                        .dir("tmp/rpm-inspection")
                        .get()
                        .asFile
                        .apply { mkdirs() }
                check(inspectPackage("dpkg-deb", "-f", deb, "Version") == expectedVersion) {
                    "Wrong internal DEB version"
                }
                check(inspectPackage("dpkg-deb", "-f", deb, "Architecture") == "amd64") {
                    "Wrong DEB architecture"
                }
                check(
                    inspectPackage("rpm", "--dbpath", rpmDatabase.absolutePath, "-qp", "--qf", "%{VERSION}", rpm) ==
                        expectedVersion,
                ) { "Wrong internal RPM version" }
                check(
                    inspectPackage("rpm", "--dbpath", rpmDatabase.absolutePath, "-qp", "--qf", "%{ARCH}", rpm) ==
                        "x86_64",
                ) { "Wrong RPM architecture" }
            }
        }
        doLast {
            val staged = output.get().asFile
            val hashes =
                staged
                    .walkTopDown()
                    .filter { it.isFile && it.name != "SHA256SUMS" }
                    .sortedBy { it.relativeTo(staged).invariantSeparatorsPath }
                    .map { file ->
                        val digest = MessageDigest.getInstance("SHA-256")
                        file.inputStream().use { stream ->
                            val buffer = ByteArray(64 * 1024)
                            var count = stream.read(buffer)
                            while (count >= 0) {
                                digest.update(buffer, 0, count)
                                count = stream.read(buffer)
                            }
                        }
                        val hash = digest.digest().joinToString("") { "%02x".format(it) }
                        "$hash  ${file.relativeTo(staged).invariantSeparatorsPath}"
                    }.toList()
            staged.resolve("SHA256SUMS").writeText(hashes.joinToString("\n", postfix = "\n"))
            logger.lifecycle("Installers and checksums: $staged")
        }
    }

tasks.register("packageCurrentOS") {
    group = "distribution"
    description = "Builds the configured Nucleus installer formats for this host OS."
    dependsOn(stageInstallers)
}
