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

**Code that logs receives its logger.** The parameter defaults to `KotlinLogging.logger {}`, so the
production wiring and the logger's name are exactly what they were:

```kotlin
class SettingsStore(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
    private val logger: KLogger = KotlinLogging.logger {},
)
```

The shape follows the declaration that does the logging:

| The code that logs is | The logger arrives as |
| --- | --- |
| a class | a constructor parameter |
| an `object` | a defaulted parameter on the function that logs |
| a top-level function | a defaulted parameter |

Two places keep a file-level logger, because neither has anywhere to put a parameter: the
application entry point (`app/src/main/kotlin/com/azkar/Main.kt`) and the `@Composable` functions in
`app/src/main/kotlin/com/azkar/ui/home/HomeScreen.kt`. When two declarations in one file log — a
class and its companion, say — give them one shared file-level default rather than two inline
defaults, so that both write to the same logger under the same name.

### Why the logger is handed in rather than looked up

A logger reached through a name is a JVM-wide singleton. A test that wants to assert on a log
statement can therefore only attach an appender to that name and collect whatever arrives while its
window is open — which is every statement anybody else logged under the same name in the meantime,
not the ones the code under test produced. The capture is a *time window* rather than a *cause*, and
the assertions it can support have to be approximate: "somebody logged this at some point", not
"this code logged this".

Handing the logger to the code makes the capture exact. The test owns the logger, the code writes to
it, and the events in between are precisely the ones the code under test emitted. It also lets the
code decide where a statement lands, which matters for the failures that are logged from a
coroutine on `Dispatchers.IO` rather than on the thread that started the work.


The empty lambda lets kotlin-logging derive the name from the enclosing declaration, which is what
makes `%logger` and `%method` resolve in the shipped pattern. Passing an explicit name works but
renders `%method` as `[?]`, so do not do it outside of tests.

The raw `org.slf4j.Logger` and `java.util.logging.Logger` types are still banned (see
[Enforcement](#enforcement)): a logger in a signature is fine, but it should be the kotlin-logging
one.

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
and provides the capture surface:

```kotlin
val logs = captureLogs { logger -> SeedDataLoader.load(logger) }

logs.messagesAt(Level.WARN) containsExactly "Dropped explanations without translation"
```

`captureLogs` is `suspend` because the code under test usually is. It creates a logger private to the
capture — named after a counter, so two captures running at the same time cannot see each other's
events — attaches a Logback `ListAppender` to it, and forces that logger's level to `TRACE` for the
duration.

Nothing global is touched, and that is what makes the capture trustworthy. There is no shared
`logger.level` for two captures to restore over each other, so a capture can run while another one
is open. The events are collected whichever thread emitted them, so a statement logged from a
coroutine on `Dispatchers.IO` is captured like any other.

Because the logger belongs to the capture, the code under test has to be given it — the house rule
above is what makes that possible — and the assertions that follow can be exact.

`renderAsProduction(event)` and `productionPattern()` read the pattern out of the shipped
`logback.xml` and run an event through it, so a test can assert on the line a user would actually
see without hard-coding the pattern. That only works in `:app`, the one module that packages
`logback.xml`; see `app/src/test/kotlin/com/azkar/LoggingPatternSuite.kt`.

The harness is the only code in the repository that touches Logback or `LoggerFactory` types
directly, which is why `config/testing` is excluded from the rules below.

## Enforcement

One detekt task runs in `verification`, configured by `config/detekt/detekt.yml` and
`config/detekt/detekt-compose.yml`:

- `style > ForbiddenImport` bans `org.slf4j.LoggerFactory`, `org.slf4j.Logger`,
  `java.util.logging.Logger` and `java.util.logging.LogManager`. The facade cannot be bypassed,
  and a raw logger type cannot appear in a signature — a logger in a signature is the kotlin-logging
  `KLogger` or nothing at all. `config/testing` is excluded, because the capture harness is the one
  place that legitimately holds a backend handle.
- `style > ForbiddenMethodCall` bans `println`/`print` in both their `kotlin.io` and
  `java.io.PrintStream` forms, and excludes `config/testing` for the same reason: the harness may
  print a diagnostic while a test is failing.
- detekt's built-in `PrintStackTrace` covers stack-trace printing.

`println` is not a logging API, but it behaves like one: it bypasses `logback.xml`, ignores
`AZKAR_LOG_LEVEL`, and never reaches the appenders. The single legitimate use is
`app/src/screenshot/kotlin/com/azkar/Screenshot.kt`, whose `stdout` is the `runScreenshot` task's
contract and whose source set is excluded from the shipped jar; it carries a `@Suppress` with that
justification.

## Adding logging to new code

1. Give the code that logs a logger: a constructor parameter on a class, a defaulted parameter on
   an `object`'s function or on a top-level function, defaulting to `KotlinLogging.logger {}`.
2. Log at the point where the information still exists, before a `runCatching` discards it.
3. Pick the level from the table above, and prefer `error` whenever a throwable is being swallowed.
4. Use `atX { … }` with a `payload` once a message needs more than a couple of fields.
5. Give the code a logger to write to, as the house rule above says, so that a test can assert on
   what it wrote.
6. Assert the line it wrote, with the capture described in
   [Logging in tests](#logging-in-tests). The suite that already covers the code is where the
   assertion belongs.
