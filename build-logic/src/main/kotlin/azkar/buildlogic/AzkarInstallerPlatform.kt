package azkar.buildlogic

data class AzkarInstallerPlatform(
    val os: String,
    val arch: String,
) {
    val id: String get() = "$os-$arch"

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
