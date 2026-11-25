package com.azkar.updates

import com.azkar.settings.withInMemorySettings
import de.infix.testBalloon.framework.core.testSuite
import dev.nucleusframework.updater.InstallFailure
import dev.nucleusframework.updater.provider.GenericProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import org.assertj.core.api.Assertions.assertThat

// TestBalloon generates the test entry point, but the IDE does not see that usage.
@Suppress("unused")
val MockUpdateSuite by testSuite("Local mock updates") {
    test("uses the development fixture to check, retry, and simulate installation") {
        MockUpdateFixture("download-failed", null, "1.0.0").use { fixture ->
            HttpClient(MockEngine { error("Fixture updates must not contact GitHub") }).use { client ->
                withInMemorySettings { scope, settings, _ ->
                    val manager = fixture.manager(settings, client, scope)
                    manager.checkManually()
                    manager.download((manager.status.value as UpdateStatus.Available).info)
                    assertThat(manager.status.value).isInstanceOf(UpdateStatus.Failed::class.java)

                    fixture.clearFaults()
                    manager.checkManually()
                    manager.download((manager.status.value as UpdateStatus.Available).info)
                    val ready = manager.status.value as UpdateStatus.ReadyToInstall
                    manager.checkAutomatically()
                    assertThat(manager.status.value).isSameAs(ready)
                    manager.installAndRestart(ready.file)
                    assertThat(manager.currentVersion).isEqualTo("2.0.0")
                    assertThat(manager.status.value).isEqualTo(UpdateStatus.Current)
                    manager.checkManually()
                    assertThat(manager.status.value).isEqualTo(UpdateStatus.Current)
                }
            }
        }
    }

    test("manual retry offers the downgrade recorded by the installer") {
        MockUpdateFixture("check-failed", null, "1.0.0").use { fixture ->
            HttpClient(MockEngine { error("Fixture updates must not contact GitHub") }).use { client ->
                withInMemorySettings { scope, settings, _ ->
                    val manager =
                        UpdateManager(
                            settings,
                            client,
                            scope,
                            provider = GenericProvider(fixture.baseUrl),
                            packageFormat = fixture.format,
                            initialVersion = "3.0.0",
                            readInstallFailure = { InstallFailure("2.0.0", 126, "") },
                        )
                    manager.checkAutomatically()
                    assertThat(manager.status.value).isInstanceOf(UpdateStatus.Failed::class.java)

                    manager.checkManually()
                    assertThat(manager.status.value).isInstanceOf(UpdateStatus.Failed::class.java)
                    manager.checkAutomatically()
                    fixture.clearFaults()
                    manager.checkManually()
                    val available = manager.status.value as UpdateStatus.Available
                    assertThat(available.info.version).isEqualTo("2.0.0")
                    assertThat(available.isDowngrade).isTrue()
                    manager.download(available.info)
                    val ready = manager.status.value as UpdateStatus.ReadyToInstall
                    val signature = ready.file.resolveSibling("${ready.file.fileName}.asc")
                    java.nio.file.Files
                        .createFile(signature)
                    manager.selectChannel(UpdateChannel.DEV)
                    assertThat(ready.file.parent).doesNotExist()
                }
            }
        }
    }

    test("a failed channel switch can be retried and a scheduled check preserves the downgrade offer") {
        MockUpdateFixture("check-failed", null, "3.0.0-beta.1").use { fixture ->
            HttpClient(MockEngine { error("Fixture updates must not contact GitHub") }).use { client ->
                withInMemorySettings { scope, settings, _ ->
                    val manager =
                        UpdateManager(
                            settings,
                            client,
                            scope,
                            provider = GenericProvider(fixture.baseUrl),
                            packageFormat = fixture.format,
                            initialVersion = "5.0.0-beta.1",
                        )
                    manager.selectChannel(UpdateChannel.STABLE)
                    assertThat(manager.channel.value).isEqualTo(UpdateChannel.STABLE)
                    assertThat(manager.status.value).isInstanceOf(UpdateStatus.Failed::class.java)
                    manager.checkManually()
                    val failed = manager.status.value
                    assertThat(failed).isInstanceOf(UpdateStatus.Failed::class.java)

                    fixture.clearFaults()
                    manager.checkAutomatically()
                    assertThat(manager.status.value).isSameAs(failed)
                    manager.checkManually()
                    val available = manager.status.value as UpdateStatus.Available
                    assertThat(available.info.version).isEqualTo("4.0.0")
                    assertThat(available.isDowngrade).isTrue()

                    manager.checkAutomatically()
                    assertThat(manager.status.value).isSameAs(available)
                }
            }
        }
    }
}
