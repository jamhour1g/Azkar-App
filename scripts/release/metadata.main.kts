#!/usr/bin/env -S bash scripts/release/kotlin.sh
@file:Import("release-rules.main.kts", "github-client.main.kts")

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE
import java.time.Instant
import kotlin.system.exitProcess

fun environment(name: String): String =
    requireNotNull(System.getenv(name)?.takeIf { it.isNotBlank() }) { "$name is required" }

// Git process I/O is blocking; keep it off the coroutine's event-loop thread.
suspend fun capture(vararg arguments: String): String =
    withContext(Dispatchers.IO) {
        val process = ProcessBuilder(*arguments).redirectError(ProcessBuilder.Redirect.INHERIT).start()
        process.outputStream.close()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exit = process.waitFor()
        check(exit == 0) { "${arguments.take(2).joinToString(" ")} failed (exit $exit)" }
        output.trimEnd('\r', '\n')
    }

suspend fun <T> github(block: suspend (GitHubReleaseClient) -> T): T =
    GitHubReleaseClient(
        environment("GITHUB_REPOSITORY"),
        System.getenv("GH_TOKEN")?.takeIf { it.isNotBlank() } ?: System.getenv("GITHUB_TOKEN"),
    ).use { block(it) }

fun append(
    name: String,
    text: String,
) {
    Files.writeString(Path.of(environment(name)), "$text\n", CREATE, APPEND)
}

suspend fun selectBatch() {
    val decision =
        if (System.getenv("ACT") == "true" || environment("GITHUB_REF").startsWith("refs/tags/v")) {
            BatchDecision(true, "Stable tag or local rehearsal")
        } else {
            require(environment("GITHUB_REF") == "refs/heads/dev") { "Dev releases must build dev" }
            val event = environment("EVENT")
            require(event in setOf("schedule", "workflow_dispatch")) { "Unsupported Dev release event: $event" }
            github { client ->
                selectDevBatch(client.releases(), event, Instant.now()) { previous ->
                    client.comparison(previous, environment("GITHUB_SHA"))
                }
            }
        }
    append("GITHUB_OUTPUT", "publish=${decision.publish}")
    append("GITHUB_STEP_SUMMARY", "Dev/Stable release decision: ${decision.reason}; publish=${decision.publish}")
    println(decision.reason)
}

suspend fun resolveVersion() {
    val version =
        when {
            System.getenv("ACT") == "true" -> ReleaseVersion("0.0.0-beta.1", "dev")
            environment("GITHUB_REF").startsWith("refs/tags/v") -> resolveStableVersion(environment("GITHUB_REF_NAME"))
            else -> {
                require(environment("GITHUB_REF") == "refs/heads/dev") { "Dev releases must build dev" }
                github { nextDevVersion(it.releases(), capture("git", "tag", "--list", "v*-beta.*").lines()) }
            }
        }
    append(
        "GITHUB_OUTPUT",
        "app_version=${version.appVersion}\nrelease_tag=${version.tag}\nchannel=${version.channel}\nprerelease=${version.prerelease}",
    )
}

runBlocking {
    when (args.singleOrNull()) {
        "select-batch" -> selectBatch()
        "resolve-version" -> resolveVersion()
        "latest-dev-tag" -> println(github { latestDevRelease(it.releases())?.tag.orEmpty() })
        "latest-stable-tag" -> println(github { latestStableRelease(it.releases())?.tag.orEmpty() })
        "verify-stable-source" -> {
            capture("git", "fetch", "origin", "production")
            capture("git", "merge-base", "--is-ancestor", environment("GITHUB_SHA"), "origin/production")
            Unit
        }
        else -> {
            System.err.println(
                "Usage: metadata.main.kts {select-batch|resolve-version|latest-dev-tag|latest-stable-tag|verify-stable-source}",
            )
            exitProcess(2)
        }
    }
}
