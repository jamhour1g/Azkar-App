package com.azkar.ui.shell

/**
 * How loudly a toast is announced.
 *
 * Ported from `NotificationPriority`, and moved here out of `ui/library/model` when the reminder
 * planner it originally existed for was ripped out. What is left of it belongs to the shell rather
 * than to a screen: [ShowToast] and [ToastHost] are the only things that read it, and leaving the
 * enum in the library package would have meant the shell importing a type from a package with
 * nothing left in it to name it after.
 *
 * The `orderWeight` and `randomWeight` fields it used to carry went with the scheduler — they existed
 * to pick which of a queue of scheduled reminders fired next, and there is no queue any more. The
 * three cases stay because they are three visible treatments: [ToastHost] paints a different
 * gradient and border for each.
 */
enum class NotificationPriority {
    HIGH,
    MEDIUM,
    LOW,
    ;

    companion object {
        /** Reads a priority out of reconstructed state, treating "no answer" as [MEDIUM]. */
        fun fallback(value: NotificationPriority?): NotificationPriority = value ?: MEDIUM
    }
}
