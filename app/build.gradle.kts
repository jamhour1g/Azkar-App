import org.gradle.api.artifacts.ExternalModuleDependency
import java.util.jar.JarFile

plugins {
    id("azkar-common")
    // Applied unversioned: buildSrc owns these two on the classpath (detekt needs the
    // Kotlin plugin to resolve source sets), and the Compose compiler version must
    // match Kotlin exactly.
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    // Applied for `CollectionTransfer`'s JSON: the import/export feature is the app's
    // first machine-readable document, and a hand-written parser is where escaping bugs
    // and unescaped Arabic live.
    id("org.jetbrains.kotlin.plugin.serialization")
    alias(libs.plugins.org.jetbrains.compose)
    alias(libs.plugins.org.jetbrains.compose.hot.reload)
    // Hydraulic Conveyor (desktop packaging + auto-update, configured in `conveyor.conf`).
    // Applied last: the cross-platform configurations it adds must see the Compose
    // extension created above.
    alias(libs.plugins.dev.hydraulic.conveyor)
}

version = "0.1.0"

// The desktop entry point, in one place.
//
// Three separate things have to agree on this string, and none of them can check
// the others:
//   - the launcher inside the package (`app.jvm.gui.main-class`),
//   - the `StartupWMClass` the Linux desktop entry declares, which the desktop
//     manager matches against the running window's `WM_CLASS`, and
//   - the `WM_CLASS` AWT reports, which it derives from the class that owns `main`.
//
// Conveyor derives the first two from the Compose plugin's `mainClass` below, so
// what is left to keep true is the third, and `Main.kt` owns that. The test
// `DesktopWindowClassSuite` holds the two sides to that agreement.
val desktopMainClass = "com.azkar.MainKt"

kotlin {
    compilerOptions {
        // The Compose Styles API (`androidx.compose.foundation.style.Style`,
        // `Modifier.styleable`, `rememberUpdatedStyleState`) is still flagged experimental and
        // every use of it is an error until the module opts in.
        //
        // It is declared here rather than in `azkar-common` because `:data` and `:domain` have no
        // Compose dependency at all — the flag would name a package those modules cannot resolve.
        //
        // The skill's own prerequisites also ask for `compileSdk 37` / Compose BOM `2026.04.01`.
        // Those are Android-only and do not apply to this build; the desktop foundation artifact
        // (`foundation-desktop`) ships the API on its own, which is why only the opt-in is needed.
        freeCompilerArgs.add("-opt-in=androidx.compose.foundation.style.ExperimentalFoundationStyleApi")
    }
}

java {
    // Skiko (the Compose Desktop renderer) is not a JPMS module and needs JNI
    // access to unpack its native runtime, so `:app` runs from the classpath.
    // `azkar-common` turns module-path inference on for every module; `:domain`
    // and `:data` keep their descriptors, `:app` opts out — which is why
    // `app/src/main/java/module-info.java` is deleted.
    modularity.inferModulePath = false
}

repositories {
    // Compose Multiplatform's desktop artifacts delegate part of their runtime
    // graph (`lifecycle-runtime-compose`, `savedstate-compose`) to the upstream
    // `androidx.*` coordinates, which are only published to Google's Maven.
    // Scoped by group so every other dependency still resolves from Maven Central.
    google {
        content {
            includeGroupByRegex("androidx\\..*")
            includeGroupByRegex("com\\.google\\.android.*")
        }
    }
}

