repositories {
    gradlePluginPortal()
    mavenCentral()
}

plugins {
    `kotlin-dsl`
}

dependencies {

    implementation(plugin(libs.plugins.com.diffplug.spotless))
    implementation(plugin(libs.plugins.com.github.spotbugs))
    implementation(plugin(libs.plugins.net.ltgt.errorprone))
    implementation(plugin(libs.plugins.io.freefair.lombok))

}

// Helper function that transforms a Gradle Plugin alias from a
// Version Catalog into a valid dependency notation for buildSrc
fun plugin(plugin: Provider<PluginDependency>) =
    plugin.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }
