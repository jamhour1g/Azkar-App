"""Require installed-release evidence before publishing a stable release."""

import re
import subprocess
from pathlib import Path

from release_support import cli, require

CHECKS = ("windowsX64", "macosX64", "macosArm64", "deb", "rpm", "appImageArch")
DISTRIBUTION_PATHS = (
    ".github/workflows", ".github/scripts", "scripts", "build-logic", "app/build.gradle.kts",
    "app/packaging", "build.gradle.kts", "settings.gradle.kts", "gradle", "assets",
    "app/src/main/kotlin/com/azkar/updates", "app/src/main/kotlin/com/azkar/Main.kt",
    "app/src/main/kotlin/com/azkar/PackagingSmokeTest.kt",
    "app/src/main/kotlin/com/azkar/ui/notifications/StartupRegistration.kt",
    "app/src/main/kotlin/com/azkar/ui/shell/ReminderTray.kt",
)


def verify_evidence(root):
    record = (root / "docs/release-validation.properties").read_text(encoding="utf-8")
    properties = dict(re.findall(r"^[ \t]*([^#!\s][^:=\s]*)(?:[ \t]*[:=][ \t]*|[ \t]+)(.*)$", record, re.MULTILINE))
    commit = properties.get("sourceCommit", "")
    version = properties.get("artifactVersion", "")
    require(re.fullmatch(r"[0-9a-f]{40}", commit), "Record the full manually tested source commit before Stable publication")
    require(re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+(-beta\.[0-9]+(\.[0-9]+)*)?", version),
            "Record the tested installer version")
    for key in CHECKS:
        require(properties.get(key) == "passed", f"Missing installed-release validation: {key}")
    require(len(properties.get("evidence", "")) >= 20, "Link or describe the recorded install/update evidence")
    ancestry = subprocess.run(["git", "merge-base", "--is-ancestor", commit, "HEAD"], cwd=root)
    require(ancestry.returncode == 0, "Tested commit is not an ancestor of this release")
    diff = subprocess.run(["git", "diff", "--quiet", commit, "HEAD", "--", *DISTRIBUTION_PATHS], cwd=root)
    require(diff.returncode == 0, "Packaging/updater sources changed after installed-release validation; repeat the checklist")
    print(f"Installed-release evidence accepted for {commit} ({version})")


if __name__ == "__main__":
    cli(lambda: verify_evidence(Path.cwd()))
