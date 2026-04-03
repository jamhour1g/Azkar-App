import com.diffplug.spotless.extra.wtp.EclipseWtpFormatterStep
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
    id("jacoco")
}

val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

dependencies {

    // SLF4J API for all modules
    implementation(libs.findLibrary("org-slf4j-slf4j-api").get())

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
        palantirJavaFormat("2.90.0")
        formatAnnotations()
    }

    kotlin {
        target("src/**/*.kt")
        ktfmt()
    }

    kotlinGradle {
        target("*.gradle.kts")
        ktfmt()
    }

    format("fxml") {
        target("src/*/resources/**/*.fxml")
        eclipseWtp(EclipseWtpFormatterStep.XML)
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// https://spotbugs-gradle-plugin.netlify.app/com/github/spotbugs/snom/spotbugsextension
spotbugs {
    toolVersion = "4.9.4"
    effort = com.github.spotbugs.snom.Effort.MAX
    reportLevel = com.github.spotbugs.snom.Confidence.LOW
    showProgress = true
    ignoreFailures = !System.getenv().containsKey("CI")
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
            disableWarningsInGeneratedCode.set(true)
            // Do NOT disable all checks — keep the defaults and enable NullAway
            check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
            option("NullAway:JSpecifyMode", "true")
            option("NullAway:OnlyNullMarked", "true")
            // Skip NullAway analysis on annotation-processor-generated classes
            // (e.g. Hibernate metamodel *_.java and Jakarta Data repository impls)
            option("NullAway:ExcludedClassAnnotations", "jakarta.annotation.Generated")
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
    finalizedBy(tasks.named("jacocoTestReport"))
}

// JaCoCo — test coverage reporting
jacoco {
    toolVersion = "0.8.13"
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.test)
    reports {
        xml.required = true   // needed for CI integrations
        html.required = true  // human-readable report
        csv.required = false
    }
}

// In CI: fail on formatting violations; locally: auto-fix before build
tasks.build {
    val isCI = providers.environmentVariable("CI").isPresent
    if (isCI) {
        dependsOn(tasks.named("spotlessCheck"))
    } else {
        dependsOn(tasks.spotlessApply)
    }
}
