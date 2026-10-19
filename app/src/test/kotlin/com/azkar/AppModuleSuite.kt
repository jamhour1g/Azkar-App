package com.azkar

import com.azkar.data.config.LibraryBootstrapper
import com.azkar.data.repo.exposed.ExposedCollectionReminderRepository
import com.azkar.data.repo.exposed.ExposedCollectionRepository
import com.azkar.data.repo.exposed.ExposedRecitationRepository
import com.azkar.data.repo.exposed.ExposedRemembranceRepository
import com.azkar.data.repo.exposed.ExposedTagRepository
import com.azkar.data.storage.CoverStorage
import com.azkar.domain.repo.CollectionReminderRepository
import com.azkar.domain.repo.CollectionRepository
import com.azkar.domain.repo.LibrarySeedState
import com.azkar.domain.repo.RecitationRepository
import com.azkar.domain.repo.RemembranceRepository
import com.azkar.domain.repo.TagRepository
import com.azkar.domain.service.CollectionReminderService
import com.azkar.domain.service.CollectionService
import com.azkar.domain.service.RecitationService
import com.azkar.domain.service.RemembranceService
import com.azkar.domain.service.TagService
import com.azkar.domain.service.TransactionRunner
import com.azkar.i18n.LocaleStore
import com.azkar.settings.SettingsStore
import com.azkar.ui.home.DomainHomeGateway
import com.azkar.ui.home.HomeGateway
import com.azkar.ui.library.data.CollectionHomeGateway
import com.azkar.ui.library.data.CollectionReminderGateway
import com.azkar.ui.library.data.CollectionsGateway
import com.azkar.ui.library.data.DomainCollectionsGateway
import com.azkar.ui.library.data.DomainReminderGateway
import com.azkar.ui.library.data.DomainTagsGateway
import com.azkar.ui.library.data.TagGateway
import com.azkar.ui.notifications.LocationDetector
import com.azkar.ui.notifications.NotificationActionRouter
import com.azkar.ui.notifications.NotificationPreferencesRepository
import com.azkar.ui.notifications.NotificationRuntime
import com.azkar.ui.notifications.ReminderCatalogueSource
import com.azkar.ui.notifications.StartupRegistration
import com.azkar.ui.notifications.presentation.NotificationPublisher
import com.azkar.ui.notifications.presentation.OverlayNotificationPresenter
import com.azkar.ui.notifications.presentation.SystemNotificationPresenter
import com.azkar.ui.shell.ReminderNotifier
import com.azkar.ui.shell.ThemePreferenceStore
import com.azkar.ui.shell.TypographyStore
import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.reflect.KClass

/**
 * What [appModule] promises to the composables that resolve out of it.
 *
 * `DataModuleSuite` covers the other half of the graph. This covers the half only this module knows
 * how to build, and what is worth asserting here is shape rather than behaviour: that every
 * definition resolves under the name a screen asks for, that the `bind` really does yield one object
 * under two names, that the adapters cannot be reached past the seam they are registered under, and
 * that the whole notification stack resolves without a composition existing. Each of those is
 * invisible to a resolution test alone, and each would otherwise surface as a crash on the first
 * window rather than as a failing test.
 *
 * The definitions are resolved over stubbed storage because the module, not the adapters, is what is
 * under test. `:app` could not use a real database in any case: `com.azkar.data` declares Exposed
 * `implementation`, so its tables are on this module's runtime classpath and not on its compile
 * classpath, and the database itself is `DatabaseManager`'s, opened under `~/.azkar`. The stubbing
 * stops at the ports `dataModule` registers — that is the seam between the two halves — so below it
 * every definition is the production one and above it every definition is `appModule`'s.
 */
