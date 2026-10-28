package com.azkar

import java.io.File

internal fun screenshotCapturePath(name: String): String {
    val outputDirectory = requireNotNull(System.getProperty("azkar.screenshotOutputDir"))
    val image = File(outputDirectory, "$name.webp")
    image.parentFile.mkdirs()
    return image.absolutePath
}
