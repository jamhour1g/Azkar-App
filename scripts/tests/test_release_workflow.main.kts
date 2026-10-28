#!/usr/bin/env -S kotlinc -script
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.11.0")
@file:Import("../release/release-rules.main.kts")

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant

val scripts = Path.of("scripts/release").toAbsolutePath()
val mocks =
    mapOf(
        "gh" to
            """
            #!/usr/bin/env bash
            set -euo pipefail
            printf '%s\n' "${'$'}*" >> "${'$'}MOCK_LOG"
            if [[ "${'$'}1" == api ]]; then
              if [[ "${'$'}*" == *generate-notes* ]]; then echo 'Release notes';
              else echo 'Unexpected GitHub CLI API request' >&2; exit 1; fi
            elif [[ "${'$'}1 ${'$'}2" == 'release view' ]]; then
              if [[ "${'$'}*" == *'--json isDraft'* ]]; then echo "${'$'}MOCK_DRAFT";
              else [[ "${'$'}MOCK_EXISTS" == true ]]; fi
            fi
            """.trimIndent(),
        "metadata.main.kts" to
            """
            #!/usr/bin/env bash
            set -euo pipefail
            printf 'metadata %s\n' "${'$'}*" >> "${'$'}MOCK_LOG"
            case "${'$'}{1:-}" in
              latest-dev-tag) printf '%s\n' "${'$'}MOCK_LATEST_DEV" ;;
              latest-stable-tag) printf '%s\n' "${'$'}MOCK_LATEST_STABLE" ;;
              *) echo 'Unexpected metadata command' >&2; exit 2 ;;
            esac
            """.trimIndent(),
        "git" to
            """
            #!/usr/bin/env bash
            if [[ "${'$'}1" == merge-base ]]; then exit "${'$'}MOCK_ANCESTOR_EXIT";
            else printf '%s\n' "${'$'}MOCK_TAGS"; fi
            """.trimIndent(),
        "curl" to
            """
            #!/usr/bin/env bash
            set -euo pipefail
            output=''
            while (( ${'$'}# > 1 )); do
              if [[ "${'$'}1" == --output ]]; then output="${'$'}2"; shift; fi
              shift
            done
            [[ "${'$'}1" == https://example.invalid/* ]]
            file="${'$'}MOCK_SITE/${'$'}{1#https://example.invalid/}"
            if [[ -n "${'$'}output" ]]; then cp "${'$'}file" "${'$'}output"; else cat "${'$'}file"; fi
            """.trimIndent(),
    )

data class Release(
    val tag: String,
    val prerelease: Boolean = true,
    val draft: Boolean = false,
    val publishedAt: String? = "2026-10-28T00:00:00Z",
) {
    fun info(): ReleaseInfo = ReleaseInfo(tag, prerelease, draft, publishedAt?.let(Instant::parse))
}

data class ScriptResult(
    val exit: Int,
    val outputs: Map<String, String>,
    val calls: String,
    val log: String,
) {
    fun succeeds(): ScriptResult {
        check(exit == 0) { log }
        return this
    }
}

fun runScript(
    script: String,
    command: String,
    pages: List<List<Release>> = listOf(emptyList()),
    files: Map<String, String> = emptyMap(),
    environment: Map<String, String> = emptyMap(),
): ScriptResult {
    val root = Files.createTempDirectory("azkar-release-test-")
    try {
        files.forEach { (relative, content) ->
            val file = root.resolve(relative)
            Files.createDirectories(file.parent)
            Files.writeString(file, content)
        }
        val releases = pages.flatten().map { it.info() }
        mocks.forEach { (name, body) ->
            val file = Files.writeString(root.resolve(name), body)
            check(file.toFile().setExecutable(true))
        }
        val process =
            ProcessBuilder(
                if (script.endsWith(".main.kts")) {
                    listOf(scripts.resolve("kotlin.sh").toString(), scripts.resolve(script).toString(), command)
                } else {
                    // Copy shell entry points so their sibling metadata command can be mocked.
                    listOf("bash", Files.copy(scripts.resolve(script), root.resolve(script)).toString(), command)
                },
            ).directory(root.toFile())
                .redirectErrorStream(true)
        process.environment().putAll(
            mapOf(
                "PATH" to "$root:${System.getenv("PATH")}",
                "ACT" to "false",
                "EVENT" to "schedule",
                "GITHUB_REF" to "refs/heads/dev",
                "GITHUB_REF_NAME" to "dev",
                "GITHUB_REPOSITORY" to "example/azkar",
                "GITHUB_SHA" to "a".repeat(40),
                "GITHUB_OUTPUT" to root.resolve("outputs").toString(),
                "GITHUB_STEP_SUMMARY" to root.resolve("summary").toString(),
                "RUNNER_TEMP" to root.toString(),
                "MOCK_LATEST_DEV" to latestDevRelease(releases)?.tag.orEmpty(),
                "MOCK_LATEST_STABLE" to latestStableRelease(releases)?.tag.orEmpty(),
                "MOCK_LOG" to root.resolve("calls").toString(),
                "MOCK_TAGS" to "",
                "MOCK_ANCESTOR_EXIT" to "0",
                "MOCK_EXISTS" to "false",
                "MOCK_DRAFT" to "true",
                "MOCK_SITE" to root.resolve("mock-site").toString(),
                "PAGE_URL" to "https://example.invalid/",
                "CHANNEL" to "dev",
                "RELEASE_TAG" to "v1.2.4-beta.24",
                "APP_VERSION" to "1.2.4-beta.24",
                "RELEASE_CHANNEL" to "dev",
                "PRE_RELEASE" to "true",
            ) + environment,
        )
        val running = process.start()
        val log = running.inputStream.bufferedReader().use { it.readText() }

        fun read(name: String): String = root.resolve(name).let { if (Files.exists(it)) Files.readString(it) else "" }
        return ScriptResult(
            running.waitFor(),
            read("outputs").lineSequence().filter { it.isNotBlank() }.associate {
                it.substringBefore('=') to it.substringAfter('=')
            },
            read("calls"),
            log,
        )
    } finally {
        Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
    }
}

