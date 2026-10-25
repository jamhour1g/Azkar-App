#!/usr/bin/env -S kotlinc -script

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64

val platformFormats =
    linkedMapOf(
        "linux-x64" to setOf("deb", "rpm", "AppImage"),
        "windows-x64" to setOf("exe"),
        "macos-x64" to setOf("dmg", "zip"),
        "macos-arm64" to setOf("dmg", "zip"),
    )
val manifestPattern = Regex("^(latest|beta)(-mac|-linux)?\\.yml$")

data class ManifestFile(
    val url: String,
    val sha512: String,
    val size: Long,
    val blockMapSize: Long?,
)

data class Manifest(
    val version: String,
    val files: List<ManifestFile>,
    val releaseDate: String?,
)

fun installerName(
    version: String,
    platform: String,
    format: String,
): String {
    val suffix = if (platform == "windows-x64" && format == "exe") "-nsis" else ""
    return "azkar-$version-$platform$suffix.$format"
}

fun scalar(value: String): String = value.trim().removeSurrounding("'").removeSurrounding("\"")

fun readManifest(path: Path): Manifest {
    val lines = Files.readAllLines(path)
    val version = lines.single { it.startsWith("version:") }.substringAfter(':').let(::scalar)
    val files = mutableListOf<ManifestFile>()
    var fields: MutableMap<String, String>? = null

    fun finish() {
        fields?.let { entry ->
            check(entry.keys.all { it in setOf("url", "sha512", "size", "blockMapSize") }) {
                "Unsupported update entry in $path: ${entry.keys}"
            }
            files +=
                ManifestFile(
                    entry.getValue("url"),
                    entry.getValue("sha512"),
                    entry.getValue("size").toLong(),
                    entry["blockMapSize"]?.toLong(),
                )
        }
        fields = null
    }
    var inFiles = false
    for (line in lines) {
        when {
            line == "files:" -> {
                check(!inFiles)
                inFiles = true
            }
            inFiles && line.startsWith("  - ") -> {
                finish()
                fields = linkedMapOf()
                val (key, value) = line.removePrefix("  - ").split(':', limit = 2)
                fields!![key] = scalar(value)
            }
            inFiles && line.startsWith("    ") -> {
                val (key, value) = line.trim().split(':', limit = 2)
                check(fields != null && fields!!.put(key, scalar(value)) == null) { "Duplicate/invalid field in $path" }
            }
            inFiles && line.isNotBlank() -> {
                finish()
                inFiles = false
            }
        }
    }
    finish()
    check(files.isNotEmpty()) { "No update files in $path" }
    val releaseDate = lines.singleOrNull { it.startsWith("releaseDate:") }?.substringAfter(':')?.let(::scalar)
    releaseDate?.let(Instant::parse)
    return Manifest(version, files, releaseDate)
}

fun digest(
    path: Path,
    algorithm: String,
): ByteArray {
    val hash = MessageDigest.getInstance(algorithm)
    Files.newInputStream(path).use { stream ->
        val buffer = ByteArray(64 * 1024)
        var count = stream.read(buffer)
        while (count >= 0) {
            hash.update(buffer, 0, count)
            count = stream.read(buffer)
        }
    }
    return hash.digest()
}

