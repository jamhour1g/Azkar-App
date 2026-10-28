package com.azkar.ui.preview

/** Excludes previews that launch native desktop windows from the generated Roborazzi preview scan. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RoborazziDesktopPreviewExclude
