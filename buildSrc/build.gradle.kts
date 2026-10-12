repositories {
    gradlePluginPortal()
    mavenCentral()
}

plugins {
    `kotlin-dsl`
}

dependencies {

    implementation(plugin(libs.plugins.com.diffplug.spotless))
    implementation(plugin(libs.plugins.dev.detekt))

    // detekt's plugin applies the Kotlin Gradle plugin to discover source sets, so
    // its classes must be on the buildSrc classpath for `azkar-common` to apply it.
    // Owning both Kotlin plugins here means `:app` applies them unversioned, and
    // guarantees the Compose compiler always matches the Kotlin version.
    implementation(plugin(libs.plugins.org.jetbrains.kotlin.jvm))
    implementation(plugin(libs.plugins.org.jetbrains.kotlin.plugin.compose))
    // The serialization *compiler* plugin, for the same reason as the two above: `:app`
    // applies it unversioned because buildSrc owns the version, which is what guarantees
    // the generated serializers match the Kotlin version exactly.
    implementation(plugin(libs.plugins.org.jetbrains.kotlin.plugin.serialization))

    // testBalloon's Gradle plugin is the half that installs a Kotlin *compiler* plugin,
    // which is what turns the `val X by testSuite { }` properties in the test sources
    // into a JUnit Platform entry point. It has to sit on the buildSrc classpath for
    // `azkar-common` to apply it unversioned, exactly like the plugins above.
    implementation(plugin(libs.plugins.de.infix.test.balloon))

}

// Helper function that transforms a Gradle Plugin alias from a
// Version Catalog into a valid dependency notation for buildSrc
fun plugin(plugin: Provider<PluginDependency>) =
    plugin.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }
