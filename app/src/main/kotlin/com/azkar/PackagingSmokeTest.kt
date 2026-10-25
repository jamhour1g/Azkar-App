package com.azkar

import com.azkar.data.config.DatabaseManager
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path

internal fun verifyPackagedRuntime() {
    val profile = Path.of(checkNotNull(System.getProperty("azkar.packaging.smoke.profile")))
    check(Files.isSameFile(profile, Path.of(System.getProperty("user.home"))))
    check(Files.isRegularFile(profile.resolve(".packaging-smoke-test")))
    DatabaseManager().use { it.warmUp() }
    KotlinLogging.logger {}.info { "AZKAR_PACKAGING_SMOKE_OK" }
}
