package com.azkar.updates

import de.infix.testBalloon.framework.core.testSuite
import org.assertj.core.api.Assertions.assertThat

val VersionComparisonSuite by testSuite("Update version ordering") {
    test("numeric prerelease identifiers compare numerically") {
        assertThat(compareVersions("0.1.2-beta.10.1", "0.1.2-beta.9.8")).isPositive()
    }

    test("a stable release is newer than its prerelease") {
        assertThat(compareVersions("0.1.2", "0.1.2-beta.99.1")).isPositive()
    }

    test("switching to stable detects a lower core version") {
        assertThat(compareVersions("0.1.1", "0.1.2-beta.3.1")).isNegative()
    }

    test("Debian prerelease package versions use tilde ordering") {
        assertThat(debianPackageVersion("0.1.2-beta.84.1")).isEqualTo("0.1.2~beta.84.1")
    }
}
