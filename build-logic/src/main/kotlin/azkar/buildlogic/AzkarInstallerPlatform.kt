package azkar.buildlogic

data class AzkarInstallerPlatform(
    val os: String,
    val arch: String,
) {
    val id: String get() = "$os-$arch"

    fun installerName(
        version: String,
        extension: String,
    ): String {
        val suffix = if (os == "windows" && extension == "exe") "-nsis" else ""
        return "azkar-$version-$id$suffix.$extension"
    }

    companion object {
        fun current(): AzkarInstallerPlatform {
            val osName = System.getProperty("os.name")
            val os =
                when {
                    osName.startsWith("Windows", ignoreCase = true) -> "windows"
                    osName.startsWith("Mac", ignoreCase = true) -> "macos"
                    else -> "linux"
                }
            val archName = System.getProperty("os.arch")
            val arch =
                when (archName) {
                    "amd64", "x86_64" -> "x64"
                    "aarch64", "arm64" -> "arm64"
                    else -> archName
                }
            return AzkarInstallerPlatform(os, arch)
        }
    }
}

fun macPackageVersion(version: String): String {
    val components = version.substringBefore('-').split('.').map(String::toBigInteger)
    require(components.size == 3 && components.all { it.signum() >= 0 }) { "Expected a numeric three-part version" }
    // jpackage requires a positive macOS major; offset every major to keep native versions ordered.
    return listOf(components[0] + java.math.BigInteger.ONE, components[1], components[2]).joinToString(".")
}
