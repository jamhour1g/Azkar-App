package com.azkar.testing

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.PatternLayout
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.read.ListAppender
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.util.concurrent.atomic.AtomicLong

/**
 * What one logger emitted while [captureLogs] ran its block.
 *
 * The event list is the source of truth; the accessors exist so a test can assert on the thing it
 * actually cares about — a message, a level, a payload field, the throwable — without every
 * caller re-deriving the same `filter`/`map` chains. Anything richer than that is one
 * [ILoggingEvent] away.
 */
class CapturedLogs internal constructor(
    events: List<ILoggingEvent>,
) {
    /** Every event, in emission order. */
    val events: List<ILoggingEvent> = events.toList()

    /** Every formatted message, in emission order. */
    val messages: List<String> get() = events.map { it.formattedMessage }

    /** The events logged at exactly [level]. */
    fun at(level: Level): List<ILoggingEvent> = events.filter { it.level == level }

    /** The formatted messages logged at exactly [level]. */
    fun messagesAt(level: Level): List<String> = at(level).map { it.formattedMessage }

    /**
     * Every `payload` entry of every structured `logger.atX { … }` call, keyed by field name.
     *
     * A field can legitimately appear more than once across a block, hence the list per key.
     * Values are rendered with [Any.toString] because SLF4J types a key-value value as `Object`:
     * what every payload in this codebase holds is a string, and an assertion reads better as one.
     */
    val payload: Map<String, List<String>>
        get() = events.flatMap(::fieldsOf).groupBy({ (key, _) -> key }, { (_, value) -> value })

    /** The throwable attached to the one event at [level]. */
    fun throwableAt(level: Level): Throwable? = (at(level).single().throwableProxy as? ThrowableProxy)?.throwable
}

/** [event]'s key-value pairs, rendered as plain string pairs. */
private fun fieldsOf(event: ILoggingEvent): List<Pair<String, String>> =
    event.keyValuePairs.orEmpty().map { pair -> pair.key to pair.value.toString() }

/**
 * Runs [block] with a logger of its own, and collects every event that logger emitted.
 *
 * What this is for: the **shipped configuration** — that the console pattern renders a structured
 * payload the way a user would see it. Production log lines are not asserted on: a method that
 * loses data or swallows a failure reports it through its return value or through a callback it
 * accepts, and the test asserts that instead. See `docs/logging.md`.
 *
 * The logger is **private to this capture**: it is created here, named after nothing but a counter,
 * and handed to [block]. Nothing global is touched, which is the whole point of the design.
 *
 * The older shape of this helper — "attach an appender to the logger named `com.azkar.something`
 * and collect whatever arrives" — could only ever see a *time window*, not a *cause*. A logback
 * logger is a JVM-wide singleton, so any other code logging under that name while the window was
 * open landed in the capture too: the seed loader's line appeared twice when two suites seeded at
 * once, and a repository's failure warning appeared in a test that was asserting that a *successful*
 * call logs nothing. A capture that belongs to one test can only be one the test owns.
 *
 * Three properties follow from that, and they are the reason this shape was chosen over the older
 * one rather than merely being tidier:
 *
 * - **Causal.** Only the events emitted through *this* logger are collected, so `containsExactly`
 *   and `isEmpty` stay exact. A window-based capture can only ever be approximate.
 * - **Thread-independent.** The event is collected whichever thread emitted it. A warm-up that
 *   runs on `Dispatchers.IO` — the production default — logs from that thread, and a capture that
 *   filtered by thread would lose the very line it was written for.
 * - **Free of shared state.** There is no `logger.level` to put back and no appender to detach from
 *   a logger somebody else may be using, so two captures running at the same time cannot restore
 *   each other's level or read each other's events.
 *
 * The logger is forced to [Level.TRACE] for the duration, because `captureLogs` is the only way to
 * assert on a `debug` statement and the configured level for `com.azkar` is a property of whichever
 * `logback-test.xml` the module happened to load — not something a test should have to coordinate
 * with. The level is set on this capture's own logger, so nothing else is affected.
 *
 * [block] is typed `suspend (KLogger) -> Unit` on purpose. It has to be `suspend` because the code
 * that logs tends to log from inside a suspending call — a repository read, a warm-up — so a
 * non-suspending block would rule out exactly the calls worth capturing.
 */