fun mergeRelease(
    sourceDir: Path,
    outputDir: Path,
    version: String,
) {
    check(Regex("\\d+\\.\\d+\\.\\d+(-beta\\.\\d+(\\.\\d+)*)?").matches(version)) { "Invalid release version: $version" }
    check(
        !Files.exists(outputDir) ||
            Files.list(outputDir).use {
                !it.findAny().isPresent
            },
    ) { "Output must be empty: $outputDir" }
    val channel = if ("-beta." in version) "beta" else "latest"
    val packages = linkedMapOf<String, Path>()
    val blockmaps = linkedMapOf<String, Path>()
    val manifests = linkedMapOf<String, MutableList<ManifestFile>>()
    val manifestDates = linkedMapOf<String, MutableList<String>>()
    val consumedMetadata = linkedSetOf<Path>()
    val allPaths = Files.walk(sourceDir).use { stream -> stream.filter(Files::isRegularFile).sorted().toList() }
    check(allPaths.none(Files::isSymbolicLink)) { "Symbolic links are not valid release inputs" }
    for ((platform, formats) in platformFormats) {
        val platformDir = sourceDir.resolve(platform)
        check(Files.isDirectory(platformDir)) { "Missing platform: $platform" }
        val paths = allPaths.filter { it.startsWith(platformDir) }
        val expectedNames = formats.mapTo(linkedSetOf()) { installerName(version, platform, it) }
        val foundNames =
            paths
                .filter { it.fileName.toString().substringAfterLast('.') in formats }
                .map { path ->
                    val name = path.fileName.toString()
                    check(packages.put(name, path) == null) { "Duplicate artifact: $name" }
                    name
                }.toSet()
        check(foundNames == expectedNames) { "$platform expected $expectedNames, found $foundNames" }
        val expectedManifest =
            channel +
                when {
                    platform.startsWith("linux") -> "-linux"
                    platform.startsWith("macos") -> "-mac"
                    else -> ""
                } + ".yml"
        val metadata = paths.filter { manifestPattern.matches(it.fileName.toString()) }
        check(metadata.isNotEmpty() && metadata.all { it.fileName.toString() == expectedManifest }) {
            "Missing or wrong-channel manifests for $platform"
        }
        val referencedNames = linkedSetOf<String>()
        for (path in metadata) {
            consumedMetadata.add(path)
            val manifest = readManifest(path)
            manifest.releaseDate?.let { manifestDates.getOrPut(expectedManifest) { mutableListOf() }.add(it) }
            for (file in manifest.files) {
                check(file.url in expectedNames) { "Unknown or unsafe artifact URL in $path: ${file.url}" }
                val nativeVersion =
                    file.url.endsWith(".exe") ||
                        file.url.endsWith(".dmg") ||
                        file.url.endsWith(".zip")
                val nativeBase = version.substringBefore('-')
                val components = nativeBase.split('.')
                val macMajor = components[0].toBigInteger() + java.math.BigInteger.ONE
                val macVersion = "$macMajor.${components[1]}.${components[2]}"
                check(
                    manifest.version == version ||
                        (nativeVersion && manifest.version == nativeBase) ||
                        (platform.startsWith("macos") && manifest.version == macVersion),
                ) {
                    "Unexpected version ${manifest.version} in $path (release $version)"
                }
                val artifact = packages.getValue(file.url)
                check(Files.size(artifact) == file.size && file.size > 0) { "Size mismatch: ${file.url}" }
                check(Base64.getEncoder().encodeToString(digest(artifact, "SHA-512")) == file.sha512) {
                    "SHA-512 mismatch: ${file.url}"
                }
                check(
                    file.blockMapSize == null || file.blockMapSize in 1..file.size,
                ) { "Invalid blockMapSize: ${file.url}" }
                check(referencedNames.add(file.url)) { "Duplicate update entry: ${file.url}" }
                manifests.getOrPut(expectedManifest) { mutableListOf() }.add(file)
            }
        }
        check(referencedNames == expectedNames) { "Update metadata does not cover every $platform installer" }
        paths.filter { it.fileName.toString().endsWith(".blockmap") }.forEach { path ->
            val name = path.fileName.toString()
            check(name.removeSuffix(".blockmap") in expectedNames && blockmaps.put(name, path) == null) {
                "Unexpected/duplicate blockmap: $name"
            }
        }
    }
    val allowed =
        packages.values.toSet() + blockmaps.values + consumedMetadata +
            platformFormats.keys.map { sourceDir.resolve("$it/SHA256SUMS") }
    check(allPaths.all { it in allowed }) { "Unexpected files or platform in release input" }
    Files.createDirectories(outputDir)
    (packages + blockmaps).forEach { (name, path) -> Files.copy(path, outputDir.resolve(name), COPY_ATTRIBUTES) }
    manifests.forEach { (name, files) ->
        val text =
            buildString {
                appendLine("version: $version")
                appendLine("files:")
                files.sortedBy { it.url }.forEach { file ->
                    appendLine("  - url: ${file.url}")
                    appendLine("    sha512: ${file.sha512}")
                    appendLine("    size: ${file.size}")
                    file.blockMapSize?.let { appendLine("    blockMapSize: $it") }
                }
                manifestDates[name]?.maxOrNull()?.let { appendLine("releaseDate: '$it'") }
            }
        Files.writeString(outputDir.resolve(name), text)
    }
    val hashes =
        Files.list(outputDir).use { it.sorted().toList() }.map { path ->
            val hash = digest(path, "SHA-256").joinToString("") { "%02x".format(it) }
            "$hash  ${path.fileName}"
        }
    Files.writeString(outputDir.resolve("SHA256SUMS"), hashes.joinToString("\n", postfix = "\n"))
    println("Validated ${packages.size} installers and ${manifests.size} update manifests in $outputDir")
}

