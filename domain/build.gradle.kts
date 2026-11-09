plugins {
    `java-library`
    id("azkar-common")
}

java {
    // `azkar-common` turns module-path inference on for every module. This module used to ship a
    // `module-info.java` listing five exported packages; it no longer does, because the Kotlin
    // sources are the whole module and Kotlin has no way to keep the descriptor in step with
    // them. `:data` and `:app` already opt out, so the three modules now agree.
    modularity.inferModulePath = false
}
