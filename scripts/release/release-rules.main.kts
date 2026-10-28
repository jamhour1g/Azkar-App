import java.math.BigInteger
import java.time.Duration
import java.time.Instant

data class StableVersion(
    val major: BigInteger,
    val minor: BigInteger,
    val patch: BigInteger,
) : Comparable<StableVersion> {
    override fun compareTo(other: StableVersion): Int =
        compareValuesBy(this, other, StableVersion::major, StableVersion::minor, StableVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"
}

data class ReleaseInfo(
    val tag: String,
    val prerelease: Boolean,
    val draft: Boolean,
    val publishedAt: Instant?,
)

data class ReleaseVersion(
    val appVersion: String,
    val channel: String,
) {
    val tag: String get() = "v$appVersion"
    val prerelease: Boolean get() = channel == "dev"
}

data class BatchDecision(
    val publish: Boolean,
    val reason: String,
)

val stableTagPattern = Regex("""v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)""")
val devTagPattern = Regex("""v\d+\.\d+\.\d+-beta\.\d+(\.\d+)*""")

fun parseStableTag(tag: String): StableVersion? =
    stableTagPattern.matchEntire(tag)?.let {
        val (major, minor, patch) = it.destructured
        StableVersion(major.toBigInteger(), minor.toBigInteger(), patch.toBigInteger())
    }

fun latestStableRelease(releases: List<ReleaseInfo>): ReleaseInfo? =
    releases
        .filter { !it.draft && !it.prerelease && it.publishedAt != null && parseStableTag(it.tag) != null }
        .maxByOrNull { requireNotNull(parseStableTag(it.tag)) }

fun latestDevRelease(releases: List<ReleaseInfo>): ReleaseInfo? =
    releases
        .filter { !it.draft && it.prerelease && it.publishedAt != null && devTagPattern.matches(it.tag) }
        .maxByOrNull { requireNotNull(it.publishedAt) }

fun resolveStableVersion(tag: String): ReleaseVersion {
    val version = requireNotNull(parseStableTag(tag)) { "Stable tags must be vMAJOR.MINOR.PATCH: $tag" }
    return ReleaseVersion(version.toString(), "stable")
}

fun nextDevVersion(
    releases: List<ReleaseInfo>,
    tags: List<String>,
): ReleaseVersion {
    val base =
        latestStableRelease(releases)?.tag?.let(::parseStableTag)
            ?: StableVersion(BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE)
    val next = base.copy(patch = base.patch + BigInteger.ONE)
    val prefix = "v$next-beta."
    val suffix = Regex("""\d+(\.\d+)*""")
    // Drafts and tags reserve numbers too; legacy beta.N.ATTEMPT reserves N.
    val highest =
        (releases.map { it.tag } + tags)
            .asSequence()
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix) }
            .filter(suffix::matches)
            .map { it.substringBefore('.').toBigInteger() }
            .maxOrNull() ?: BigInteger.ZERO
    return ReleaseVersion("$next-beta.${highest + BigInteger.ONE}", "dev")
}

suspend fun selectDevBatch(
    releases: List<ReleaseInfo>,
    event: String,
    now: Instant,
    compare: suspend (String) -> String,
): BatchDecision {
    val previous = latestDevRelease(releases) ?: return BatchDecision(true, "First Dev release")
    val elapsed = Duration.between(requireNotNull(previous.publishedAt), now)
    if (event == "schedule" && elapsed < Duration.ofHours(6)) {
        return BatchDecision(false, "Waiting for the six-hour Dev release interval")
    }
    val comparison = compare(previous.tag)
    require(comparison in setOf("ahead", "identical", "behind", "diverged")) {
        "Unexpected GitHub comparison status: $comparison"
    }
    return if (comparison == "ahead") {
        BatchDecision(true, "New commits since ${previous.tag}")
    } else {
        BatchDecision(false, "No forward changes since ${previous.tag} ($comparison)")
    }
}
