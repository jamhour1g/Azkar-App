#!/usr/bin/env -S kotlinc -script

import java.nio.file.Files
import java.nio.file.Path

val root = Files.createTempDirectory("azkar-ci-authorization-")
val workflow = Files.readAllLines(Path.of(".github/workflows/ci.yml"))

fun shellFor(stepName: String): String {
    val start = workflow.indexOfFirst { it.trim() == "- name: $stepName" }
    check(start >= 0)
    val run = (start until workflow.size).first { workflow[it].trim() == "run: |" }
    return workflow
        .drop(run + 1)
        .takeWhile { it.isBlank() || it.startsWith("          ") }
        .joinToString("\n") { it.removePrefix("          ") }
}

val owner = "jamhour1g"
val head = "a".repeat(40)
val base = "b".repeat(40)
val mockGh =
    Files.writeString(
        root.resolve("gh"),
        """
        #!/usr/bin/env bash
        set -euo pipefail
        case "${'$'}*" in
          *statuses/*) printf '%s\n' "${'$'}*" >> "${'$'}MOCK_LOG" ;;
          *pulls/*/files*) printf '%s\n' "${'$'}MOCK_FILES" ;;
          *pulls/*)
            jq --arg head "${'$'}MOCK_HEAD" --arg base "${'$'}MOCK_BASE" --arg branch "${'$'}MOCK_BRANCH" \
              --arg state "${'$'}MOCK_STATE" --argjson count "${'$'}MOCK_CHANGED_FILES" \
              '.head.sha = ${'$'}head | .base.sha = ${'$'}base | .base.ref = ${'$'}branch | .state = ${'$'}state | .changed_files = ${'$'}count' "${'$'}MOCK_PR" ;;
          *) echo dev ;;
        esac
        """.trimIndent(),
    )
check(mockGh.toFile().setExecutable(true))

fun runShell(
    name: String,
    environment: Map<String, String>,
    author: String = "contributor",
    target: String = "dev",
): Pair<Int, String> {
    val fixture = root.resolve("pr.json")
    Files.writeString(
        fixture,
        """{"state":"open","changed_files":1,"user":{"login":"$author"},"head":{"sha":"$head","ref":"feature","repo":{"full_name":"$owner/Azkar-App"}},"base":{"ref":"$target","sha":"$base","repo":{"full_name":"$owner/Azkar-App"}}}""",
    )
    val log = root.resolve("statuses")
    Files.writeString(log, "")
    val process = ProcessBuilder("bash", "-e", "-o", "pipefail", "-c", shellFor(name)).redirectErrorStream(true)
    process.environment().putAll(
        mapOf(
            "PATH" to "$root:${System.getenv("PATH")}",
            "MOCK_PR" to fixture.toString(),
            "MOCK_LOG" to log.toString(),
            "MOCK_HEAD" to head,
            "MOCK_BASE" to base,
            "MOCK_BRANCH" to target,
            "MOCK_FILES" to """[[{"filename":"README.md"}]]""",
            "MOCK_STATE" to "open",
            "MOCK_CHANGED_FILES" to "1",
            "REPOSITORY" to "$owner/Azkar-App",
            "OWNER" to owner,
            "ACTOR" to owner,
            "EVENT" to "workflow_dispatch",
            "DISPATCH_REF" to "refs/heads/dev",
            "FULL_INSTALLERS" to "false",
            "PR_NUMBER" to "123",
            "REVIEWED_SHA" to head,
            "BASE_SHA" to base,
            "BASE_BRANCH" to target,
            "SKIP_CHECKS" to "false",
            "GITHUB_SHA" to head,
            "GITHUB_OUTPUT" to root.resolve("outputs").toString(),
            "GITHUB_STEP_SUMMARY" to root.resolve("summary").toString(),
            "GITHUB_SERVER_URL" to "https://github.com",
            "GITHUB_RUN_ID" to "1",
        ) + environment,
    )
    val running = process.start()
    val output = running.inputStream.bufferedReader().use { it.readText() }
    return running.waitFor() to (output + Files.readString(log))
}