dependencies {
    implementation(project(":data"))
    implementation(project(":domain"))

    // Compose Multiplatform (desktop only — the JVM target is the sole target)
    implementation(compose.desktop.currentOs)
    implementation(libs.org.jetbrains.compose.components.resources)
    implementation(libs.org.jetbrains.compose.material3)
    implementation(libs.org.jetbrains.compose.material.icons.extended)
    // `@Preview` / tooling annotations. `debugImplementation` of ui-tooling would
    // add the live-reload devtools; the preview artifact itself is tiny and lets
    // the IDE render `@Preview` composables without a running app.
    implementation(libs.org.jetbrains.compose.ui.tooling.preview)

    // `kotlinx-coroutines-swing` below is what drives Swing-dispatched coroutines; `core` is
    // declared in its own right because `DatabaseWarmUp` reaches for `Dispatchers`, `Job` and
    // `CoroutineScope` directly rather than through the Swing extension.
    implementation(libs.org.jetbrains.kotlinx.kotlinx.serialization.json)
    implementation(libs.org.jetbrains.kotlinx.kotlinx.coroutines.core)
    implementation(libs.org.jetbrains.kotlinx.kotlinx.coroutines.swing)
    implementation(libs.io.ktor.ktor.client.core)
    implementation(libs.io.ktor.ktor.client.cio)
    implementation(libs.io.ktor.ktor.client.content.negotiation)
    implementation(libs.io.ktor.ktor.serialization.kotlinx.json)

    // The desktop tray. It is a Compose composable and so is declared in `Main.kt` rather than built
    // here. It is native per platform, which is the whole point of dropping the hand-rolled AWT tray:
    // the JDK draws the same menu everywhere. The tray is also the only thing this app hands the OS to
    // draw — a due reminder is announced by the app's own overlay window, so there is no notification
    // service dependency here.
    //
    // The tray reaches the OS through JNA and D-Bus rather than through the JDK, so the packaging
    // tasks below are the only thing that has to care: the Linux package ships a plain jar and a
    // JVM, and nothing here shrinks or rewrites bytecode. If an uber-jar task is ever added it
    // will need the library's documented keep rules (`com.sun.jna.**` and
    // `dev.nucleusframework.composenativetray.**`) -- JNA resolves its native functions by
    // reflection at call time, so a shrinking build would break the tray silently at runtime.
    implementation(libs.dev.nucleusframework.compose.native.tray)

    // App settings (locale, notification switches). DataStore replaces `java.util.prefs`: it is
    // transactional, it survives a crash mid-write, and it exposes `Flow` reads, so the UI can
    // observe the stored value instead of each state holder hand-rolling a mirror of it.
    implementation(libs.androidx.datastore.preferences.core)

    // Navigation 3. The back stack replaces the `when (state.tab)` the shell used to switch
    // screens with: destinations are sealed `NavKey` values, so the history is a real list and a
    // destination added on top of another one comes back on pop. `navigation3-runtime` resolves
    // from the `google()` repository above and `navigation3-ui` from Maven Central, since AndroidX
    // never published a desktop variant of the latter.
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.org.jetbrains.androidx.navigation3.ui)

    // Dependency injection. This module owns the top of the Koin graph (`appModule`) and is
    // the only module that reads it back out of a composition, so it needs both the container
    // and the Compose bindings. `:data` declares the lower half against `koin-core` alone.
    implementation(libs.io.insert.koin.koin.core)
    implementation(libs.io.insert.koin.koin.compose)

    // Images and files. Coil draws the collection covers and FileKit asks for one: on Linux the
    // only real chooser is the XDG Desktop Portal, which is a D-Bus service with no JDK binding,
    // and FileKit ships the D-Bus client for it rather than this app shelling out to `zenity`
    // and dispatching on `os.name`. See `AzkarImageLoader` for where the two meet.
    implementation(libs.io.coil.kt.coil3.coil.compose)
    implementation(libs.io.github.vinceglb.filekit.dialogs)
    implementation(libs.io.github.vinceglb.filekit.coil)

    // `runTest` supplies virtual time and a deterministic scheduler, which is what makes the
    // library's debounced search assertable without a real 180 ms wait.
    testImplementation(libs.org.jetbrains.kotlinx.kotlinx.coroutines.test)

    // Compose UI tests, for the controls. `runComposeUiTest` renders off-screen and drives input
    // through `mainClock`, so the recomposition cost of a hover can be counted a frame at a time
    // instead of being guessed at from a profiler attached to a live desktop session.
    testImplementation(libs.org.jetbrains.compose.ui.test)

    // `MockEngine` stands in for the CIO engine the app installs, so the geocoding client can be
    // driven through canned responses without a socket. `ktor-client-mock` is already in the
    // version catalog above the `ktor` line; this is where it becomes a test dependency.
    testImplementation(libs.io.ktor.ktor.client.mock)
}

