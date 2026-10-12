package com.azkar.data.config

import com.azkar.data.repo.exposed.ExposedCollectionReminderRepository
import com.azkar.data.repo.exposed.ExposedCollectionRepository
import com.azkar.data.repo.exposed.ExposedRecitationRepository
import com.azkar.data.repo.exposed.ExposedRemembranceRepository
import com.azkar.data.repo.exposed.ExposedTagRepository
import com.azkar.data.storage.CoverStorage
import com.azkar.data.transaction.ExposedTransactionRunner
import com.azkar.domain.repo.CollectionReminderRepository
import com.azkar.domain.repo.CollectionRepository
import com.azkar.domain.repo.RecitationRepository
import com.azkar.domain.repo.RemembranceRepository
import com.azkar.domain.repo.TagRepository
import com.azkar.domain.service.CollectionReminderService
import com.azkar.domain.service.CollectionService
import com.azkar.domain.service.RecitationService
import com.azkar.domain.service.RemembranceService
import com.azkar.domain.service.TagService
import com.azkar.domain.service.TransactionRunner
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * The lower half of the dependency graph: the database, the repositories over it, and the domain
 * services that own the transaction boundaries.
 *
 * Everything here is a `single`. That is the whole point of moving the graph out of the per-call
 * `DomainServiceContext` this replaces: the adapters are stateless and the connection pool is
 * process-wide, so there was never anything to release per call. What the container buys is that
 * the graph is assembled once, in one place, instead of at twenty-five call sites that each had to
 * remember the same construction order — and that the ports the UI actually consumes are the
 * contract, not the Exposed implementation.
 *
 * [DatabaseManager] itself is deliberately absent. It is the owner of the process-wide pool and its
 * lifecycle is already governed by the window through `DatabaseWarmUp`, so registering the object
 * here would add a second, competing answer to "who closes this".
 *
 * The module knows nothing about `:app`: `:domain` stays dependency-free and `:data` stays unaware
 * of the UI, so the gateways that consume these services are the only place the two halves meet.
 */
val dataModule =
    module {
        single<Database> { DatabaseManager.database() }

        // One runner is shared deliberately: the repositories and the services both go through it,
        // and a repository call nested inside a service transaction has to join that transaction
        // rather than open a competing one. Two runners would be two independent decisions about
        // where the boundary sits.
        single<ExposedTransactionRunner> { ExposedTransactionRunner(get()) } bind TransactionRunner::class

        // Collections stand alone: they reference remembrances but never read through the tag
        // repository, so they get their own repository rather than a dependency on one they do not use.
        // It does take the tag repository — not to read tags with, but because writing a collection's
        // labels has to resolve and create the tag rows, which only the Exposed tag repository knows
        // how to do.
        single<CollectionRepository> { ExposedCollectionRepository(get(), get()) }

        // Recitation progress and reminders each need the collection repository as well — recording a
        // count has to be able to say the collection is not there rather than failing later on a
        // foreign key, and a schedule has to be able to say its collection is not there rather than
        // ticking on for a shelf the user has deleted. That dependency is resolved by the service
        // below, which is also the one that opens the transaction.
        single<RecitationRepository> { ExposedRecitationRepository(get()) }
        single<CollectionReminderRepository> { ExposedCollectionReminderRepository(get()) }

        // The tag repository is registered behind its port like every other repository, and behind its
        // concrete type as well: `ExposedRemembranceRepository` calls the concrete class directly to
        // persist tags alongside a remembrance, so it needs the Exposed class and not just the
        // interface. The `bind` above is what keeps it a single instance — the second definition is an
        // index entry, not a second construction.
        single<ExposedTagRepository> { ExposedTagRepository(get()) } bind TagRepository::class
        single<RemembranceRepository> { ExposedRemembranceRepository(get(), get()) }

        single { TagService(get(), get()) }
        single { RemembranceService(get(), get()) }
        single { CollectionService(get(), get()) }
        single { RecitationService(get(), get(), get()) }
        single { CollectionReminderService(get(), get(), get()) }

        // Cover images are loose files under `~/.azkar/covers`, not rows, so there is no repository and
        // no port for them: the directory is a fact about the installation rather than something the
        // domain has an opinion about, and the only thing the domain asks of it is that a cover is
        // copied in before its name is stored.
        single { CoverStorage() }

        // Resolved once, by the window's warm-up, before anything reads the library. It asks the
        // container for a [LibrarySeedState] — which `:app` supplies over its settings store, because
        // whether this installation has been seeded has to outlive its database — and everything else
        // it needs is registered above it.
        single { LibraryBootstrapper(get(), get(), get(), get(), get()) }
    }
