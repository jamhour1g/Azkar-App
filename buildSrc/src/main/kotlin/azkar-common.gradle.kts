import net.ltgt.gradle.errorprone.errorprone

repositories {
    mavenCentral()
}

plugins {
    id("io.freefair.lombok")
    id("com.diffplug.spotless")
    id("com.github.spotbugs")
    id("net.ltgt.errorprone")
    id("java")
}

val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

dependencies {

    // JSpecify: annotations only; don't ship them at runtime
    compileOnly(libs.findLibrary("org-jspecify-jspecify").get())
    testCompileOnly(libs.findLibrary("org-jspecify-jspecify").get())

    // Error Prone + NullAway for ALL modules
    errorprone(libs.findLibrary("com-google-errorprone-error-prone-core").get())
    errorprone(libs.findLibrary("com-uber-nullaway-nullaway").get())

    // JUnit (shared default)
    testImplementation(platform(libs.findLibrary("org-junit-junit-bom").get()))
    testImplementation(libs.findLibrary("org-junit-jupiter-junit-jupiter").get())
    testRuntimeOnly(libs.findLibrary("org-junit-platform-junit-platform-launcher").get())
    testImplementation(libs.findLibrary("org-assertj-assertj-core").get())
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    modularity.inferModulePath = true
}

// https://github.com/diffplug/spotless/tree/main/plugin-gradle#palantir-java-format
spotless {
    java {
        cleanthat()

        importOrder()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
        // https://github.com/prettier/prettier
        // Needs npm to be installed on the system
        prettier(
            mapOf(
                "prettier" to "3.6.2",
                // https://github.com/jhipster/prettier-java
                "prettier-plugin-java" to "2.7.3"
            )
        ).config(
            mapOf(
                "parser" to "java",
                "tabWidth" to 4,
                "plugins" to listOf(
                    "prettier-plugin-java"
                )
            )
        )
        formatAnnotations()
    }
}

// https://spotbugs-gradle-plugin.netlify.app/com/github/spotbugs/snom/spotbugsextension
spotbugs {
    toolVersion = "4.9.4"
    effort = com.github.spotbugs.snom.Effort.MAX
    reportLevel = com.github.spotbugs.snom.Confidence.LOW
    showProgress = false // TODO: enable before commiting
    ignoreFailures = true // TODO: disable before commiting
    reportsDir = file("${layout.buildDirectory}/reports/spotbugs")
    projectName = name
    release = version.toString()
    maxHeapSize = "512m"
    jvmArgs = listOf("-Duser.language=en")
}

// Error Prone + NullAway for all Java compilations
tasks.withType<JavaCompile>().configureEach {
    options.apply {
        encoding = "UTF-8"

        errorprone {
            // Do NOT disable all checks — keep the defaults and enable NullAway
            check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
            option("NullAway:JSpecifyMode", "true")
            option("NullAway:OnlyNullMarked", "true")
        }

        // Make tests less strict (disable NullAway for test sources only)
        if (name.contains("test", ignoreCase = true)) {
            errorprone.disable("NullAway")
        }

    }

}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

// Keep code formatted before build
tasks.build {
    dependsOn(tasks.spotlessApply)
}