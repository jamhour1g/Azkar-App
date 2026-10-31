package com.azkar.ui.notifications

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Default dispatcher binding for audio file and JavaFX initialization work. */
internal object AudioIoDispatcher {
    @Suppress("InjectDispatcher")
    val default: CoroutineDispatcher = Dispatchers.IO
}