var passed = 0

suspend fun test(
    name: String,
    block: suspend () -> Unit,
) {
    try {
        block()
        passed++
        println("PASS: $name")
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        throw IllegalStateException("Failed: $name", error)
    }
}

runBlocking {
    test("sequential beta versions") {
        val stable = Release("v1.2.3", prerelease = false)
        val cases =
            listOf(
                Triple(listOf(listOf(stable)), emptyMap(), "1.2.4-beta.1"),
                Triple(listOf(listOf(Release("v1.2.4-beta.23.1"), stable)), emptyMap(), "1.2.4-beta.24"),
                Triple(
                    listOf(listOf(Release("v1.2.4-beta.9"), stable), listOf(Release("v1.2.4-beta.24"))),
                    emptyMap(),
                    "1.2.4-beta.25",
                ),
                Triple(listOf(listOf(Release("v1.2.4-beta.25", draft = true), stable)), emptyMap(), "1.2.4-beta.26"),
                Triple(
                    listOf(listOf(stable)),
                    mapOf("MOCK_TAGS" to "v1.2.4-beta.26\nv1.2.4-beta.2.9"),
                    "1.2.4-beta.27",
                ),
                Triple(
                    listOf(listOf(Release("v1.2.4", prerelease = false), Release("v1.2.4-beta.99"))),
                    emptyMap(),
                    "1.2.5-beta.1",
                ),
                Triple(listOf(emptyList()), emptyMap(), "0.1.2-beta.1"),
            )
        cases.forEach { (pages, environment, version) ->
            val result = nextDevVersion(pages.flatten().map { it.info() }, environment["MOCK_TAGS"].orEmpty().lines())
            check(result.appVersion == version) { result }
            check(result.tag == "v$version")
            check(result.prerelease)
        }
    }

    test("stable has no suffix") {
        listOf("v1.2.3", "v1.2.3.1", "v1.2.3-beta.1", "v01.2.3").forEach { tag ->
            val result = runCatching { resolveStableVersion(tag) }
            if (tag == "v1.2.3") {
                check(result.getOrThrow().appVersion == "1.2.3")
                check(!result.getOrThrow().prerelease)
            } else {
                check(result.isFailure)
            }
        }
    }

    test("local act does not query GitHub") {
        val result = runScript("metadata.main.kts", "resolve-version", environment = mapOf("ACT" to "true")).succeeds()
        check(result.outputs["app_version"] == "0.0.0-beta.1")
        check(result.calls.isEmpty())
    }

    test("Stable CLI emits non-prerelease metadata without HTTP") {
        val result =
            runScript(
                "metadata.main.kts",
                "resolve-version",
                environment = mapOf("GITHUB_REF" to "refs/tags/v1.2.3", "GITHUB_REF_NAME" to "v1.2.3"),
            ).succeeds()
        check(
            result.outputs ==
                mapOf(
                    "app_version" to "1.2.3",
                    "release_tag" to "v1.2.3",
                    "channel" to "stable",
                    "prerelease" to "false",
                ),
        ) { result.outputs }
        check(result.calls.isEmpty())
    }

    test("Dev prerelease label on new and resumed drafts") {
        listOf("false", "true").forEach { exists ->
            val result =
                runScript(
                    "github-release.sh",
                    "prepare-draft",
                    environment = mapOf("MOCK_EXISTS" to exists),
                ).succeeds()
            check((if (exists == "true") "--prerelease=true" else "--prerelease") in result.calls)
        }
    }

    test("published releases cannot be overwritten") {
        val result =
            runScript(
                "github-release.sh",
                "prepare-draft",
                environment = mapOf("MOCK_EXISTS" to "true", "MOCK_DRAFT" to "false"),
            )
        check(result.exit != 0)
        check("release upload" !in result.calls)
    }

    test("Dev batch decisions") {
        val previous = Release("v1.2.4-beta.23.1")
        val cases =
            listOf(
                Triple(emptyList(), emptyMap(), "true"),
                Triple(listOf(Release("v1.2.3", prerelease = false)), emptyMap(), "true"),
                Triple(listOf(previous), emptyMap(), "true"), // Exactly six hours after publication.
                Triple(listOf(previous), mapOf("MOCK_NOW" to "1793167199"), "false"),
                Triple(listOf(previous), mapOf("MOCK_COMPARISON" to "identical"), "false"),
                Triple(listOf(previous), mapOf("MOCK_COMPARISON" to "behind"), "false"),
                Triple(listOf(previous), mapOf("MOCK_COMPARISON" to "diverged"), "false"),
                Triple(listOf(previous), mapOf("EVENT" to "workflow_dispatch", "MOCK_NOW" to "1793167199"), "true"),
                Triple(
                    listOf(previous),
                    mapOf("EVENT" to "workflow_dispatch", "MOCK_COMPARISON" to "identical"),
                    "false",
                ),
            )
        cases.forEach { (releases, environment, publish) ->
            val now = Instant.ofEpochSecond(environment["MOCK_NOW"]?.toLong() ?: 1793167200L)
            val result =
                selectDevBatch(releases.map { it.info() }, environment["EVENT"] ?: "schedule", now) {
                    environment["MOCK_COMPARISON"] ?: "ahead"
                }
            check(result.publish.toString() == publish) { "$environment: $result" }
        }
    }

    test("batch uses latest published Dev across pages") {
        val pages =
            listOf(
                listOf(
                    Release("v1.2.4-beta.99", draft = true, publishedAt = null),
                    Release("v1.2.4-beta.22", publishedAt = "2026-10-27T00:00:00Z"),
                ),
                listOf(Release("v1.2.4-beta.23", publishedAt = "2026-10-28T05:00:00Z")),
            )
        val result =
            selectDevBatch(pages.flatten().map { it.info() }, "schedule", Instant.parse("2026-10-28T06:00:00Z")) {
                error("Comparison must not run during the six-hour interval")
            }
        check(!result.publish)
    }

    test("Stable and act bypass Dev batching") {
        listOf(mapOf("ACT" to "true"), mapOf("GITHUB_REF" to "refs/tags/v1.2.3")).forEach { environment ->
            val result = runScript("metadata.main.kts", "select-batch", environment = environment).succeeds()
            check(result.outputs["publish"] == "true")
            check(result.calls.isEmpty())
        }
    }

    test("notes start at latest published Dev") {
        val pages =
            listOf(
                listOf(Release("v1.2.4-beta.22", publishedAt = "2026-10-27T00:00:00Z")),
                listOf(Release("v1.2.4-beta.23", publishedAt = "2026-10-28T00:00:00Z")),
            )
        val result = runScript("github-release.sh", "prepare-draft", pages).succeeds()
        check("previous_tag_name=v1.2.4-beta.23" in result.calls)
    }

    test("Stable source must be on production") {
        listOf("0", "1").forEach { ancestor ->
            val result =
                runScript(
                    "metadata.main.kts",
                    "verify-stable-source",
                    environment =
                        mapOf(
                            "MOCK_ANCESTOR_EXIT" to ancestor,
                        ),
                )
            check((result.exit == 0) == (ancestor == "0")) { result.log }
        }
    }

    test("final publication labels") {
        listOf("true" to "false", "false" to "true").forEach { (prerelease, latest) ->
            val result =
                runScript(
                    "github-release.sh",
                    "publish",
                    environment =
                        mapOf(
                            "PRE_RELEASE" to prerelease,
                            "RELEASE_TAG" to if (prerelease == "true") "v1.2.4-beta.24" else "v1.2.4",
                        ),
                ).succeeds()
            check("--draft=false --prerelease=$prerelease --latest=$latest" in result.calls)
        }
    }

    test("deployed site checksums and paths") {
        val digest =
            MessageDigest.getInstance("SHA-256").digest("installer".toByteArray()).joinToString("") {
                "%02x".format(it)
            }
        listOf(
            Triple(digest, "./packages/app.deb", true),
            Triple("0".repeat(64), "./packages/app.deb", false),
            Triple(digest, "./../app.deb", false),
        ).forEach { (checksum, file, accepted) ->
            val result =
                runScript(
                    "repository-site.sh",
                    "verify",
                    files =
                        mapOf(
                            "mock-site/dev-version.txt" to "1.2.4-beta.24\n",
                            "mock-site/packages/app.deb" to "installer",
                            "expected/site-SHA256SUMS" to "$checksum  $file\n",
                        ),
                )
            check((result.exit == 0) == accepted) { result.log }
        }
    }

    test("unknown commands fail without external calls") {
        listOf("metadata.main.kts", "github-release.sh", "repository-site.sh").forEach { script ->
            val result = runScript(script, "typo")
            check(result.exit == 2)
            check(result.calls.isEmpty())
        }
    }

    test("Stable versions sort numerically and drafts do not become the base") {
        val releases =
            listOf(
                Release("v1.2.9", prerelease = false),
                Release("v1.2.10", prerelease = false, publishedAt = "2026-10-27T00:00:00Z"),
                Release("v99.0.0", prerelease = false, draft = true, publishedAt = null),
                Release("unrelated-tag", prerelease = false),
            ).map { it.info() }
        check(latestStableRelease(releases)?.tag == "v1.2.10")
        check(nextDevVersion(releases, emptyList()).appVersion == "1.2.11-beta.1")
        val large = "999999999999999999999999999"
        check(
            nextDevVersion(releases, listOf("v1.2.11-beta.$large.1")).appVersion ==
                "1.2.11-beta.1000000000000000000000000000",
        )
    }

    test("invalid source refs and events fail before outputs") {
        val invalidRef =
            runScript(
                "metadata.main.kts",
                "resolve-version",
                environment =
                    mapOf(
                        "GITHUB_REF" to "refs/heads/feature",
                    ),
            )
        check(invalidRef.exit != 0 && invalidRef.calls.isEmpty() && invalidRef.outputs.isEmpty())
        val invalidEvent =
            runScript("metadata.main.kts", "select-batch", environment = mapOf("EVENT" to "pull_request"))
        check(invalidEvent.exit != 0 && invalidEvent.calls.isEmpty() && invalidEvent.outputs.isEmpty())
    }

    test("unexpected comparison statuses fail explicitly") {
        val releases = listOf(Release("v1.2.4-beta.23.1").info())
        check(
            runCatching {
                selectDevBatch(releases, "workflow_dispatch", Instant.parse("2026-10-28T06:00:00Z")) { "unknown" }
            }.isFailure,
        )
    }

    test("invalid publication inputs cannot reach GitHub") {
        listOf(
            mapOf("PRE_RELEASE" to "typo"),
            mapOf("PRE_RELEASE" to "false"),
            mapOf("RELEASE_TAG" to "v1.2.4"),
            mapOf("APP_VERSION" to "1.2.4-beta.99"),
            mapOf("RELEASE_CHANNEL" to "stable"),
        ).forEach { environment ->
            val result = runScript("github-release.sh", "prepare-draft", environment = environment)
            check(result.exit != 0 && result.calls.isEmpty()) { result.log }
        }
    }

    test("repository package collection shares the release selectors") {
        val pages =
            listOf(
                listOf(
                    Release("v1.2.9", prerelease = false),
                    Release("v1.2.10", prerelease = false),
                    Release("v1.2.11-beta.2", publishedAt = "2026-10-27T00:00:00Z"),
                    Release("v1.2.11-beta.3"),
                ),
            )
        listOf("dev" to "v1.2.10", "stable" to "v1.2.11-beta.3").forEach { (channel, tag) ->
            val result =
                runScript(
                    "repository-site.sh",
                    "collect-packages",
                    pages,
                    files =
                        mapOf(
                            "release-assets/current.deb" to "deb",
                            "release-assets/current.rpm" to "rpm",
                            "release-assets/SHA256SUMS" to "sums",
                        ),
                    environment = mapOf("CHANNEL" to channel),
                ).succeeds()
            check("release download $tag" in result.calls) { result.calls }
        }
        val invalid = runScript("repository-site.sh", "collect-packages", environment = mapOf("CHANNEL" to "typo"))
        check(invalid.exit != 0 && invalid.calls.isEmpty())
    }

    println("Release workflow fixtures passed: $passed test groups")
}
