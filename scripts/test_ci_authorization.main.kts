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
          *pulls/*)
            if [[ "${'$'}*" == *'--jq .head.sha'* ]]; then printf '%s\n' "${'$'}MOCK_HEAD";
            else cat "${'$'}MOCK_PR"; fi ;;
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
        """{"state":"open","user":{"login":"$author"},"head":{"sha":"$head","ref":"feature","repo":{"full_name":"$owner/Azkar-App"}},"base":{"ref":"$target","sha":"$base","repo":{"full_name":"$owner/Azkar-App"}}}""",
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
            "REPOSITORY" to "$owner/Azkar-App",
            "OWNER" to owner,
            "ACTOR" to owner,
            "EVENT" to "workflow_dispatch",
            "DISPATCH_REF" to "refs/heads/dev",
            "FULL_INSTALLERS" to "false",
            "PR_NUMBER" to "123",
            "REVIEWED_SHA" to head,
            "GITHUB_SHA" to head,
            "GITHUB_OUTPUT" to root.resolve("outputs").toString(),
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
    check(workflow.none { it.trim() == "pull_request_target:" }) {
        "PR events must not start CI automatically"
    }
    accepts()
    rejects(mapOf("ACTOR" to "contributor"))
    rejects(mapOf("REVIEWED_SHA" to "c".repeat(40)))
    rejects(mapOf("PR_NUMBER" to "123/../../statuses"))
    rejects(mapOf("DISPATCH_REF" to "refs/heads/feature"))
    accepts(mapOf("DISPATCH_REF" to "refs/heads/feature"), author = owner)
    accepts(mapOf("FULL_INSTALLERS" to "true"))
    accepts(target = "production")
    check(runShell("Resolve and authorize the PR", emptyMap(), target = "other").first != 0)
    val success =
        mapOf(
            "INSTALLERS_REQUIRED" to "true",
            "LINUX_RESULT" to "success",
            "CROSS_RESULT" to "success",
            "INSTALLERS_RESULT" to "success",
        )
    check(runShell("Publish required commit statuses", success).second.count { it == '\n' } >= 2)
    check("state=failure" !in runShell("Publish required commit statuses", success).second)
    check(
        "state=success" !in
            runShell("Publish required commit statuses", success + ("MOCK_HEAD" to "c".repeat(40))).second,
    )
    check(
        "state=failure" in
            runShell("Publish required commit statuses", success + ("INSTALLERS_RESULT" to "failure")).second,
    )
    check(
        "state=failure" in runShell("Publish required commit statuses", success + ("CROSS_RESULT" to "skipped")).second,
    )
    println(
        "CI authorization fixtures passed: owner/contributor approval, stale SHA, branch selection, installer failures",
    )
} finally {
    Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
}
