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
import com.azkar.ui.shell.ThemePreferenceStore
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
 * [com.azkar.ui.shell.ReminderNotifier] is deliberately absent. `SystemTrayReminderNotifier` needs
 * `AzkarNavigator.showCollection`, and a navigator only exists once a composition does — so the
 * notifier's lifetime is the window's, not the process's, and asking the container for it at startup
 * would have to invent the dependency rather than resolve it.
 *
 * Nothing here has to be closed when the window shuts. Koin 4 does not release `AutoCloseable`
 * values on its own, and nothing here is one that matters: the settings store's work is committed to
 * disk by the time a click returns, and the database pool is owned by `DatabaseWarmUp`, which is
 * where it was before the container existed.
 */
val appModule =
    module {
        single { SettingsStore.create() }

        single { ThemePreferenceStore(get()) }

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

        // Under its own seam rather than added to `CollectionsGateway`, because a tag outlives the
        // screens that attach one to a collection: deleting or renaming one is a job the library has
        // and no collection screen should be able to do by accident. One dependency, so no bag.
        single<TagGateway> { DomainTagsGateway(get()) }
    }
