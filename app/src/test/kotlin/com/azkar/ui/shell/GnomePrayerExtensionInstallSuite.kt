package com.azkar.ui.shell

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream

private const val EXTENSION_ID = "azkar-prayers@azkar.app"
private const val EXTENSION_PATH = "gnome-shell/extensions/$EXTENSION_ID"

@Suppress("unused")
val GnomePrayerExtensionInstallSuite by testSuite("GNOME prayer extension installation") {
    test("native package backs up per-user copies once and keeps unrelated extensions") {
        withExtensionDirectories { _, userData, systemData ->
            val system = createBundle(systemData.resolve(EXTENSION_PATH))
            val user = Files.createDirectories(userData.resolve(EXTENSION_PATH))
            unpackLegacyExtension(user)
            val oldScript = Files.readString(user.resolve("extension.js"))
            val neighbor = Files.createDirectory(user.parent.resolve("another-extension"))

            assertThat(prepareGnomePrayerExtension(userData, listOf(systemData), system, false))
                .isEqualTo(GnomeExtensionStatus.SIGN_IN_REQUIRED)
            assertThat(prepareGnomePrayerExtension(userData, listOf(systemData), system, false))
                .isEqualTo(GnomeExtensionStatus.AVAILABLE)

            assertThat(user).doesNotExist()
            assertThat(neighbor).exists()
            assertThat(Files.readString(system.resolve("extension.js"))).isEqualTo("new extension")
            Files.list(user.parent.parent.resolve("azkar-extension-backups")).use { entries ->
                val backup = entries.toList().single()
                assertThat(
                    Files.readString(backup.resolve(EXTENSION_ID).resolve("extension.js")),
                ).isEqualTo(oldScript)
                Files.move(backup.resolve(EXTENSION_ID), user)
                assertThat(prepareGnomePrayerExtension(userData, listOf(systemData), system, false))
                    .isEqualTo(GnomeExtensionStatus.LOCAL_OVERRIDE)
            }
        }
    }

    test("a one-byte customization stays active until the user chooses the package") {
        withExtensionDirectories { _, userData, systemData ->
            val system = createBundle(systemData.resolve(EXTENSION_PATH))
            val user = Files.createDirectories(userData.resolve(EXTENSION_PATH))
            unpackLegacyExtension(user)
            val stylesheet = user.resolve("stylesheet.css")
            val bytes = Files.readAllBytes(stylesheet)
            bytes[0] = 0
            Files.write(stylesheet, bytes)

            assertThat(prepareGnomePrayerExtension(userData, listOf(systemData), system, false))
                .isEqualTo(GnomeExtensionStatus.LOCAL_OVERRIDE)
            assertThat(Files.readAllBytes(stylesheet)).isEqualTo(bytes)
            assertThat(prepareGnomePrayerExtension(userData, listOf(systemData), system, false, usePackagedCopy = true))
                .isEqualTo(GnomeExtensionStatus.SIGN_IN_REQUIRED)
            assertThat(user).doesNotExist()
        }
    }

    test("a marked AppImage copy migrates on switching to a native package") {
        withExtensionDirectories { _, userData, systemData ->
            val system = createBundle(systemData.resolve(EXTENSION_PATH))
            val user = Files.createDirectories(userData.resolve(EXTENSION_PATH))
            Files.createFile(user.resolve(".azkar-bundle"))

            assertThat(prepareGnomePrayerExtension(userData, listOf(systemData), system, false))
                .isEqualTo(GnomeExtensionStatus.SIGN_IN_REQUIRED)
            assertThat(user).doesNotExist()
        }
    }

    test("a developer symlink and legacy bundles with extra files remain personal copies") {
        withExtensionDirectories { root, userData, systemData ->
            val system = createBundle(systemData.resolve(EXTENSION_PATH))
            val user = Files.createDirectories(userData.resolve(EXTENSION_PATH))
            unpackLegacyExtension(user)
            Files.createFile(user.resolve("notes"))
            assertThat(prepareGnomePrayerExtension(userData, listOf(systemData), system, false))
                .isEqualTo(GnomeExtensionStatus.LOCAL_OVERRIDE)
            if (System.getProperty("os.name") == "Linux") {
                val checkout = Files.move(user, root.resolve("checkout"))
                Files.delete(checkout.resolve("notes"))
                Files.createSymbolicLink(user, checkout)
                assertThat(prepareGnomePrayerExtension(userData, listOf(systemData), system, false))
                    .isEqualTo(GnomeExtensionStatus.LOCAL_OVERRIDE)
                assertThat(Files.isSymbolicLink(user)).isTrue()
            }
        }
    }

    test("a development build leaves its per-user extension in place") {
        withExtensionDirectories { root, userData, systemData ->
            createBundle(systemData.resolve(EXTENSION_PATH))
            val devBundle = createBundle(root.resolve("dev"))
            val user = Files.createDirectories(userData.resolve(EXTENSION_PATH))
            Files.writeString(user.resolve("metadata.json"), "{}")

            assertThat(
                prepareGnomePrayerExtension(userData, listOf(systemData), devBundle, isAppImage = false),
            ).isEqualTo(GnomeExtensionStatus.AVAILABLE)
            assertThat(user).exists()
        }
    }

    test("native package keeps the per-user extension when the system bundle is incomplete") {
        withExtensionDirectories { _, userData, systemData ->
            val bundle = createBundle(systemData.resolve(EXTENSION_PATH))
            Files.delete(bundle.resolve("extension.js"))
            val user = Files.createDirectories(userData.resolve(EXTENSION_PATH))
            Files.writeString(user.resolve("metadata.json"), "{}")

            prepareGnomePrayerExtension(userData, listOf(systemData), bundle, isAppImage = false)

            assertThat(user).exists()
        }
    }

    test("AppImage updates its managed extension when there is no system copy") {
        withExtensionDirectories { root, userData, systemData ->
            val bundle = createBundle(root.resolve("bundle"))
            val user = Files.createDirectories(userData.resolve(EXTENSION_PATH))
            Files.writeString(user.resolve(".azkar-bundle"), "")
            Files.writeString(user.resolve("metadata.json"), "{}")
            Files.writeString(user.resolve("extension.js"), "old extension")

            assertThat(
                prepareGnomePrayerExtension(userData, listOf(systemData), bundle, isAppImage = true),
            ).isEqualTo(GnomeExtensionStatus.AVAILABLE)

            assertThat(Files.readString(user.resolve("extension.js"))).isEqualTo("new extension")
        }
    }
}