// Conveyor's cross-platform inputs. The plugin creates one configuration per OS/CPU
// combination and Conveyor reads them back through `printConveyorConfig` (see the hashbang
// include in `conveyor.conf`). Each one resolves the Compose Desktop artifact for that
// platform, which is how a single Linux CI runner packages all three operating systems.
// `compose.desktop.currentOs` above covers a local `./gradlew :app:run`; these cover shipping.
dependencies {
    linuxAmd64(compose.desktop.linux_x64)
    macAmd64(compose.desktop.macos_x64)
    macAarch64(compose.desktop.macos_arm64)
    windowsAmd64(compose.desktop.windows_x64)
}

// Compose Multiplatform re-publishes part of the Compose runtime under
// `org.jetbrains.compose.runtime:<module>-desktop`, but those artifacts are empty
// stubs (or redirectors): the classes live in the `androidx.compose.runtime`
// coordinates, which the androidx lifecycle stack in the same graph depends on
// directly. Both land on the classpath under the same file name, so jar/dup
// checks complain. Two more deliberate drops: the material3 1.13-alpha skew
// drags in the JetBrains-coordinate `ui-skiko` POM, whose onlyJvm target
// depends on the 43 MB aggregate `skiko-awt-runtime-all` (natives for every OS)
// instead of the per-OS runtime the Compose plugin's own resolution picks.
//
// The rules are attached PER DECLARED DEPENDENCY rather than to each
// configuration. Configuration-level `exclude(...)` rules stay local to the
// configuration they're declared on, and the Conveyor plugin resolves the
// shipped classpath through its own detached configuration (which copies the
// declared dependencies but not the exclusions declared next to them), so
// config-level rules let the stubs and the aggregate jar slip back into the
// emitted package config. Scoped per dependency, the rules travel with the
// dependency into every resolution — including Conveyor's. Dropping the above
// loses nothing anywhere: the androidx runtime ships the real classes and the
// per-OS skiko runtimes carry the natives (shared classpath for `:app:run`,
// OS-specific Conveyor inputs — and desktop test runtimes get the Linux
// natives via `compose.desktop.currentOs`).
configurations.configureEach {
    dependencies.withType<ExternalModuleDependency>().configureEach {
        exclude(group = "org.jetbrains.compose.runtime", module = "runtime-desktop")
        exclude(group = "org.jetbrains.compose.runtime", module = "runtime-saveable-desktop")
        exclude(group = "org.jetbrains.skiko", module = "skiko-awt-runtime-all")
    }
}

