# CI

Two workflows build and check the app, plus a third that packages a release.

| Workflow                                                        | Trigger                                                     | What it does                                              |
|-----------------------------------------------------------------|-------------------------------------------------------------|-----------------------------------------------------------|
| [CI](../.github/workflows/ci.yml)                                | every push and pull request to `master`                     | `./gradlew build` on Linux, Windows and macOS              |
| [PR Quality](../.github/workflows/quality.yml)                   | a pull request carrying the `run-quality` label             | `./gradlew check`: formatting, static analysis, tests      |
| [Release](../.github/workflows/release.yml)                      | a `v*` tag                                                  | packages the installers and publishes the release          |

## The JDK is Zulu, not Temurin

Every workflow installs **Zulu 25**, which is the same JDK the project is developed
against (SDKMAN's default). It is not an arbitrary preference: Temurin 24 dropped the
JDK's `jmods` directory ([JEP 493](https://openjdk.org/jeps/493)), and ProGuard reads
the JDK's own classes from there. A build that shrinks the release classpath against a
Temurin JDK therefore fails, which is what the release pipeline hit before it moved to
Zulu. `docs/releasing.md` covers the release side of the same requirement.

`setup-java` resolves `java-version: '25'` on the `zulu` distribution to the newest
Zulu 25 available, so the patch level moves forward on its own.

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

## Running the same checks locally

```bash
./gradlew check        # Spotless, detekt and the test suite, exactly as CI runs them
```

`check` is the same task the quality pipeline runs, so a green run locally is a green
run in CI. Note that `build` behaves differently on purpose: it *applies* the
Spotless formatting when `CI` is unset, and only *verifies* it when `CI` is set, so a
local `build` quietly fixes formatting while a CI `build` fails on it.