val AppModuleSuite by testSuite("AppModule") {
    testFixture { StubbedGraph.start() } closeWith {
        close()
    } asParameterForAll {
        test("every definition resolves, asked for by the name the UI asks for") { graph ->
            val koin = graph.koin
            assertThat(
                listOf(
                    koin.get<SettingsStore>(),
                    koin.get<CollectionReminderGateway>(),
                    koin.get<CollectionsGateway>(),
                    koin.get<CollectionHomeGateway>(),
                    koin.get<HomeGateway>(),
                    koin.get<TagGateway>(),
                    koin.get<ThemePreferenceStore>(),
                    koin.get<LocaleStore>(),
                    koin.get<HttpClient>(),
                    koin.get<LocationDetector>(),
                    koin.get<NotificationPreferencesRepository>(),
                    koin.get<NotificationActionRouter>(),
                    koin.get<StartupRegistration>(),
                    koin.get<ReminderNotifier>(),
                    koin.get<NotificationPublisher>(),
                    koin.get<NotificationRuntime>(),
                    koin.get<OverlayNotificationPresenter>(),
                    koin.get<SystemNotificationPresenter>(),
                    koin.get<CoroutineDispatcher>(
                        org.koin.core.qualifier
                            .named("notificationDispatcher"),
                    ),
                    koin.get<CoroutineScope>(
                        org.koin.core.qualifier
                            .named("notificationScope"),
                    ),
                ),
            ).doesNotContainNull()
        }

        test("a single really is a single") { graph ->
            val koin = graph.koin
            assertThat(koin.get<SettingsStore>()).isSameAs(koin.get<SettingsStore>())
            assertThat(koin.get<HomeGateway>()).isSameAs(koin.get<HomeGateway>())
            assertThat(koin.get<CollectionReminderGateway>()).isSameAs(koin.get<CollectionReminderGateway>())
            assertThat(koin.get<CollectionsGateway>()).isSameAs(koin.get<CollectionsGateway>())
            assertThat(koin.get<TagGateway>()).isSameAs(koin.get<TagGateway>())
            assertThat(koin.get<HttpClient>()).isSameAs(koin.get<HttpClient>())
            assertThat(koin.get<NotificationPreferencesRepository>())
                .isSameAs(koin.get<NotificationPreferencesRepository>())
        }

        test("the library shelf and the home rail share one collections gateway") { graph ->
            val koin = graph.koin
            // The rail asks for `CollectionHomeGateway` and the shelf asks for `CollectionsGateway`,
            // and they have to be the same object: the rail's cards come from the very read model the
            // shelf mutates, so two instances would be two copies of one rule, free to drift apart.
            assertThat(koin.get<CollectionHomeGateway>()).isSameAs(koin.get<CollectionsGateway>())
        }

        test("the reminder gateway is a single in its own right") { graph ->
            val koin = graph.koin
            // Distinct from the collections gateway on purpose, and this is where that is pinned: the
            // shelf delegates its six reminder methods to this instance, and the reminder editor is
            // handed only this one. Both are stateless adapters over the same service, so a second
            // copy of either would cost nothing but a second thing to keep in step.
            assertThat(koin.get<CollectionReminderGateway>()).isSameAs(koin.get<CollectionReminderGateway>())
            assertThat(koin.get<CollectionReminderGateway>()).isNotSameAs(koin.get<CollectionsGateway>())
        }

        test("the adapters cannot be reached past the seam they are registered under") { graph ->
            val koin = graph.koin
            // A guard rather than a fix: nothing asks for an adapter by name, and `koinInject` only
            // ever names the seam, so registering one under its own class would add a way in for
            // whoever comes next without changing a single call site.
            assertThat(koin.getOrNull<DomainCollectionsGateway>()).isNull()
            assertThat(koin.getOrNull<DomainReminderGateway>()).isNull()
            assertThat(koin.getOrNull<DomainHomeGateway>()).isNull()
            assertThat(koin.getOrNull<DomainTagsGateway>()).isNull()
        }

        test("the module registers its app services and UI seams") {
            assertThat(registeredTypes())
                .containsExactlyInAnyOrder(
                    SettingsStore::class,
                    ThemePreferenceStore::class,
                    LocaleStore::class,
                    TypographyStore::class,
                    LibrarySeedState::class,
                    CollectionReminderGateway::class,
                    CollectionsGateway::class,
                    HomeGateway::class,
                    TagGateway::class,
                    ReminderCatalogueSource::class,
                    HttpClient::class,
                    LocationDetector::class,
                    NotificationPreferencesRepository::class,
                    NotificationActionRouter::class,
                    StartupRegistration::class,
                    ReminderNotifier::class,
                    NotificationPublisher::class,
                    NotificationRuntime::class,
                    OverlayNotificationPresenter::class,
                    SystemNotificationPresenter::class,
                    CoroutineDispatcher::class,
                    CoroutineScope::class,
                )
        }

        test("the notification stack is a single, and it is in the container") { graph ->
            val koin = graph.koin
            // The overlay window is a composition, but everything that decides *whether* to show
            // something is not: the scheduler, the dispatcher and the presenter stack have to exist
            // before any window does, because the state the tray leaves the app in is "no window at
            // all" and a reminder still has to arrive there. So the container owns them, and this is
            // the assertion that they are built once — a second overlay presenter would be a second
            // stack, and a second runtime would be a second scheduler.
            assertThat(registers(ReminderNotifier::class)).isTrue()
            assertThat(koin.get<NotificationPublisher>())
                .isSameAs(koin.get<NotificationPublisher>())
            assertThat(koin.get<OverlayNotificationPresenter>())
                .isSameAs(koin.get<OverlayNotificationPresenter>())
            assertThat(koin.get<SystemNotificationPresenter>())
                .isSameAs(koin.get<SystemNotificationPresenter>())
            assertThat(koin.get<NotificationRuntime>()).isSameAs(koin.get<NotificationRuntime>())
            // And the notifier itself is reachable only as the system-presenter's collaborator, so
            // no screen can start talking to the OS channel without going through the dispatcher.
            assertThat(koin.get<ReminderNotifier>()).isSameAs(koin.get<ReminderNotifier>())
        }
    }
}

