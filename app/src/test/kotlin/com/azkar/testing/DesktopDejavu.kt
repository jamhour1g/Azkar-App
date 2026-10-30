package com.azkar.testing

import androidx.compose.ui.platform.isDebugInspectorInfoEnabled
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import dejavu.Dejavu
import dejavu.assertRecompositions
import dejavu.runRecompositionTrackingUiTest

/** Runs a custom-size Desktop test with the full reset used by Dejavu's public test runner. */
@OptIn(ExperimentalTestApi::class)
internal fun <T> withDesktopDejavu(block: () -> T): T {
    runRecompositionTrackingUiTest { }
    val previousInspectorInfoEnabled = isDebugInspectorInfoEnabled
    isDebugInspectorInfoEnabled = true
    Dejavu.enable()
    try {
        return block()
    } finally {
        Dejavu.disable()
        isDebugInspectorInfoEnabled = previousInspectorInfoEnabled
        // On JVM, disable() only disables tracing. The public runner also clears the
        // inspection tables and composition history and removes the installed tracer.
        runRecompositionTrackingUiTest { }
    }
}

/** Fails closed when Dejavu cannot map a tag instead of accepting its fallback count of zero. */
@OptIn(ExperimentalTestApi::class)
internal fun SemanticsNodeInteraction.assertTrackedRecompositions(
    exactly: Int? = null,
    atLeast: Int? = null,
    atMost: Int? = null,
    expectedFunction: String? = null,
): SemanticsNodeInteraction {
    assertRecompositions(exactly = exactly, atLeast = atLeast, atMost = atMost)
    val tag = checkNotNull(fetchSemanticsNode().config.getOrNull(SemanticsProperties.TestTag))
    val function = checkNotNull(DejavuMapping.functionFor(tag)) { "Dejavu could not map test tag '$tag'" }
    check(expectedFunction == null || function.substringAfterLast('.') == expectedFunction) {
        "Dejavu mapped '$tag' to '$function', expected '$expectedFunction'"
    }
    return this
}

@OptIn(ExperimentalTestApi::class)
internal fun SemanticsNodeInteraction.assertTrackedStable(expectedFunction: String? = null): SemanticsNodeInteraction =
    assertTrackedRecompositions(exactly = 0, expectedFunction = expectedFunction)

/** Checks every traced application composable during an explicitly bounded idle interval. */
internal fun assertNoAzkarRecompositions() {
    val changes = DejavuMapping.recompositions().filter { (name, count) -> name.startsWith("com.azkar.") && count > 0 }
    check(changes.isEmpty()) { "Unexpected application recompositions: $changes" }
}

/** Checks a traced function's aggregate count when its stateful scope has no taggable node. */
internal fun assertComposableRecompositions(
    function: String,
    expected: IntRange,
) {
    require(expected.first >= 0 && !expected.isEmpty())
    check(function in DejavuMapping.tracedFunctions()) { "Dejavu did not trace '$function'" }
    val count = DejavuMapping.recompositions()[function] ?: 0
    check(count in expected) { "Recompositions for '$function': expected $expected, actual $count" }
}

// Dejavu 0.5.1 exposes no public mapping query; its assertion silently returns zero for an
// unmapped tag. Fail on an incompatible library update instead of accepting a false stable result.
private object DejavuMapping {
    private val tracerClass = Class.forName("dejavu.internal.DejavuTracer")
    private val tracer = tracerClass.getField("INSTANCE").get(null)
    private val mapping = tracerClass.getMethod("getFunctionNameForTag", String::class.java)
    private val counts = tracerClass.getMethod("getAllRecompositionCounts")
    private val traced = tracerClass.getMethod("getAllTracedComposables")
    private val qualifiedName =
        Class
            .forName(
                "dejavu.internal.DejavuTracer\$TracedComposable",
            ).getMethod("getQualifiedName")

    fun functionFor(tag: String): String? = mapping.invoke(tracer, tag) as String?

    @Suppress("UNCHECKED_CAST")
    fun recompositions(): Map<String, Int> = counts.invoke(tracer) as Map<String, Int>

    fun tracedFunctions(): Set<String> =
        (traced.invoke(tracer) as List<*>)
            .map {
                qualifiedName.invoke(it) as String
            }.toSet()
}