fun selfTest() {
    val root = Files.createTempDirectory("azkar-release-test-")
    val version = "1.2.3-beta.4.1"

    fun fixture(
        name: String,
        fixtureVersion: String = version,
    ): Path {
        val input = root.resolve(name)
        platformFormats.forEach { (platform, formats) ->
            formats.forEach { format ->
                val directory = Files.createDirectories(input.resolve("$platform/$format"))
                val filename = installerName(fixtureVersion, platform, format)
                val artifact = Files.writeString(directory.resolve(filename), "$platform $format")
                val manifestName =
                    (if ("-beta." in fixtureVersion) "beta" else "latest") +
                        when {
                            platform.startsWith("linux") -> "-linux"
                            platform.startsWith("macos") -> "-mac"
                            else -> ""
                        } + ".yml"
                Files.writeString(
                    directory.resolve(manifestName),
                    """
                    version: $fixtureVersion
                    files:
                      - url: $filename
                        sha512: ${Base64.getEncoder().encodeToString(digest(artifact, "SHA-512"))}
                        size: ${Files.size(artifact)}
                    releaseDate: '2026-10-25T00:00:00.000Z'
                    """.trimIndent(),
                )
            }
        }
        return input
    }

    fun rejects(
        name: String,
        change: (Path) -> Unit,
    ) {
        val input = fixture(name)
        change(input)
        check(
            runCatching {
                mergeRelease(input, root.resolve("$name-out"), version)
            }.isFailure,
        ) { "Accepted invalid $name input" }
    }
    try {
        mergeRelease(fixture("valid"), root.resolve("valid-out"), version)
        mergeRelease(fixture("stable", "1.2.3"), root.resolve("stable-out"), "1.2.3")
        check(readManifest(root.resolve("valid-out/beta-mac.yml")).files.size == 4)
        val nativeInput = fixture("native-version")
        val nativeManifest = nativeInput.resolve("windows-x64/exe/beta.yml")
        Files.writeString(
            nativeManifest,
            Files.readString(nativeManifest).replace("version: $version", "version: 1.2.3"),
        )
        mergeRelease(nativeInput, root.resolve("native-out"), version)
        check(readManifest(root.resolve("native-out/beta.yml")).version == version)
        val macInput = fixture("mac-native-version")
        val macManifest = macInput.resolve("macos-x64/dmg/beta-mac.yml")
        Files.writeString(macManifest, Files.readString(macManifest).replace("version: $version", "version: 2.2.3"))
        mergeRelease(macInput, root.resolve("mac-native-out"), version)
        check(readManifest(root.resolve("mac-native-out/beta-mac.yml")).version == version)
        rejects("wrong-windows-name") { input ->
            val source = input.resolve("windows-x64/exe/azkar-$version-windows-x64-nsis.exe")
            Files.move(source, source.resolveSibling("azkar-$version-windows-x64.exe"))
        }
        rejects("missing-arch") { it.resolve("macos-arm64").toFile().deleteRecursively() }
        rejects("missing-format") { Files.delete(it.resolve("linux-x64/rpm/azkar-$version-linux-x64.rpm")) }
        rejects("missing-manifest") { Files.delete(it.resolve("windows-x64/exe/beta.yml")) }
        rejects("wrong-channel") {
            Files.move(it.resolve("windows-x64/exe/beta.yml"), it.resolve("windows-x64/exe/latest.yml"))
        }
        rejects("unexpected-file") { Files.writeString(it.resolve("unexpected.exe"), "unexpected") }
        rejects("duplicate") { input ->
            val original = input.resolve("linux-x64/deb/azkar-$version-linux-x64.deb")
            Files.copy(original, original.parent.parent.resolve(original.fileName))
        }
        rejects("hash") { Files.writeString(it.resolve("linux-x64/deb/azkar-$version-linux-x64.deb"), "changed") }
        for ((name, from, to) in listOf(
            Triple("wrong-version", version, "1.2.3-beta.99.1"),
            Triple("unsafe-url", "url: azkar", "url: ../azkar"),
            Triple("wrong-hash", "sha512: ", "sha512: invalid"),
        )) {
            rejects(name) { input ->
                val metadata = input.resolve("windows-x64/exe/beta.yml")
                Files.writeString(metadata, Files.readString(metadata).replace(from, to))
            }
        }
        println("Release validation self-tests passed")
    } finally {
        root.toFile().deleteRecursively()
    }
}

if (args.contentEquals(arrayOf("self-test"))) {
    selfTest()
} else {
    require(
        args.size == 3,
    ) { "Usage: merge_nucleus_release.main.kts SOURCE_DIR OUTPUT_DIR RELEASE_VERSION | self-test" }
    mergeRelease(Path.of(args[0]), Path.of(args[1]), args[2])
}
