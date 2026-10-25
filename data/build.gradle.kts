plugins {
    `java-library`
    id("azkar-common")
}

java {
    // `azkar-common` turns module-path inference on for every module. Exposed is a
    // plain classpath library with no JPMS descriptor, and this module no longer
    // ships a `module-info.java`, so `:data` opts out like `:app` does.
    modularity.inferModulePath = false
}

dependencies {
    implementation(project(":domain"))

    // Database: Exposed owns the schema, the queries and the seed data.
    implementation(libs.org.jetbrains.exposed.exposed.core)
    implementation(libs.org.jetbrains.exposed.exposed.jdbc)
    implementation(libs.org.jetbrains.exposed.exposed.java.time)
    // The entity layer. Repositories address rows as `LongEntity` / `CompositeEntity`
    // instances bound to the same `Table` objects below, rather than as bare
    // `ResultRow`s and hand-written update blocks.
    implementation(libs.org.jetbrains.exposed.exposed.dao)
    // DDL generation against a live database, replacing the former SQL migrations.
    implementation(libs.org.jetbrains.exposed.exposed.migration.jdbc)

    // Embedded H2 (file-backed in the app, in-memory in tests).
    implementation(libs.com.h2database.h2)
    // Connection pool. Exposed opens a fresh connection per transaction when given a
    // plain JDBC URL, which is far too chatty for the AUTO_SERVER H2 file database.
    implementation(libs.com.zaxxer.hikari)

    // The domain ports are `suspend`, so the adapter that runs them needs a coroutine
    // builder. Exposed happens to drag coroutines-core in transitively; declaring it
    // keeps this module honest if Exposed ever stops depending on it.
    implementation(libs.org.jetbrains.kotlinx.kotlinx.coroutines.core)

    // This module owns the data half of the Koin graph (`dataModule`), so it needs the
    // container DSL. The Compose bindings stay in `:app`, which is the only module with a
    // UI.
    implementation(libs.io.insert.koin.koin.core)

    // The repositories and the cover storage take a `KLogger`, so the type is part of
    // this module's public API and belongs on a consumer's compile classpath.
    // `azkar-common` adds kotlin-logging as an `implementation` dependency of every
    // module; declaring it as `api` here is what lifts it out of this module's
    // implementation details.
    api(libs.io.github.oshai.kotlin.logging.jvm)

    testImplementation(libs.org.jetbrains.kotlinx.kotlinx.coroutines.test)
}
