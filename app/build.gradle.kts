import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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
}

version = "0.1.0"

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
    // The tray reaches the OS through JNA and D-Bus rather than through the JDK, so the packaging tasks
    // below are the only thing that has to care: `packageDeb`/`packageMsi`/`packageDmg` ship a plain
    // jar and a JVM, and nothing here shrinks or rewrites bytecode. If an uber-jar task is ever added
    // it will need the library's documented keep rules (`com.sun.jna.**` and
    // `dev.nucleusframework.composenativetray.**`) -- JNA resolves its native functions by reflection at
    // call time, so a shrinking build would break the tray silently at runtime.
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

// Compose Multiplatform 1.12.x re-publishes the Compose runtime under
// `org.jetbrains.compose.runtime:<module>-desktop`, but those artifacts are empty
// stubs: the classes live in the `androidx.compose.runtime` coordinates, which the
// androidx lifecycle artifacts in the same graph depend on directly. Both land on
// the classpath under the same file name, so `distZip`/`distTar`/`installDist` fail
// with "duplicate ... runtime-desktop-1.12.1.jar". Dropping the stubs leaves the
// androidx jars as the single source of the runtime classes.
configurations
    .matching { it.isCanBeResolved }
    .configureEach {
        exclude(group = "org.jetbrains.compose.runtime", module = "runtime-desktop")
        exclude(group = "org.jetbrains.compose.runtime", module = "runtime-saveable-desktop")
    }

// The Gradle `application` plugin is deliberately NOT applied: `org.jetbrains.compose`
// contributes its own `run` task and the two collide. Everything that plugin used to
// give us comes from `compose.desktop { application { } }` at the bottom instead.
tasks.jar {
    archiveFileName.set("azkar-app.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest { attributes["Main-Class"] = "com.azkar.MainKt" }

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
    // Skiko needs JNI access; DataStore's bundled protobuf currently uses sun.misc.Unsafe on Java 25.
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
}

// ---------------------------------------------------------------------------
// Native packaging via the Compose Multiplatform plugin (jpackage under the
// hood). Per-host-OS task names are suffixed, so on Linux you get
// `packageDeb` / `packageUberJarForCurrentOS`; on Windows `packageMsi` /
// `packageExe`; on macOS `packageDmg` / `packagePkg`.
//
//   ./gradlew :app:run                          — run from the IDE/classpath
//   ./gradlew :app:packageUberJarForCurrentOS   — fat jar for the current OS
//   ./gradlew :app:packageDeb                   — native installer (Linux)
//   ./gradlew :app:packageDistributionForCurrentOS — unsigned native image
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

compose.desktop {
    application {
        mainClass = "com.azkar.MainKt"
        // Skiko needs JNI access; DataStore's bundled protobuf currently uses sun.misc.Unsafe on Java 25.
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
        jvmArgs += "--sun-misc-unsafe-memory-access=allow"

        nativeDistributions {
            // `AppImage` is deliberately excluded: it needs FUSE at runtime, which is
            // unavailable on most CI runners. `Deb` is the portable Linux artifact.
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)

            // Debian package names must be lowercase, so the package is `azkar`
            // while the user-visible application name stays `Azkar`.
            packageName = "Azkar"
            packageVersion = project.version.toString()
            description =
                "Azkar — a companion app for daily adhkar: prayer times, remembrances, and reminders."
            vendor = "jamhour1g"
            copyright = "© 2025 jamhour1g"

            // Skiko draws through AWT, so `java.desktop` (which transitively pulls
            // java.datatransfer, java.xml and java.prefs) is mandatory. H2/Hikari/Exposed
            // need java.sql + java.naming, and Logback needs java.logging.
            //
            // `jdk.security.auth` is there for the file dialog and only for the file dialog: the
            // XDG Desktop Portal authenticates the requester against the session bus over
            // GSSAPI, and this is the module that speaks it. Running from the classpath resolves
            // it anyway -- the unnamed module's default root set is every module in the image that
            // exports something -- so the packaged builds are the only ones where leaving it out
            // would be felt, which is exactly the case nobody would otherwise catch.
            modules(
                "java.desktop",
                "java.sql",
                "java.naming",
                "java.logging",
                "jdk.unsupported",
                "jdk.security.auth",
            )

            // The app mark, rasterised from `AzkarIcons.Brand` — the same `ImageVector` the header
            // lockup draws, so the taskbar entry, the dock tile, the desktop shortcut and the
            // window's own title bar all show one mark rather than four. Without these jpackage
            // falls back to the generic Java coffee cup, which is what the taskbar showed before.
            // `Main.kt` sets the in-app window icon from the vector directly, so these three files
            // exist only for the packager, which needs real image files rather than draw commands.
            val iconDirectory = rootProject.layout.projectDirectory.dir("assets/icons")
            linux {
                // Debian package names must be lowercase; the user-visible name stays
                // `Azkar` via `packageName` on the distribution above.
                packageName = "azkar"
                shortcut = true
                iconFile = iconDirectory.file("azkar.png").asFile
            }
            windows {
                iconFile = iconDirectory.file("azkar.ico").asFile
                menu = true
                perUserInstall = true
            }
            macOS {
                bundleID = "com.azkar.app"
                dockName = "Azkar"
                iconFile = iconDirectory.file("azkar.icns").asFile
            }
        }
    }
}