fun accepts(
    environment: Map<String, String> = emptyMap(),
    author: String = "contributor",
    target: String = "dev",
) {
    val (exit, output) = runShell("Resolve and authorize the PR", environment, author, target)
    check(exit == 0) { output }
}

fun rejects(
    environment: Map<String, String>,
    author: String = "contributor",
) {
    check(runShell("Resolve and authorize the PR", environment, author).first != 0)
}

try {
    accepts()
    accepts(mapOf("EVENT" to "pull_request_target"), author = owner)
    rejects(mapOf("EVENT" to "pull_request_target"))
    rejects(mapOf("ACTOR" to "contributor"))
    rejects(mapOf("REVIEWED_SHA" to "c".repeat(40)))
    rejects(mapOf("PR_NUMBER" to "123/../../statuses"))
    rejects(mapOf("MOCK_STATE" to "closed"))
    rejects(mapOf("DISPATCH_REF" to "refs/heads/feature"))
    accepts(mapOf("DISPATCH_REF" to "refs/heads/feature"), author = owner)
    accepts(mapOf("FULL_INSTALLERS" to "true"))
    accepts(mapOf("SKIP_CHECKS" to "true"))
    accepts(
        mapOf(
            "SKIP_CHECKS" to "true",
            "MOCK_FILES" to """[[{"filename":"docs/new.md","previous_filename":"docs/old.md"}]]""",
        ),
    )
    rejects(mapOf("SKIP_CHECKS" to "true", "MOCK_FILES" to """[[{"filename":"app/build.gradle.kts"}]]"""))
    rejects(mapOf("SKIP_CHECKS" to "true", "MOCK_CHANGED_FILES" to "2"))
    rejects(mapOf("SKIP_CHECKS" to "true", "MOCK_CHANGED_FILES" to "3001"))
    rejects(
        mapOf(
            "SKIP_CHECKS" to "true",
            "MOCK_FILES" to """[[{"filename":"docs/old-code.md","previous_filename":"app/src/main.kt"}]]""",
        ),
    )
    rejects(mapOf("SKIP_CHECKS" to "true", "FULL_INSTALLERS" to "true"))
    check(runShell("Resolve and authorize the PR", mapOf("SKIP_CHECKS" to "true"), target = "production").first != 0)
    accepts(target = "production")
    check(runShell("Resolve and authorize the PR", emptyMap(), target = "other").first != 0)
    val success =
        mapOf(
            "INSTALLERS_REQUIRED" to "true",
            "LINUX_RESULT" to "success",
            "CROSS_RESULT" to "success",
            "INSTALLERS_RESULT" to "success",
        )

    fun report(environment: Map<String, String>): String {
        val (exit, output) = runShell("Publish required commit statuses", environment)
        check(exit == 0) { output }
        return output
    }
    check(report(success).count { it == '\n' } >= 2)
    check("state=failure" !in report(success))
    check(
        "state=failure" !in
            report(
                success +
                    mapOf("SKIP_CHECKS" to "true", "LINUX_RESULT" to "skipped", "INSTALLERS_REQUIRED" to "false"),
            ),
    )
    check(
        "state=success" !in
            report(success + ("MOCK_HEAD" to "c".repeat(40))),
    )
    check("state=success" !in report(success + ("MOCK_BASE" to "c".repeat(40))))
    check("state=success" !in report(success + ("MOCK_BRANCH" to "production")))
    check("state=success" !in report(success + ("MOCK_STATE" to "closed")))
    check(
        "state=failure" in
            report(success + ("INSTALLERS_RESULT" to "failure")),
    )
    check(
        "state=failure" in report(success + ("CROSS_RESULT" to "skipped")),
    )
    check("state=failure" in report(success + ("LINUX_RESULT" to "cancelled")))
    println(
        "CI authorization fixtures passed: approval, documentation skips, renames, stale head/base, installer failures",
    )
} finally {
    Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
}
