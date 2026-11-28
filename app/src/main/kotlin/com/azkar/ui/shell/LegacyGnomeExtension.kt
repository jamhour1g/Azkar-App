package com.azkar.ui.shell

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

// The five-file legacy distribution predates the symbolic icons and version-name metadata.
// Remove this one-time migration and its test fixture when support for upgrades from this bundle ends.
private val legacyFiles =
    mapOf(
        "azkar.png" to (12968L to "be58938ed29f6e4d243b20d84b2b7405c85bfd2063899c1db600086ce91e175e"),
        "extension.js" to (23059L to "9c7539820949d77312dd78013e1abd585cfacaf46c7ac08ed2954e762d8bd8f0"),
        "metadata.json" to (273L to "7064c95e1453e0c0d8614ea409f166f1e44691d46c3f58e7dcbc89a33dc46752"),
        "panelState.js" to (1883L to "7bf8d23154f0b30108673ae0e4d64f5be7649607ded784bcb87e1b9e044a107d"),
        "stylesheet.css" to (1460L to "2921e03c790a49e8790c2423e329a526dfe602fffb67421ffe852f8430a44b6d"),
    )

internal fun isAzkarUserExtension(directory: Path): Boolean {
    if (Files.isSymbolicLink(directory)) return false
    return Files.isRegularFile(directory.resolve(BUNDLE_MARKER), NOFOLLOW_LINKS) || matchesLegacyBundle(directory)
}

private fun matchesLegacyBundle(directory: Path): Boolean =
    Files.list(directory).use { entries ->
        val names = entries.map { it.fileName.toString() }.toList().toSet()
        names == legacyFiles.keys &&
            legacyFiles.all { (name, fingerprint) ->
                val file = directory.resolve(name)
                val (size, hash) = fingerprint
                Files.isRegularFile(file, NOFOLLOW_LINKS) && Files.size(file) == size && sha256(file) == hash
            }
    }

private fun sha256(file: Path): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))
    return HexFormat.of().formatHex(digest)
}