/**
 * Every type [appModule] registers a definition under, once each.
 *
 * `Module.mappings` holds one entry per index key rather than one per definition, so the bound
 * `CollectionHomeGateway` key points at the `CollectionsGateway` factory as well: seven entries for
 * six definitions. That duplication is the shape the previous test relies on, so it is collapsed
 * here rather than asserted twice.
 *
 * The module's own index is Koin's internal API, so the opt-in lives here rather than on the whole
 * file: two assertions need it, and every other assertion in the suite asks the container the way
 * the UI does, which is the way worth teaching.
 */
@OptIn(KoinInternalApi::class)
private fun registeredTypes(): List<KClass<*>> =
    appModule.mappings.values
        .map { it.beanDefinition.primaryType }
        .distinct()

/** Whether any definition in [appModule] is reachable by asking for [type]. */
@OptIn(KoinInternalApi::class)
private fun registers(type: KClass<*>): Boolean = appModule.mappings.values.any { it.beanDefinition.hasType(type) }

/**
 * The container, plus the one piece of scratch state the overrides need released afterwards.
 *
 * A class rather than a bare `Koin` because the settings store's scope and its temporary directory
 * both outlive the fixture block that creates them: `testFixture` and `closeWith` are sibling
 * lambdas, so a local declared in the first is not in scope in the second.
 */
private class StubbedGraph private constructor(
    val koin: Koin,
    private val settingsScope: CoroutineScope,
    private val settingsDirectory: Path,
) {
    fun close() {
        // The notification scope outlives the fixture block for the same reason the settings scope
        // does, and it holds the presenter's expiry jobs, so it is cancelled rather than leaked.
        runCatching {
            koin
                .get<CoroutineScope>(
                    org.koin.core.qualifier
                        .named("notificationScope"),
                ).cancel()
        }
        runCatching { koin.get<HttpClient>().close() }
        stopKoin()
        settingsScope.cancel()
        runCatching { Files.list(settingsDirectory).forEach { it.deleteIfExists() } }
        runCatching { settingsDirectory.deleteIfExists() }
    }

    companion object {
        /** Starts the real [appModule] over stubbed storage and a settings file of its own. */
        fun start(): StubbedGraph {
            val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val settingsDirectory = Files.createTempDirectory("azkar-app-module")
            val settingsFile = settingsDirectory.resolve("settings.preferences_pb")
            startKoin {
                modules(
                    storagePortModule,
                    appModule,
                    // The production definition builds over `~/.azkar/settings.preferences_pb`.
                    // Pointed at a scratch file instead, so a graph test never writes settings the
                    // developer would then open the real app against.
                    module { single { SettingsStore.create(settingsFile, settingsScope) } },
                )
            }
            return StubbedGraph(KoinPlatform.getKoin(), settingsScope, settingsDirectory)
        }
    }
}

/**
 * `dataModule` with the database taken out from under it.
 *
 * Every adapter's constructor takes nothing but the [TransactionRunner] port — no `Database`, no
 * Exposed type in any signature — and that is what makes this reachable from a module which cannot
 * see Exposed at all. [PassThroughTransactionRunner] runs the block and does nothing else, so no
 * schema and no connection are needed; the real runner's behaviour, and the wiring around it, are
 * what `DataModuleSuite` and the repository suites are for.
 *
 * Mirrors `dataModule` definition for definition, including the tag repository's `bind` and the
 * single shared transaction runner, so a definition added to one and not the other fails here.
 */
private val storagePortModule =
    module {
        single<TransactionRunner> { PassThroughTransactionRunner }

        single<CollectionRepository> { ExposedCollectionRepository(get(), get()) }
        single<RecitationRepository> { ExposedRecitationRepository(get()) }
        single<CollectionReminderRepository> { ExposedCollectionReminderRepository(get()) }
        single { ExposedTagRepository(get()) } bind TagRepository::class
        single<RemembranceRepository> { ExposedRemembranceRepository(get(), get()) }

        single { TagService(get(), get()) }
        single { RemembranceService(get(), get()) }
        single { CollectionService(get(), get()) }
        single { RecitationService(get(), get(), get()) }
        single { CollectionReminderService(get(), get(), get()) }

        // Neither of these touches the filesystem or a connection at construction, so they are
        // mirrored verbatim. What the bootstrapper *reads* — the gate — is a `LibrarySeedState`,
        // which is `appModule`'s to supply rather than this one's, and that asymmetry is the point.
        single { CoverStorage() }
        single { LibraryBootstrapper(get(), get(), get(), get(), get()) }
    }

/**
 * A [TransactionRunner] that does the one thing a resolution test needs, and nothing else.
 *
 * Deliberately not a spy, and deliberately never failing: the claim under test here is that a
 * definition resolves, and an adapter's transaction handling is claimed elsewhere, against a real
 * runner over a real schema.
 */
private object PassThroughTransactionRunner : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}
