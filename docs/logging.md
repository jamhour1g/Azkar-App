# Logging

Azkar logs through [kotlin-logging](https://github.com/oshai/kotlin-logging) on top of
[SLF4J](https://www.slf4j.org/), with [Logback](https://logback.qos.ch/) as the backend. The
versions are pinned in `gradle/libs.versions.toml` and the dependencies are added to every module by
`buildSrc/src/main/kotlin/azkar-common.gradle.kts`, so no module needs to declare logging
itself.

```
KotlinLogging.logger {}   →   SLF4J API 2.0.20   →   Logback 1.5.32
```

## Declaring a logger

One logger per file, at the top level, immediately after the imports:

```kotlin
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}
```

The empty lambda lets kotlin-logging derive the name from the enclosing declaration, which is what
makes `%logger` and `%method` resolve in the shipped pattern. Passing an explicit name works but
renders `%method` as `[?]`, so do not do it outside of tests.

Never hold a `Logger` in a field, pass one as a parameter, or build one with
`LoggerFactory.getLogger(...)`. Both are banned by detekt (see [Enforcement](#enforcement)).

## Writing a log statement

Always use the lambda form. The message is only built if the level is enabled, which matters because
several of these messages interpolate collections:

```kotlin
logger.info { "Seeded ${rows.size} remembrances and ${tagIds.size} tags" }
```

Never concatenate with `+` inside a log message, and never interpolate a throwable into the text.
Pass it as the first argument instead so the backend can render the stack trace:

```kotlin
logger.error(failure) { "Unable to open the application database" }
```

For a statement with more than one or two fields of context, use the structured `atX` form. The
`payload` map is rendered by the `%kvp` converter in the shipped pattern, so the values survive into
the log line rather than being discarded:

```kotlin
logger.atWarn {
    message = "Dropped locales missing counterpart"
    payload =
        mapOf(
            "remembranceId" to row.id,
            "droppedTranslations" to droppedTranslations,
            "droppedExplanations" to droppedExplanations,
        )
}
```

Note the field names describe the table a locale *survived* in, not the one that was lost. A locale
present only in `explanation_translation` shows up under `droppedTranslations`, because
`droppedTranslations = translationKeys - explanationKeys`.

## Choosing a level

The level answers "how would anyone find out this happened", not "how bad does it feel".

| Level | Use it for | Examples in the codebase |
| --- | --- | --- |
| `debug` | Lifecycle chatter that only matters while developing. | Creating the Exposed database, closing the pool, starting a warm-up, the schema already being current. |
| `info` | A one-shot milestone, once per process, that a user support log should show. | Application start and shutdown, the seed counts, "applying N schema statements", the reminder schedule. |
| `warn` | Degradation that is either recovered or will be reported again by whoever rethrows. | A seed resource with no rows, a locale that could not be persisted, a persistence call that is about to become a `PersistenceFailureException`. |
| `error` | A failure that something swallowed. Use this whenever a `runCatching`, an `onFailure`, or a coroutine boundary discards the throwable — nothing else will surface it. | The database warm-up job, every UI `runCatching` in `LibraryState`/`HomeScreen`/`HomeState`, `DatabaseManager.open()`, `DataSourceConfig`. |

The last row is the one that is easy to get wrong. The UI deliberately shows users a single
localized sentence (a toast or an `infoLine`) and then keeps running; that message is not a
diagnostic, so without an `error` the cause is gone. Conversely, a failure that is rethrown does not
need `error` — the stack trace is already going somewhere.

If a `runCatching` has no `.onFailure`, that is a gap, not a style choice: add one.

## Runtime configuration

`app/src/main/resources/logback.xml` sets the root level from `AZKAR_LOG_LEVEL`, defaulting to
`INFO`. It is read as a system property first and an environment variable second:

```bash
AZKAR_LOG_LEVEL=DEBUG ./azkar                # packaged application
AZKAR_LOG_LEVEL=DEBUG ./gradlew :app:run     # from source
AZKAR_LOG_LEVEL=TRACE -Dlogback.configurationFile=/path/to/logback.xml ./azkar
```

`INFO` is the shipped default. `DEBUG` adds the lifecycle chatter. `TRACE` additionally enables
per-statement filtering, which is far too noisy to leave on outside a debugging session.

The console pattern is:

```
[%logger] [%method] [%thread] [%date{yyyy-MM-dd@HH:mm:ss}] [%level] - %msg %kvp%n
```

`%kvp` is load-bearing, not decoration: it is what renders an `atX { payload = … }` map. Logback
emits nothing at all for `%kvp` when an event has no key-value pairs, so a plain `logger.info { … }`
is unaffected. The same file contains a commented-out `RollingFileAppender` writing to `logs/azkar.log`
with 30 days of history; uncomment it together with its `<appender-ref>` when a bug report needs a
file to attach.

To quieten a noisy third party, add a `<logger name="…" level="…"/>` above `<root>`, as
`com.zaxxer.hikari` already does.

## Logging in tests

`config/logback/logback-test.xml` is added to the test resources of all three modules by
`azkar-common`. Logback prefers it over `logback.xml`, which matters twice over: without it
`:data` and `:domain` would fall back to Logback's built-in default configuration, and `:app` would
inherit the packaged `INFO` root level and silently discard the `debug` statements the assertions
target. It pins `com.azkar` to `DEBUG` and the root to `WARN` so test output stays quiet without
making assertions depend on the shell that launched Gradle.

`config/testing/com/azkar/testing/LogCapture.kt` is compiled into every module's test source set
and provides the assertion surface:

```kotlin
val logs = captureLogs("com.azkar.data.seed.SeedDataLoader") {
    TestDatabase.bootstrapWithH2()
}

logs.messagesAt(Level.INFO) containsExactly "Seeded 254 remembrances and 12 tags"
logs.payload["remembranceId"] containsExactly "7"
logs.throwableAt(Level.ERROR) isSameAs failure
```

`captureLogs` is `suspend` because the code under test usually is. It attaches a Logback
`ListAppender`, forces the level it is given, and restores the previous level in a `finally`.

`renderAsProduction(event)` and `productionPattern()` read the pattern out of the shipped
`logback.xml` and run an event through it, so a test can assert on the line a user would actually
see without hard-coding the pattern. That only works in `:app`, the one module that packages
`logback.xml`; see `app/src/test/kotlin/com/azkar/LoggingPatternSuite.kt`.

The harness is the only code in the repository that touches Logback or `LoggerFactory` types
directly, which is why `config/testing` is excluded from the rules below.

## Enforcement

Two detekt tasks run in `verification`:

- `detekt` — the project's existing gate, configured by `config/detekt/detekt.yml` and
  `config/detekt/detekt-compose.yml`. Its `style > ForbiddenImport` rule bans
  `org.slf4j.LoggerFactory`, `org.slf4j.Logger`, `java.util.logging.Logger` and
  `java.util.logging.LogManager`, so the facade cannot be bypassed. detekt's built-in
  `PrintStackTrace` covers stack-trace printing.
- `detektLogging` — configured by `config/detekt/detekt-logging.yml`, and the only task that sets a
  `classpath`. Its `style > ForbiddenMethodCall` rule bans `println`/`print` in both their
  `kotlin.io` and `java.io.PrintStream` forms.

`detektLogging` needs its own task because of a pre-existing quirk: the project's `detekt` task runs
with an empty `classpath`, and detekt's method-call rules silently match nothing without type
resolution. Rather than switch on that whole dormant rule set for every module — which surfaces
around seventeen pre-existing `LongParameterList`, `InjectDispatcher` and unused-symbol findings in
unrelated UI code — the logging rules get a dedicated, additive task. The main gate is unchanged.

`println` is not a logging API, but it behaves like one: it bypasses `logback.xml`, ignores
`AZKAR_LOG_LEVEL`, and never reaches the appenders. The single legitimate use is
`app/src/screenshot/kotlin/com/azkar/Screenshot.kt`, whose `stdout` is the `runScreenshot` task's
contract and whose source set is excluded from the shipped jar; it carries a `@Suppress` with that
justification.

## Adding logging to new code

1. Add `private val logger = KotlinLogging.logger {}` after the imports.
2. Log at the point where the information still exists, before a `runCatching` discards it.
3. Pick the level from the table above, and prefer `error` whenever a throwable is being swallowed.
4. Use `atX { … }` with a `payload` once a message needs more than a couple of fields.
5. If the new code sits behind a failure path, add an assertion to the suite that already covers it.
