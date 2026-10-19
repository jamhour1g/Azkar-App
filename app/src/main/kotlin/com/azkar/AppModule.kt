package com.azkar

import com.azkar.domain.repo.LibrarySeedState
import com.azkar.i18n.LocaleStore
import com.azkar.settings.LibrarySeedStore
import com.azkar.settings.SettingsStore
import com.azkar.ui.home.DomainHomeGateway
import com.azkar.ui.home.HomeGateway
import com.azkar.ui.library.data.CollectionHomeGateway
import com.azkar.ui.library.data.CollectionReminderGateway
import com.azkar.ui.library.data.CollectionsGateway
import com.azkar.ui.library.data.CollectionsGatewayDependencies
import com.azkar.ui.library.data.DomainCollectionsGateway
import com.azkar.ui.library.data.DomainReminderGateway
import com.azkar.ui.library.data.DomainTagsGateway
import com.azkar.ui.library.data.TagGateway
import com.azkar.ui.library.model.CollectionsSnapshot
import com.azkar.ui.notifications.GeocodingClient
import com.azkar.ui.notifications.LocationDetector
import com.azkar.ui.notifications.NotificationActionRouter
import com.azkar.ui.notifications.NotificationPreferencesRepository
import com.azkar.ui.notifications.NotificationRuntime
import com.azkar.ui.notifications.ReminderCatalogueSource
import com.azkar.ui.notifications.StartupRegistration
import com.azkar.ui.notifications.presentation.NotificationDispatcher
import com.azkar.ui.notifications.presentation.NotificationPublisher
import com.azkar.ui.notifications.presentation.OverlayNotificationPresenter
import com.azkar.ui.shell.ThemePreferenceStore
import com.azkar.ui.shell.TypographyStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * The upper half of the dependency graph: the settings file, and the gateways the composables reach
 * for instead of the database.
 *
 * Composed with [com.azkar.data.config.dataModule] at the entry point — `main` in [Main.kt], and the
 * screenshot harness in its own `main` — so there is one list of modules rather than one per entry
 * point, and both halves come from the same file.
 *
 * Everything here is a `single`. The settings store owns a `DataStore` and the scope that keeps it
 * alive, the theme, locale and seed stores are the three places that decide how a stored preference
 * becomes bytes, and a gateway is a stateless adapter over services that are themselves already single
 * instances from `dataModule`, so a second copy of any of them would buy nothing but a second thing
 * to keep in step.
 *
 * Each gateway is registered under the seam the UI depends on rather than under its own class, so a
 * screen cannot reach past `CollectionsGateway` by accident. The `bind` is an index entry on the same
 * factory, not an extra construction: `DomainCollectionsGateway` is asked for as a `CollectionsGateway`
 * by the library shelf and as a `CollectionHomeGateway` by the home dashboard, and both have to be
 * the same object, or a mutation made on one screen would not be visible from the other.
 *
 * The notification stack is built here too, and that is the point of it being here rather than in
 * [Main]. The scheduler, the dispatcher and the overlay's state machine are all window-independent —
 * a reminder has to fire whether or not anything is on screen — so none of them belongs to a
 * composition, and constructing them at the root would mean every one of them had to be re-created
 * by whatever composition happened to start first. The only genuinely window-owned collaborators,
 * the tray icon and the overlay's borderless frame, stay in `Main` and reach this graph for
 * everything they do not own.
 *
 * Koin 4 does not release `AutoCloseable` values on its own. [Main] cancels
 * `notificationScope` and closes the shared Ktor client during explicit process shutdown; the
 * database pool is owned by `DatabaseWarmUp`.
 */
val appModule =
    module {
        single { SettingsStore.create() }

        // A single configured Ktor client is shared by the best-effort location lookup. Keeping the
        // engine in the graph gives the app one owner to close on explicit Quit and makes the HTTP
        // boundary injectable in tests.
        single {
            HttpClient(CIO) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                install(HttpTimeout) {
                    connectTimeoutMillis = 5_000
                    requestTimeoutMillis = 8_000
                    socketTimeoutMillis = 8_000
                }
                expectSuccess = false
            }
        }
        single { LocationDetector(get()) }
        // Place search for the location picker. It shares the one configured client above, so it
        // inherits its timeouts and its single close on Quit.
        single { GeocodingClient(get()) }
        single { NotificationPreferencesRepository(get()) }
        single { NotificationActionRouter() }
        single { StartupRegistration() }
        single<CoroutineDispatcher>(named("notificationDispatcher")) { notificationDispatcher() }

        // The notification stack. The scope is owned here rather than in `Main` because both the
        // scheduler's tick loop and the overlay's expiry loops have to survive the window being
        // hidden, and a scope created inside the application composition would not.
        single<CoroutineScope>(named("notificationScope")) {
            CoroutineScope(SupervisorJob() + notificationDispatcher())
        }
        single {
            OverlayNotificationPresenter(
                scope = get(named("notificationScope")),
                settings = get<NotificationPreferencesRepository>().presentation,
            )
        }
        single<NotificationPublisher> {
            NotificationDispatcher(
                // Named rather than inferred: the parameter is the `NotificationPresenter` seam, and
                // a parameterless `get()` would ask the container for the interface nothing is
                // registered under.
                overlay = get<OverlayNotificationPresenter>(),
            )
        }
        single { NotificationRuntime(get(), get(), get(), get()) }

        single { ThemePreferenceStore(get()) }

        single { TypographyStore(get()) }

        single { LocaleStore(get()) }

        // The one half of the dataset bootstrap that `:data` cannot supply for itself: the answer to
        // "has the shipped library been written yet?" has to outlive the database it describes, so it
        // lives in the settings file rather than in a row. Bound to the port so the bootstrapper cannot
        // reach around the seam and read the keys itself.
        single<LibrarySeedState> { LibrarySeedStore(get()) }

        single<CollectionReminderGateway> { DomainReminderGateway(get(), get()) }

        single<CollectionsGateway> {
            DomainCollectionsGateway(
                CollectionsGatewayDependencies(get(), get(), get(), get(), get(), get(), get()),
            )
        } bind CollectionHomeGateway::class

        single<HomeGateway> { DomainHomeGateway(get(), get()) }

        // The reminders screen's own seam over the same gateway. It reads one snapshot and writes
        // nothing, and giving it the whole library interface for that single call would make it the
        // one screen that can reach past `CollectionsGateway` by accident — the thing the `bind`
        // above exists to prevent.
        single<ReminderCatalogueSource> {
            val gateway = get<CollectionsGateway>()
            object : ReminderCatalogueSource {
                override suspend fun snapshot(): CollectionsSnapshot = gateway.fetchSnapshot()
            }
        }

        // Under its own seam rather than added to `CollectionsGateway`, because a tag outlives the
        // screens that attach one to a collection: deleting or renaming one is a job the library has
        // and no collection screen should be able to do by accident. One dependency, so no bag.
        single<TagGateway> { DomainTagsGateway(get()) }
    }

@Suppress("InjectDispatcher")
private fun notificationDispatcher(): CoroutineDispatcher = Dispatchers.IO
