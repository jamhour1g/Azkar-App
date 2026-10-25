# CI

The branch flow is **feature PRs → `dev` → `production`**. CI runs on pull
requests targeting either long-lived branch; ordinary pushes do not start CI:

| Change | Checks |
| --- | --- |
| PR targeting `dev` | Linux build, tests, packaged-runtime smoke test, release-asset validator tests |
| PR targeting `production` | Linux, Windows, and macOS builds; `Production promotion gate` requires all three |
| Manual CI run | Linux, Windows, and macOS builds |
| Push or manual Release run on `dev` | Checks, native packages, versioned Dev prerelease and signed Dev apt/RPM repositories |
| `v*` tag on `production` | Publishes a stable release |

The [CI workflow](../.github/workflows/ci.yml) cancels obsolete pull-request
runs. Dispatch the [Release workflow](../.github/workflows/release.yml) manually
with `dev` selected to request another Dev build; it runs checks before packaging.
Stable releases must use a version tag whose commit is already on `production`.

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

## The release classpath is checked before promotion

`./gradlew build` shrinks nothing: ProGuard only runs for the release build type, and
`build` does not produce it. CI also runs `:app:smokeTestPackagedRuntime`, which depends on ProGuard and the linked release runtime, so a change
that breaks the release classpath — for example, a dependency ProGuard cannot see or a
keep rule that stops matching — is caught before the release workflow packages it. The smoke test starts the bundled launcher against a fresh temporary profile, migrates the database and requires a clean exit. It does not test windows, tray integration or native install/update behavior.

The release workflow checks every required installer/architecture and update-manifest hash before preparing a draft release. Signed apt/RPM metadata is verified and pushed before the release becomes public. Local `act` runs skip artifact uploads and are blocked from publication; see [local packaging](releasing.md#local-packaging).

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

## Reports only when something fails

Linux CI uploads detekt, test and coverage reports only for failed runs. Successful
runs keep the built jars for seven days. Windows and macOS upload test reports when
their build fails.

## Running the same checks locally

```bash
./gradlew build                      # build, tests, detekt and Spotless
./gradlew :app:smokeTestPackagedRuntime # linked release runtime and isolated database startup
./scripts/merge_nucleus_release.main.kts self-test # release-asset validation fixtures (requires kotlinc)
```

`check` is included in `build`. Note that `build` behaves differently on purpose:
it *applies* Spotless formatting when `CI` is unset, and only *verifies* it when
`CI` is set, so a local build can quietly fix formatting while a CI build fails on it.