// The Gradle `application` plugin is deliberately NOT applied: `org.jetbrains.compose`
// contributes its own `run` task and the two collide. Everything that plugin used to
// give us comes from `compose.desktop { application { } }` at the bottom instead.
tasks.jar {
    archiveFileName.set("azkar-app.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest { attributes["Main-Class"] = desktopMainClass }

    // Include resources (bundles, fonts, etc.)
    from(sourceSets.main.get().output)
}

// ---------------------------------------------------------------------------
// Off-screen screenshot harness.
//
// Rasterises a screen to a PNG without needing a display server, which is how
// the UI gets visually verified on a headless machine. It lives in its own
// source set so it never ends up in the shipped jar.
//
//   ./gradlew :app:runScreenshot -Pazkar.screenshot.screen=collections
//
// `screen` accepts `app` (default), `home`, `settings`, `collections` or
// `collection-detail`. Two extra switches exercise the appearance axes that
// are otherwise only reachable through user interaction, so a single headless
// run can check both of them:
//
//   ./gradlew :app:runScreenshot -Pazkar.screenshot.locale=ar   # RTL mirroring
//   ./gradlew :app:runScreenshot -Pazkar.screenshot.dark=true   # dark palette
//
// `collection-detail` has one further switch, because its member grid is only half
// reachable from the way the page opens:
//
//   ./gradlew :app:runScreenshot -Pazkar.screenshot.screen=collection-detail \
//       -Pazkar.screenshot.memberView=list
// ---------------------------------------------------------------------------

val screenshot =
    sourceSets.create("screenshot") {
        compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
    }

configurations["screenshotImplementation"].extendsFrom(configurations.implementation.get())

configurations["screenshotRuntimeOnly"].extendsFrom(configurations.runtimeOnly.get())

tasks.register<JavaExec>("runScreenshot") {
    group = "verification"
    description = "Rasterise a screen to a PNG (headless UI check)"
    mainClass.set("com.azkar.ScreenshotKt")
    classpath = screenshot.runtimeClasspath
    // Skiko loads its native library through System.loadLibrary; DataStore's bundled protobuf
    // currently uses sun.misc.Unsafe on Java 25.
    jvmArgs("--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow")
    systemProperty(
        "azkar.screenshot.out",
        providers.gradleProperty("azkar.screenshot.out").getOrElse("build/screenshot.png"),
    )
    providers.gradleProperty("azkar.screenshot.screen").orNull?.let {
        systemProperty("azkar.screenshot.screen", it)
    }
    providers.gradleProperty("azkar.screenshot.width").orNull?.let {
        systemProperty("azkar.screenshot.width", it)
    }
    providers.gradleProperty("azkar.screenshot.height").orNull?.let {
        systemProperty("azkar.screenshot.height", it)
    }
    providers.gradleProperty("azkar.screenshot.locale").orNull?.let {
        systemProperty("azkar.screenshot.locale", it)
    }
    providers.gradleProperty("azkar.screenshot.dark").orNull?.let {
        systemProperty("azkar.screenshot.dark", it)
    }
    providers.gradleProperty("azkar.screenshot.memberView").orNull?.let {
        systemProperty("azkar.screenshot.memberView", it)
    }
    dependsOn(tasks.named("screenshotClasses"))
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    // Match the desktop runtime flags so UI and persistence tests do not emit JDK access warnings.
    jvmArgs("--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow")
    // The Compose plugin's `mainClass` is the single source of truth for the packaged entry
    // point, and Conveyor derives the Linux `StartupWMClass` from it. The desktop-entry test
    // needs that exact value rather than a copy of it, so it is handed over instead of
    // being re-typed in the test.
    systemProperty("azkar.desktop.mainClass", desktopMainClass)
}

// ---------------------------------------------------------------------------
// Packaging.
//
// Hydraulic Conveyor is the only thing that packages this app. `conveyor.conf`
// at the repository root describes the packages and the update feeds, and
// `.github/workflows/release.yml` runs it.
//
// The Compose Multiplatform plugin's own `nativeDistributions` block used to sit
// here as a second, jpackage-based packager. It has been removed, and the reason
// is Linux-only and not cosmetic.
//
// jpackage builds its desktop entry from
// `classes/jdk/jpackage/internal/resources/template.desktop` inside the JDK, and
// that template has no `StartupWMClass` key at all. A desktop entry without one
// can never be matched to the running window -- the desktop manager matches on the
// window's `WM_CLASS` -- so a jpackage-built .deb puts the app in the dock as an
// unassociated window: a generic Java icon, and the window's raw `WM_CLASS` as
// the label. Conveyor's desktop entry does carry `StartupWMClass`, and the
// Conveyor Gradle plugin derives it from the Compose `mainClass` below, so the
// two agree by construction.
//
// The Compose extension is still the source of everything Conveyor needs:
//   - `mainClass`  -> `app.jvm.gui.main-class` and `StartupWMClass`
//   - `jvmArgs`    -> `app.jvm.options`
//   - `buildTypes` -> the ProGuard-shrunk jars that Conveyor packages
//
// The jpackage-only settings went with it: `targetFormats`, the per-OS icon
// files (Conveyor derives .ico/.icns/hicolor sizes from the one PNG named in
// `conveyor.conf`) and the `modules()` list (Conveyor bundles a full JDK, so
// there is no jlink image to select modules for).
// ---------------------------------------------------------------------------

// Compose Multiplatform's own resource system, which replaces the hand-rolled
// `ResourceBundle` + typed `Strings` facade (see `i18n/Strings.kt`).
//
// The generated `Res` class is public because background work — the reminder
// scheduler and the filtering service — has to resolve strings outside a
// composition, and the generated accessors are the only way to name a key.
compose.resources {
    publicResClass = true
    packageOfResClass = "com.azkar.generated.resources"
    generateResClass = always
}

// ServiceLoader providers that a rule elsewhere already keeps. Emitting them here
// would be a duplicate line, and a reader would have to open three files to find
// out which keep is actually load-bearing. Running :app:proguardReleaseJars with
// and without this generated file shows that only the dbus and Exposed providers
// are load-bearing; everything below is covered by a broader rule.
val alreadyKeptProviders =
    listOf(
        // app/proguard-rules.pro
        "ch.qos.logback.",
        "io.ktor.",
        "org.h2.",
        // default-compose-desktop-rules.pro, which carries `-keep class kotlin.** { *; }`
        "kotlin.reflect.",
        // ditto: it has an explicit `-keep class kotlinx.coroutines.swing.SwingDispatcherFactory`
        "kotlinx.coroutines.swing.SwingDispatcherFactory",
    )

val collectProguardRules =
    tasks.register("collectProguardRules") {
        val runtimeClasspath = configurations.named("runtimeClasspath")
        val output = layout.buildDirectory.file("compose/proguard-generated.pro")
        inputs.files(runtimeClasspath)
        outputs.file(output)
        doLast {
            val providers = sortedSetOf<String>()
            runtimeClasspath.get().files.filter { it.name.endsWith(".jar") }.forEach { jar ->
                JarFile(jar).use { zip ->
                    zip
                        .entries()
                        .asSequence()
                        .filter { it.name.startsWith("META-INF/services/") }
                        .forEach { entry ->
                            zip.getInputStream(entry).bufferedReader().useLines { lines ->
                                lines.map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.forEach {
                                    providers +=
                                        it
                                }
                            }
                        }
                }
            }
            val uncovered =
                providers.filterTo(sortedSetOf()) { provider ->
                    alreadyKeptProviders.none { provider.startsWith(it) }
                }
            output.get().asFile.apply {
                parentFile.mkdirs()
                writeText(
                    buildString {
                        appendLine("# Generated by :app:collectProguardRules - do not edit.")
                        appendLine()
                        appendLine("# Classes named in META-INF/services are loaded by name, so shrinking")
                        appendLine("# would delete them and the app would fail with `Provider ... not found`.")
                        appendLine("# Providers already held by a broader keep are left out; see")
                        appendLine("# `alreadyKeptProviders` in this build script.")
                        uncovered.forEach { appendLine("-keep class $it { *; }") }
                    },
                )
            }
            logger.lifecycle(
                "Collected ${uncovered.size} service providers, " +
                    "${providers.size - uncovered.size} already kept elsewhere",
            )
        }
    }

compose.desktop {
    application {
        mainClass = desktopMainClass
        // Skiko loads its native library through System.loadLibrary; DataStore's bundled protobuf
        // currently uses sun.misc.Unsafe on Java 25.
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
        jvmArgs += "--sun-misc-unsafe-memory-access=allow"

        // Shrink the release classpath with ProGuard. The desktop classpath has no
        // R8/tree-shaking, so unminified packages ship ~37MB of unused Material
        // icons (material-icons-extended-desktop) among other dead weight.
        buildTypes.release {
            proguard {
                isEnabled.set(true)
                optimize.set(false)
                // `obfuscate` is off on purpose, and it is load-bearing rather than
                // cosmetic. ProGuard would rename `com.azkar.MainKt`, and the window's
                // `WM_CLASS` is derived from that class name, while the packaged
                // `StartupWMClass` is derived from the Gradle `mainClass` above. Rename
                // one side and the dock stops matching the window to its desktop entry:
                // the app still runs, it just shows up with a generic Java icon.
                obfuscate.set(false)
                configurationFiles.from(project.file("proguard-rules.pro"))
                configurationFiles.from(collectProguardRules)
            }
        }
    }
}

// Conveyor publishes whatever version this project declares, so a release tag that
// disagrees with it would ship an installer whose name lies about its contents.
// GitHub Actions sets GITHUB_REF_NAME to the pushed tag; locally the check is skipped.
tasks.register("checkTagParity") {
    val tag = providers.environmentVariable("GITHUB_REF_NAME")
    val version = providers.provider { project.version.toString() }
    doLast {
        val tagName = tag.orNull
        if (tagName == null) {
            logger.lifecycle("GITHUB_REF_NAME is unset; skipping the release tag check")
            return@doLast
        }
        val declared = version.get()
        check(tagName.removePrefix("v") == declared) {
            "Tag $tagName does not match the Gradle app version '$declared'"
        }
        logger.lifecycle("Tag version parity OK: $declared")
    }
}

// Local rehearsal of .github/workflows/release.yml with act (https://github.com/nektos/act). It
// builds and signs every platform, so give it half an hour, and it needs Docker plus the `conveyor`
// CLI on PATH. The signing key is a throwaway under ~/.conveyor-acttest; publishing fails on the
// dummy GITHUB_TOKEN, which is the point - everything up to that step is the real workflow.
val actKeyDirectory = file("${System.getProperty("user.home")}/.conveyor-acttest")
val actWorkDirectory = layout.buildDirectory.dir("act")
val releaseVersion = version.toString()

tasks.register<Exec>("verifyReleaseWorkflow") {
    group = "verification"
    description = "Runs the release workflow locally with act, using a throwaway signing key."
    // Let Ctrl+C reach act directly so it can tear the container down itself.
    standardInput = System.`in`

    val secrets = actWorkDirectory.map { it.file("secrets.env").asFile }
    val event = actWorkDirectory.map { it.file("push-tag.json").asFile }
    // The container is thrown away when act exits, so Conveyor installers are only
    // recoverable if act uploads them to this host directory.
    val artifacts = actWorkDirectory.map { it.dir("artifacts") }
    // act copies its working directory into the container, and this repository is
    // mostly .git and build output. Rehearse from a pruned copy so that copy stays
    // small enough for the container's temporary storage.
    val staging = actWorkDirectory.map { it.dir("staging") }
    val repository = rootProject.layout.projectDirectory.asFile
    // The generated key is unencrypted, so the workflow's passphrase is empty unless overridden.
    val passphrase = providers.gradleProperty("actSigningPassphrase").orElse("")

    doFirst {
        actWorkDirectory.get().asFile.mkdirs()
        artifacts.get().asFile.mkdirs()
        val keyFile = actKeyDirectory.resolve("defaults.conf")
        if (!keyFile.exists()) {
            providers
                .exec {
                    commandLine(
                        "conveyor",
                        "keys",
                        "generate",
                        "--no-passphrase",
                        "--no-keyring",
                        "-o",
                        actKeyDirectory.absolutePath,
                    )
                }.result
                .get()
                .assertNormalExitValue()
        }
        val signingKey =
            keyFile
                .readLines()
                .first { it.startsWith("app.signing-key") }
                .substringAfter('=')
                .trim()
                .trim('"')
        secrets.get().writeText("SIGNING_KEY=$signingKey\nSIGNING_PASSPHRASE=${passphrase.get()}\n")
        // act reads the tag from the event payload; a branch ref would fail `checkTagParity`.
        event.get().writeText("""{"ref":"refs/tags/v$releaseVersion"}""")

        val stagedDirectory = staging.get().asFile
        stagedDirectory.deleteRecursively()
        stagedDirectory.mkdirs()
        providers
            .exec {
                commandLine(
                    "rsync",
                    "-a",
                    "--delete",
                    "--exclude=.git",
                    "--exclude=.gradle",
                    "--exclude=build",
                    "--exclude=.idea",
                    "--exclude=conveyor.output",
                    "--exclude=.conveyor",
                    "${repository.path}/",
                    stagedDirectory.absolutePath,
                )
            }.result
            .get()
            .assertNormalExitValue()
    }

    workingDir = staging.get().asFile

    commandLine(
        "act",
        "push",
        "--workflows",
        staging
            .get()
            .asFile
            .resolve(".github/workflows/release.yml")
            .absolutePath,
        "--eventpath",
        event.get().absolutePath,
        "--secret-file",
        secrets.get().absolutePath,
        "--platform",
        "ubuntu-latest=catthehacker/ubuntu:act-latest",
        "--artifact-server-path",
        artifacts.get().asFile.absolutePath,
    )
}