private suspend fun withExtensionDirectories(test: (Path, Path, Path) -> Unit) =
    withContext(Dispatchers.IO) {
        val root = Files.createTempDirectory("azkar-gnome-test")
        try {
            test(root, root.resolve("user"), root.resolve("system"))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

private fun createBundle(bundle: Path): Path {
    Files.createDirectories(bundle)
    listOf("panelState.js", "stylesheet.css", "azkar.png", "metadata.json").forEach {
        Files.writeString(bundle.resolve(it), "{}")
    }
    listOf(
        "bell",
        "bell-off",
        "calendar",
        "house",
        "moon",
        "sun",
        "sunrise",
        "sunset",
        "x",
        "auto-awesome",
        "open-with",
        "visibility",
        "visibility-off",
    ).forEach { Files.createFile(bundle.resolve("$it-symbolic.svg")) }
    Files.writeString(bundle.resolve("extension.js"), "new extension")
    return bundle
}

private fun unpackLegacyExtension(directory: Path) {
    val resource = checkNotNull(GnomeExtensionStatus::class.java.getResourceAsStream("/gnome/legacy-extension.zip"))
    ZipInputStream(resource).use { zip ->
        generateSequence { zip.nextEntry }.forEach { entry -> Files.copy(zip, directory.resolve(entry.name)) }
    }
}