suspend fun captureLogs(block: suspend (KLogger) -> Unit): CapturedLogs {
    val name = "com.azkar.testing.capture.${captureCounter.incrementAndGet()}"
    val logger = KotlinLogging.logger(name)
    val backend = loggerNamed(name)
    val appender = ListAppender<ILoggingEvent>()

    appender.setContext(logbackContext())
    appender.start()
    backend.addAppender(appender)
    backend.level = Level.TRACE

    return try {
        block(logger)
        CapturedLogs(appender.list)
    } finally {
        backend.detachAppender(appender)
        appender.stop()
        // The logger is this capture's own and is never used again, so it only has to be left
        // inert: no appender to collect into, and no level of its own to keep it at TRACE.
        backend.level = null
    }
}

/**
 * Numbers the capture loggers.
 *
 * A counter rather than a fixed name because two captures running at the same time must not share a
 * logger: they would collect each other's events, which is the very defect this design removes.
 * The names live under `com.azkar` so the `com.azkar` level configured by `logback-test.xml` still
 * applies to them.
 */
private val captureCounter = AtomicLong()

/**
 * Renders [event] through the pattern [productionPattern] declares — the layout the shipped app
 * actually prints with.
 *
 * A [ListAppender] can only prove a `payload` reached the event; it says nothing about whether a
 * human would ever see it. This closes that gap, which is the failure mode that let
 * `atWarn { payload = … }` sit in the codebase looking correct while rendering as a bare message.
 */
fun renderAsProduction(event: ILoggingEvent): String {
    val layout =
        PatternLayout().apply {
            setContext(logbackContext())
            pattern = productionPattern()
            start()
        }
    return layout.doLayout(event)
}

/**
 * The first `<pattern>` of the shipped `logback.xml`, read off the classpath.
 *
 * Read rather than duplicated so the test cannot drift away from the file it is checking: the
 * moment someone drops `%kvp` from the pattern, this fails instead of quietly passing against a
 * copy that still has it.
 */
fun productionPattern(): String {
    val configuration = shippedConfiguration()
    val pattern =
        PATTERN_ELEMENT
            .find(configuration)
            ?.groupValues
            ?.get(1)
            ?.trim()
    return pattern ?: error("logback.xml declares no <pattern> element")
}

private val PATTERN_ELEMENT = "<pattern>(.*?)</pattern>".toRegex(RegexOption.DOT_MATCHES_ALL)

private fun shippedConfiguration(): String {
    val loader = Thread.currentThread().contextClassLoader ?: ClassLoader.getSystemClassLoader()
    val stream =
        loader.getResourceAsStream(SHIPPED_CONFIGURATION)
            ?: error(
                "logback.xml is not on the test classpath; this only makes sense in a module " +
                    "that packages the shipped configuration",
            )
    return stream.use(InputStream::readBytes).decodeToString()
}

private const val SHIPPED_CONFIGURATION = "logback.xml"

/**
 * The backend's root context.
 *
 * kotlin-logging deliberately never hands out a backend logger, so naming one goes through
 * SLF4J. The cast back to Logback is safe rather than lucky — Logback is a `runtimeOnly`
 * dependency of every module — so the `require` states the assumption instead of letting a
 * `ClassCastException` surface from inside an assertion. This is the one place in the test tree
 * that touches a backend type on purpose, which is why the detekt logging rules skip this
 * package.
 */
private fun logbackContext(): LoggerContext {
    val factory = LoggerFactory.getILoggerFactory()
    require(factory is LoggerContext) { "SLF4J is bound to ${factory.javaClass.name}, not Logback" }
    return factory
}

private fun loggerNamed(name: String): Logger {
    val logger = LoggerFactory.getLogger(name)
    require(logger is Logger) { "SLF4J is bound to ${logger.javaClass.name}, not Logback" }
    return logger
}
