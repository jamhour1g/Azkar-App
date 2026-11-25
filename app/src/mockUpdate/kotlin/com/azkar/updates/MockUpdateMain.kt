package com.azkar.updates

import com.azkar.launchDesktop
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.qualifier.named
import org.koin.dsl.module

suspend fun main(args: Array<String>) {
    require(args.size <= 2) { "Expected a scenario and an optional installer format." }
    val scenario = args.getOrNull(0) ?: "success"
    val format = args.getOrNull(1)
    MockUpdateFixture(scenario, format, applicationVersion(useRuntimeVersion = false)).use { fixture ->
        Runtime.getRuntime().addShutdownHook(Thread(fixture::close, "azkar-mock-update-cleanup"))
        System.setProperty("user.home", fixture.profile.absolutePath)
        KotlinLogging.logger {}.info { "Mock updates (${fixture.format}, $scenario) on ${fixture.baseUrl}" }
        val updates =
            module {
                single { fixture.manager(get(), get(), get(named("updateScope"))) }
            }
        launchDesktop(emptyArray(), listOf(updates))
    }
}
