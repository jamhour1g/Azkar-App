#!/usr/bin/env -S kotlinc -script

/*
 * Appends platform-grouped installer links to a GitHub release notes file.
 * Run from the repository root:
 *
 *   ./scripts/release_download_index.main.kts OWNER/REPO TAG NOTES_FILE
 *
 * Requires the GitHub CLI (`gh`) to be authenticated. `.main.kts` lets the script run directly
 * with `kotlinc -script`, following the same convention as `generate_seed_migration.main.kts`.
 */

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND

require(args.size == 3) {
    "Usage: release_download_index.main.kts OWNER/REPO TAG NOTES_FILE"
}

val (repository, tag, notesFile) = args
val process = ProcessBuilder(
    "gh", "release", "view", tag,
    "--repo", repository,
    "--json", "assets",
    "--jq", ".assets[].name",
).redirectErrorStream(true).start()
val commandOutput = process.inputStream.bufferedReader().readText()
check(process.waitFor() == 0) {
    "Could not list assets for GitHub release '$tag': $commandOutput"
}

val assetNames = commandOutput.lineSequence().filter { it.isNotBlank() }.toList()
val groups = listOf(
    "Linux" to { name: String -> name.endsWith(".deb") || name.endsWith(".rpm") || name.endsWith(".tar.gz") },
    "Windows" to { name: String ->
        name.endsWith(".msix") ||
            (name.endsWith(".msi") || name.endsWith(".exe") || name.endsWith(".zip")) &&
            "-windows-" in name
    },
    "macOS" to { name: String -> name.endsWith(".zip") && "-mac-" in name },
)
val serverUrl = System.getenv("GITHUB_SERVER_URL")?.trimEnd('/') ?: "https://github.com"
val downloadBase = "$serverUrl/$repository/releases/download/${encodePathSegment(tag)}"

val index = buildString {
    appendLine("## Downloads by platform")
    appendLine()
    groups.forEach { (platform, matches) ->
        val packages = assetNames.filter(matches).sorted()
        if (packages.isEmpty()) return@forEach
        appendLine("### $platform")
        appendLine()
        packages.forEach { name ->
            appendLine("- [`$name`]($downloadBase/${encodePathSegment(name)})")
        }
        appendLine()
    }
}

Files.writeString(Path.of(notesFile), index, UTF_8, APPEND)

fun encodePathSegment(value: String): String = URLEncoder.encode(value, UTF_8).replace("+", "%20")
