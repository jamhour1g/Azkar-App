package com.azkar.ui.library

import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionsNoticeCollectionStarred
import com.azkar.generated.resources.collectionsNoticeCollectionUnstarred
import com.azkar.generated.resources.collectionsNoticeCreated
import com.azkar.generated.resources.collectionsNoticeDeleted
import com.azkar.generated.resources.collectionsNoticeDuplicated
import com.azkar.generated.resources.collectionsNoticeEdited
import com.azkar.generated.resources.collectionsNoticeFailed
import com.azkar.generated.resources.collectionsNoticeFavoriteAdded
import com.azkar.generated.resources.collectionsNoticeFavoriteRemoved
import com.azkar.generated.resources.collectionsNoticeMembersAdded
import com.azkar.generated.resources.collectionsNoticeMembersRemoved
import com.azkar.generated.resources.collectionsNoticeOffHome
import com.azkar.generated.resources.collectionsNoticeOnHome
import com.azkar.generated.resources.collectionsNoticePinned
import com.azkar.generated.resources.collectionsNoticeProgressReset
import com.azkar.generated.resources.collectionsNoticeRemembranceCreated
import com.azkar.generated.resources.collectionsNoticeRemembranceDeleted
import com.azkar.generated.resources.collectionsNoticeReminderRemoved
import com.azkar.generated.resources.collectionsNoticeReminderSaved
import com.azkar.generated.resources.collectionsNoticeTagDeleted
import com.azkar.generated.resources.collectionsNoticeTagRenamed
import com.azkar.generated.resources.collectionsNoticeTagRestored
import com.azkar.generated.resources.collectionsNoticeUnpinned
import com.azkar.ui.shell.NotificationPriority
import org.jetbrains.compose.resources.StringResource

/**
 * What a shelf mutation turns into on screen.
 *
 * Kept out of [CollectionsState] deliberately: the state is created inside a composition that is
 * rebuilt on every locale switch, while a mutation's outcome outlives the click that started it. A
 * sentence captured at write time would survive into the wrong language, so the outcome travels as a
 * key and is resolved after the switch.
 *
 * [FAILED] reuses the plain medium tone rather than anything alarming. It is the state for every
 * failure in the shelf — a duplicate name, a collection deleted in another window — and none of them
 * are errors the user can act on beyond trying again.
 */
val CollectionNoticeKey.messageResource: StringResource
    get() =
        when (this) {
            CollectionNoticeKey.CREATED -> Res.string.collectionsNoticeCreated
            CollectionNoticeKey.EDITED -> Res.string.collectionsNoticeEdited
            CollectionNoticeKey.DELETED -> Res.string.collectionsNoticeDeleted
            CollectionNoticeKey.DUPLICATED -> Res.string.collectionsNoticeDuplicated
            CollectionNoticeKey.PINNED -> Res.string.collectionsNoticePinned
            CollectionNoticeKey.UNPINNED -> Res.string.collectionsNoticeUnpinned
            CollectionNoticeKey.MEMBERS_ADDED -> Res.string.collectionsNoticeMembersAdded
            CollectionNoticeKey.MEMBERS_REMOVED -> Res.string.collectionsNoticeMembersRemoved
            CollectionNoticeKey.REMEMBRANCE_CREATED -> Res.string.collectionsNoticeRemembranceCreated
            CollectionNoticeKey.REMEMBRANCE_DELETED -> Res.string.collectionsNoticeRemembranceDeleted
            CollectionNoticeKey.PROGRESS_RESET -> Res.string.collectionsNoticeProgressReset
            CollectionNoticeKey.ON_HOME -> Res.string.collectionsNoticeOnHome
            CollectionNoticeKey.OFF_HOME -> Res.string.collectionsNoticeOffHome
            CollectionNoticeKey.REMINDER_SAVED -> Res.string.collectionsNoticeReminderSaved
            CollectionNoticeKey.REMINDER_REMOVED -> Res.string.collectionsNoticeReminderRemoved
            CollectionNoticeKey.FAVORITE_ADDED -> Res.string.collectionsNoticeFavoriteAdded
            CollectionNoticeKey.FAVORITE_REMOVED -> Res.string.collectionsNoticeFavoriteRemoved
            CollectionNoticeKey.FAVORITED -> Res.string.collectionsNoticeCollectionStarred
            CollectionNoticeKey.UNFAVORITED -> Res.string.collectionsNoticeCollectionUnstarred
            CollectionNoticeKey.TAG_RENAMED -> Res.string.collectionsNoticeTagRenamed
            CollectionNoticeKey.TAG_DELETED -> Res.string.collectionsNoticeTagDeleted
            CollectionNoticeKey.TAG_RESTORED -> Res.string.collectionsNoticeTagRestored
            CollectionNoticeKey.FAILED -> Res.string.collectionsNoticeFailed
        }

/** How loudly the shell should show it. Only a failure outranks the routine confirmations. */
val CollectionNoticeKey.priority: NotificationPriority
    get() =
        when (this) {
            CollectionNoticeKey.FAILED -> NotificationPriority.HIGH
            CollectionNoticeKey.DELETED,
            CollectionNoticeKey.FAVORITE_REMOVED,
            CollectionNoticeKey.TAG_DELETED,
            -> NotificationPriority.MEDIUM
            else -> NotificationPriority.LOW
        }
