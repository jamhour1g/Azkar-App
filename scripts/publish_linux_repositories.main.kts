#!/usr/bin/env -S kotlinc -script

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

fun run(
    arguments: List<String>,
    workingDirectory: Path? = null,
    standardInput: String? = null,
    standardOutput: Path? = null,
): Int {
    val process =
        ProcessBuilder(arguments)
            .apply {
                workingDirectory?.let { directory(it.toFile()) }
                standardOutput?.let { redirectOutput(it.toFile()) }
                redirectErrorStream(standardOutput == null)
            }.start()
    process.outputStream.bufferedWriter().use { writer -> standardInput?.let(writer::write) }
    val output = process.inputStream.bufferedReader().use { it.readText() }
    if (output.isNotBlank()) print(output)
    return process.waitFor()
}

fun requireSuccess(
    description: String,
    vararg arguments: String,
    workingDirectory: Path? = null,
    standardInput: String? = null,
    standardOutput: Path? = null,
) {
    check(run(arguments.toList(), workingDirectory, standardInput, standardOutput) == 0) {
        "Command failed while $description: ${arguments.joinToString(" ")}"
    }
}

fun sign(
    keyId: String,
    passphrase: String,
    vararg arguments: String,
) =
    requireSuccess(
        "signing repository metadata",
        "gpg",
        "--batch",
        "--yes",
        "--pinentry-mode",
        "loopback",
        "--passphrase-fd",
        "0",
        "--local-user",
        keyId,
        *arguments,
        standardInput = "$passphrase\n",
    )

fun publish(
    channel: String,
    assets: Path,
    pages: Path,
    publicKey: Path,
    keyId: String,
    passphrase: String,
) {
    require(channel in setOf("stable", "dev")) { "Unsupported repository channel: $channel" }
    require(Files.isDirectory(assets)) { "Missing release assets directory: $assets" }
    require(Files.exists(pages.resolve(".git"))) { "Not a GitHub Pages worktree: $pages" }
    require(Files.isRegularFile(publicKey)) { "Missing exported repository public key: $publicKey" }
    require(keyId.matches(Regex("[A-Fa-f0-9]{16,}"))) { "Invalid repository signing key fingerprint" }

    val debs = Files.list(assets).use { stream -> stream.filter { it.fileName.toString().endsWith(".deb") }.toList() }
    val rpms = Files.list(assets).use { stream -> stream.filter { it.fileName.toString().endsWith(".rpm") }.toList() }
    require(debs.isNotEmpty() && rpms.isNotEmpty()) { "Release assets must include at least one DEB and one RPM" }

    val apt = pages.resolve("apt")
    val aptPool = apt.resolve("pool/$channel/main/a/azkar")
    val aptBinary = apt.resolve("dists/$channel/main/binary-amd64")
    val rpm = pages.resolve("rpm/$channel")
    Files.createDirectories(aptPool)
    Files.createDirectories(aptBinary)
    Files.createDirectories(rpm)
    Files.createDirectories(pages.resolve("keys"))
    Files.copy(publicKey, pages.resolve("keys/azkar-repo.asc"), REPLACE_EXISTING)
    debs.forEach { Files.copy(it, aptPool.resolve(it.fileName), REPLACE_EXISTING) }
    rpms.forEach { Files.copy(it, rpm.resolve(it.fileName), REPLACE_EXISTING) }

    val packages = aptBinary.resolve("Packages")
    requireSuccess("building APT package metadata", "apt-ftparchive", "packages", "pool/$channel", workingDirectory = apt, standardOutput = packages)
    requireSuccess("compressing APT package metadata", "gzip", "-9fk", packages.toString())

    val release = apt.resolve("dists/$channel/Release")
    requireSuccess(
        "building the APT release index",
        "apt-ftparchive",
        "-o",
        "APT::FTPArchive::Release::Origin=Azkar",
        "-o",
        "APT::FTPArchive::Release::Label=Azkar",
        "-o",
        "APT::FTPArchive::Release::Suite=$channel",
        "-o",
        "APT::FTPArchive::Release::Codename=$channel",
        "-o",
        "APT::FTPArchive::Release::Architectures=amd64",
        "-o",
        "APT::FTPArchive::Release::Components=main",
        "release",
        "dists/$channel",
        workingDirectory = apt,
        standardOutput = release,
    )
    sign(keyId, passphrase, "--clearsign", "--output", apt.resolve("dists/$channel/InRelease").toString(), release.toString())
    sign(
        keyId,
        passphrase,
        "--armor",
        "--detach-sign",
        "--output",
        apt.resolve("dists/$channel/Release.gpg").toString(),
        release.toString(),
    )

    requireSuccess("building RPM repository metadata", "createrepo_c", "--update", ".", workingDirectory = rpm)
    val rpmMetadata = rpm.resolve("repodata/repomd.xml")
    val rpmSignature = rpm.resolve("repodata/repomd.xml.asc")
    sign(keyId, passphrase, "--armor", "--detach-sign", "--output", rpmSignature.toString(), rpmMetadata.toString())

    requireSuccess("verifying the APT InRelease signature", "gpg", "--verify", apt.resolve("dists/$channel/InRelease").toString())
    requireSuccess(
        "verifying the APT Release signature",
        "gpg",
        "--verify",
        apt.resolve("dists/$channel/Release.gpg").toString(),
        release.toString(),
    )
    requireSuccess("verifying the RPM metadata signature", "gpg", "--verify", rpmSignature.toString(), rpmMetadata.toString())

    val workspace = System.getenv("GITHUB_WORKSPACE") ?: error("GITHUB_WORKSPACE is required")
    val downloadPage = Path.of(workspace, "docs/download.html")
    require(Files.isRegularFile(downloadPage)) { "Missing download page: $downloadPage" }
    Files.copy(downloadPage, pages.resolve("download.html"), REPLACE_EXISTING)
    check(pages.resolve("dev-preview").toFile().deleteRecursively()) { "Could not remove the obsolete dev-preview repository" }
    requireSuccess("setting the GitHub Actions commit identity", "git", "config", "user.name", "github-actions[bot]", workingDirectory = pages)
    requireSuccess(
        "setting the GitHub Actions commit email",
        "git",
        "config",
        "user.email",
        "41898282+github-actions[bot]@users.noreply.github.com",
        workingDirectory = pages,
    )
    requireSuccess("staging repository files", "git", "add", "-A", workingDirectory = pages)
    when (run(listOf("git", "diff", "--cached", "--quiet"), pages)) {
        0 -> println("Repository metadata already matches this release.")
        1 -> {
            requireSuccess("committing repository metadata", "git", "commit", "-m", "release: publish $channel repositories", workingDirectory = pages)
            requireSuccess("publishing repository metadata", "git", "push", "origin", "HEAD:gh-pages", workingDirectory = pages)
        }
        else -> error("Could not check staged repository changes")
    }
}

require(args.size == 5) {
    "Usage: publish_linux_repositories.main.kts <stable|dev> <release-assets> <gh-pages-worktree> <public-key-file> <key-fingerprint>"
}
val passphrase = System.getenv("REPO_PASSPHRASE") ?: error("REPO_PASSPHRASE is required")
publish(args[0], Path.of(args[1]), Path.of(args[2]), Path.of(args[3]), args[4], passphrase)
