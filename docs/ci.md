# CI

Two workflows build and check the app, plus a third that packages a release.

| Workflow                                                        | Trigger                                                     | What it does                                              |
|-----------------------------------------------------------------|-------------------------------------------------------------|-----------------------------------------------------------|
| [CI](../.github/workflows/ci.yml)                                | on demand, from the Actions tab                             | `./gradlew build` on Linux, Windows and macOS              |
| [PR Quality](../.github/workflows/quality.yml)                   | a pull request carrying the `run-quality` label             | `./gradlew check` and `:app:proguardReleaseJars`           |
| [Release](../.github/workflows/release.yml)                      | a `v*` tag                                                  | packages the installers and publishes the release          |

The `CI` workflow does not start on its own. Building on Linux, Windows and macOS is
the expensive half of the pipeline — a macOS minute costs about ten times a Linux one —
so the platforms are checked when someone asks for them from the Actions tab, rather
than on every push. `PR Quality` is the other half: it runs the cheap checks, and it
waits for the `run-quality` label before it does.

## The JDK is Zulu, not Temurin

Every workflow installs **Zulu 25**, which is the same JDK the project is developed
against (SDKMAN's default). It is not an arbitrary preference: Temurin 24 dropped the
JDK's `jmods` directory ([JEP 493](https://openjdk.org/jeps/493)), and ProGuard reads
the JDK's own classes from there. A build that shrinks the release classpath against a
Temurin JDK therefore fails, which is what the release pipeline hit before it moved to
Zulu. `docs/releasing.md` covers the release side of the same requirement.

`setup-java` resolves `java-version: '25'` on the `zulu` distribution to the newest
Zulu 25 available, so the patch level moves forward on its own.

ProGuard's own reaction to a missing `jmods` directory is unreadable: thousands of
"can't find referenced class java.lang.*" warnings, followed by an error that says
nothing about the JDK at all. So the requirement is checked by the build instead of
being rediscovered by whoever hits it. The task `:app:verifyJdkShipsJmods` fails with a
sentence that names the JDK and the fix, and `:app:proguardReleaseJars` does not start
until it has passed. The check runs wherever the build runs — on a laptop, in CI and in
the release pipeline.

## The release classpath is checked on the pull request

`./gradlew build` shrinks nothing: ProGuard only runs for the release build type, and
`build` does not produce it. The quality pipeline therefore runs the release task as
well, after the quality gate:

```bash
./gradlew :app:proguardReleaseJars
```

Without it, a change that breaks the release classpath — a dependency ProGuard cannot
see, a keep rule that stops matching — would surface for the first time on the tag that
publishes the release, which is the most expensive place to find it.

## One line ending, everywhere

A checkout of the repository has LF line endings on every platform, and the formatter
writes LF as well. `.gitattributes` pins the line endings for git, and Spotless is told
to write LF rather than to take the line endings from git's configuration — which
differs between a Linux and a Windows machine.

Without that, the Windows job checks out CRLF where the Linux job checks out LF, the two
disagree about what a formatted file is supposed to look like, and the Windows build
fails on files the Linux build accepts. The failure names one file and shows the whole
file changed, which says nothing about the code.

One trap hides in the same place. A file that git considers binary is not converted at
all: `CollectionImport.kt` held a raw NUL byte — the separator in a lookup key — which
made git treat it as binary, and it was therefore the one file in the repository that
Windows checked out with LF while everything else was CRLF. The separator is now
written as an escape, so the file is ordinary text like the rest.

## The maintainer gate on the quality pipeline

`PR Quality` does not run on every pull request. It waits for a maintainer to allow
it, and the permission is the **`run-quality` label**:

```bash
gh pr edit <number> --add-label run-quality
```

Adding a label to a pull request needs triage or write access, so a label on a pull
request is a maintainer's go-ahead — an author cannot add one to their own pull
request. Without the label the job is skipped, which is the visible signal that the
pipeline is waiting rather than failing.

The label is read from the pull request rather than from the event, so the pipeline
also picks the pull request up again when the author pushes more commits after it was
allowed. Remove the label to stop it.

The workflow is triggered by `pull_request` and not by `pull_request_target`. That is
deliberate: `pull_request_target` runs with a token that can write to the repository
and with the secrets, while the code it checks out is whatever the pull request says
it is. This workflow only reads, so it takes a read-only token and no secrets.

## Reports only when something fails

Both workflows upload artifacts, but only the ones that are worth keeping:

- A **failed** run uploads the detekt, test and coverage reports. They are the files that
  say what went wrong, so they are what whoever looks into the failure reads.
- A **successful** run uploads the built jar and nothing else.

Uploading the reports on every run fills the repository's artifact storage with copies of a
result nobody looks at.

## Running the same checks locally

```bash
./gradlew check                      # Spotless, detekt and the test suite, exactly as CI runs them
./gradlew :app:proguardReleaseJars   # the release classpath, exactly as CI runs it
```

`check` is the same task the quality pipeline runs, so a green run locally is a green
run in CI. Note that `build` behaves differently on purpose: it *applies* the
Spotless formatting when `CI` is unset, and only *verifies* it when `CI` is set, so a
local `build` quietly fixes formatting while a CI `build` fails on it.
