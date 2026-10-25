package azkar.buildlogic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InstallerNamingTest {
    @Test
    fun nsisSuffixMatchesNucleus() {
        assertEquals(
            "azkar-0.1.2-beta.3-windows-x64-nsis.exe",
            AzkarInstallerPlatform("windows", "x64").installerName("0.1.2-beta.3", "exe"),
        )
        assertEquals(
            "azkar-0.1.2-linux-x64.deb",
            AzkarInstallerPlatform("linux", "x64").installerName("0.1.2", "deb"),
        )
    }

    @Test
    fun macVersionRemainsPositiveAndOrderedAcrossOnePointZero() {
        assertEquals("1.0.0", macPackageVersion("0.0.0-beta.9.1"))
        assertEquals("1.1.2", macPackageVersion("0.1.2-beta.3"))
        assertEquals("2.0.0", macPackageVersion("1.0.0"))
        assertFailsWith<IllegalArgumentException> { macPackageVersion("1.2") }
    }
}
