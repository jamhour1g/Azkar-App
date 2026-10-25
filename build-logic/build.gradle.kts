repositories {
    gradlePluginPortal()
    mavenCentral()
}

plugins {
    `kotlin-dsl`
}

dependencies {
    testImplementation(kotlin("test-junit"))

    implementation(plugin(libs.plugins.com.diffplug.spotless))
    implementation(plugin(libs.plugins.dev.detekt))

    // detekt's plugin applies the Kotlin Gradle plugin to discover source sets, so
    // its classes must be on this build's classpath for `azkar-common` to apply it.
    // Application modules request the same plugins from the shared version catalog.
    implementation(plugin(libs.plugins.org.jetbrains.kotlin.jvm))
    implementation(plugin(libs.plugins.org.jetbrains.kotlin.plugin.compose))
    implementation(plugin(libs.plugins.jetbrains.compose.multiplatform))
    implementation(plugin(libs.plugins.jetbrains.compose.hot.reload))
    // The serialization *compiler* plugin, for the same reason as the two above: `:app`
    // requests it from the same version catalog as the Kotlin plugin.
    implementation(plugin(libs.plugins.org.jetbrains.kotlin.plugin.serialization))
    implementation(plugin(libs.plugins.dev.nucleusframework))

    // testBalloon's Gradle plugin is the half that installs a Kotlin *compiler* plugin,
    // which is what turns the `val X by testSuite { }` properties in the test sources
    // into a JUnit Platform entry point. It sits here for `azkar-common` to apply.
    implementation(plugin(libs.plugins.de.infix.test.balloon))
}

// Transform a version-catalog plugin alias into a dependency notation for this build.
fun plugin(plugin: Provider<PluginDependency>) =
    plugin.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }
