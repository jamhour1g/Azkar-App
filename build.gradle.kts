/*
 * The root project owns the sources that are shared by every module and therefore cannot live
 * inside any single one of them. Today that is only the log-capture helper in
 * `config/testing`, which `azkar-common` compiles into the test source set of all three modules.
 *
 * The formatting configuration is duplicated from that convention plugin rather than shared,
 * because Spotless refuses any target outside the project directory it is configured on: a module
 * cannot format `config/testing`, so the root project has to. Both sides rely on the repository
 * `.editorconfig` for the style itself (`ktlint_official`, 120 columns), so the two configurations
 * cannot drift in behaviour.
 *
 * `config/detekt` and `config/logback` are not listed here because they are XML, which Spotless
 * does not format.
 */
import com.diffplug.spotless.LineEnding

// Applied unversioned: `buildSrc` already puts Spotless on the build classpath, pinning it to
// `com-diffplug-spotless` in the version catalog. This mirrors how `:app` applies the plugin.
plugins {
    id("com.diffplug.spotless")
    alias(libs.plugins.org.jetbrains.compose) apply false
    alias(libs.plugins.org.jetbrains.compose.hot.reload) apply false
}

// Spotless resolves the ktlint CLI from here, exactly as `azkar-common` does for every module.
repositories { mavenCentral() }

spotless {
    // The repository stores LF and checks out LF — `.gitattributes` says so — so the formatter
    // asks for LF as well. Left to its default, Spotless takes the line endings from git's
    // configuration, which differs between a Linux and a Windows machine, and the two then
    // disagree about the same file.
    lineEndings = LineEnding.UNIX

    kotlin {
        target("config/testing/**/*.kt")
        targetExclude("**/build/**")
        ktlint()
    }
}

// Mirrors the local/CI fork in `azkar-common`: a local run rewrites, CI verifies.
tasks.build {
    val isCI = providers.environmentVariable("CI").isPresent
    if (isCI) {
        dependsOn(tasks.named("spotlessCheck"))
    } else {
        dependsOn(tasks.named("spotlessApply"))
    }
}
