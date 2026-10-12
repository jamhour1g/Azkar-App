package com.azkar.testing

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.PatternLayout
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import java.io.InputStream

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
 * Runs [block] with every event from [loggerName] and its children collected into a list.
 *
 * The logger is forced to [Level.TRACE] for the duration and put back exactly as it was
 * afterwards, level included. That matters because `captureLogs` is the only way to assert on a
 * `debug` statement, and the configured level for `com.azkar` is a property of whichever
 * `logback-test.xml` the module happened to load — not something a test should have to
 * coordinate with.
 *
 * The appender hangs off [loggerName] rather than off the root so nothing double-counts an event
 * that also reaches the console, and events from sibling loggers are deliberately not collected:
 * a test that wants those asks for their name.
 *
 * [block] is typed `suspend () -> Unit` on purpose, so a value-producing call can be passed inline
 * (Kotlin coerces the last expression) without the harness inventing a second overload that differs
 * only by a type parameter. It has to be `suspend` because most of what the codebase logs from
 * happens inside a suspending call — a repository read, a warm-up — so a non-suspending block
 * would rule out exactly the logs worth asserting on.
 */
suspend fun captureLogs(
    loggerName: String,
    block: suspend () -> Unit,
): CapturedLogs {
    val logger = loggerNamed(loggerName)
    val appender = ListAppender<ILoggingEvent>()
    val previousLevel = logger.level

    appender.setContext(logbackContext())
    appender.start()
    logger.addAppender(appender)
    logger.level = Level.TRACE

    return try {
        block()
        CapturedLogs(appender.list)
    } finally {
        logger.level = previousLevel
        logger.detachAppender(appender)
        appender.stop()
    }
}

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
