package com.azkar.ui.library

import com.azkar.domain.model.CollectionKind
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionsKindCustom
import com.azkar.generated.resources.collectionsKindOfficial
import com.azkar.generated.resources.collectionsScopeAll
import com.azkar.generated.resources.collectionsScopeCustom
import com.azkar.generated.resources.collectionsScopeFavorite
import com.azkar.generated.resources.collectionsScopeInProgress
import com.azkar.generated.resources.collectionsScopeInReminders
import com.azkar.generated.resources.collectionsScopeOfficial
import com.azkar.generated.resources.collectionsScopePinned
import com.azkar.generated.resources.collectionsSortCustom
import com.azkar.generated.resources.collectionsSortItems
import com.azkar.generated.resources.collectionsSortName
import com.azkar.generated.resources.collectionsSortProgress
import com.azkar.generated.resources.collectionsSortRecent
import com.azkar.ui.library.model.CollectionScope
import com.azkar.ui.library.model.CollectionSort
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

// Maps the library enums onto the generated string resources.
//
// The enums stay free of resource imports so `ui/library/model` keeps being a plain model package
// that a test can build without a resource environment. This file is the single place where an
// enum and a resource meet, so a renamed resource breaks the build in exactly one place.
//
// Each enum gets a `labelResource` property for callers rendering inside a composition, plus a
// `label()` suspending function for the state code that cannot enter one.

/** The chip label for this scope. */
val CollectionScope.labelResource: StringResource
    get() =
        when (this) {
            CollectionScope.ALL -> Res.string.collectionsScopeAll
            CollectionScope.OFFICIAL -> Res.string.collectionsScopeOfficial
            CollectionScope.CUSTOM -> Res.string.collectionsScopeCustom
            CollectionScope.PINNED -> Res.string.collectionsScopePinned
            CollectionScope.FAVORITE -> Res.string.collectionsScopeFavorite
            CollectionScope.IN_PROGRESS -> Res.string.collectionsScopeInProgress
            CollectionScope.IN_REMINDERS -> Res.string.collectionsScopeInReminders
        }

/** Resolves [labelResource] for callers that build the text outside a composition. */
suspend fun CollectionScope.label(): String = getString(labelResource)

/** The sort option label. */
val CollectionSort.labelResource: StringResource
    get() =
        when (this) {
            CollectionSort.RECENTLY_UPDATED -> Res.string.collectionsSortRecent
            CollectionSort.NAME -> Res.string.collectionsSortName
            CollectionSort.MOST_ITEMS -> Res.string.collectionsSortItems
            CollectionSort.PROGRESS -> Res.string.collectionsSortProgress
            CollectionSort.CUSTOM -> Res.string.collectionsSortCustom
        }

/** Resolves [labelResource] for callers that build the text outside a composition. */
suspend fun CollectionSort.label(): String = getString(labelResource)

/**
 * The badge label distinguishing a shipped collection from a user's own.
 *
 * Deliberately not the scope chips' wording. "Included" answers a filter question ("what am I
 * looking at?"); this one has to answer an ownership question ("whose is this?"), and the two read as
 * unrelated labels if they are the same sentence.
 */
val CollectionKind.labelResource: StringResource
    get() =
        when (this) {
            CollectionKind.OFFICIAL -> Res.string.collectionsKindOfficial
            CollectionKind.CUSTOM -> Res.string.collectionsKindCustom
        }
