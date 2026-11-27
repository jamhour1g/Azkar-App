import json
import os
import subprocess
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

SCRIPTS = Path(__file__).resolve().parents[1] / "ci"
HEAD = "a" * 40
BASE = "b" * 40
OWNER = "jamhour1g"
REPOSITORY = f"{OWNER}/Azkar-App"


class AuthorizationTests(unittest.TestCase):
    def setUp(self):
        temp = TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        gh = self.root / "gh"
        gh.write_text("""#!/usr/bin/env python3
import os
import sys
from pathlib import Path

args = sys.argv[1:]
if any("/statuses/" in arg for arg in args):
    with Path(os.environ["MOCK_STATUSES"]).open("a") as log:
        log.write(" ".join(args) + "\\n")
elif any(arg.endswith("/files?per_page=100") for arg in args):
    print(os.environ["MOCK_FILES"])
elif any("/pulls/" in arg for arg in args):
    print(os.environ["MOCK_PR"])
else:
    print("dev")
""")
        gh.chmod(0o755)

    def run_script(self, name, *, author="contributor", target="dev", files=None, pr_overrides=None, **overrides):
        files = [{"filename": "README.md"}] if files is None else files
        pr = {
            "state": "open", "changed_files": len(files), "user": {"login": author},
            "head": {"sha": HEAD, "ref": "feature", "repo": {"full_name": REPOSITORY}},
            "base": {"ref": target, "sha": BASE, "repo": {"full_name": REPOSITORY}},
        } | (pr_overrides or {})
        output = self.root / "outputs"
        statuses = self.root / "statuses"
        for path in (output, statuses):
            path.write_text("")
        env = {
            "PATH": f"{self.root}:{os.environ['PATH']}", "MOCK_PR": json.dumps(pr),
            "MOCK_FILES": json.dumps([files]), "MOCK_STATUSES": str(statuses),
            "REPOSITORY": REPOSITORY, "OWNER": OWNER, "ACTOR": OWNER,
            "EVENT": "workflow_dispatch", "DISPATCH_REF": "refs/heads/dev",
            "PROFILE_GRADLE": "false", "FULL_INSTALLERS": "false", "SKIP_CHECKS": "false",
            "PR_NUMBER": "123", "REVIEWED_SHA": HEAD, "BASE_SHA": BASE, "BASE_BRANCH": "dev",
            "GITHUB_SHA": HEAD, "GITHUB_OUTPUT": str(output), "GITHUB_STEP_SUMMARY": str(self.root / "summary"),
            "GITHUB_SERVER_URL": "https://github.com", "GITHUB_RUN_ID": "1",
            "INSTALLERS_REQUIRED": "false", "SITE_ONLY": "false",
            "LINUX_RESULT": "success", "SITE_RESULT": "success",
            "CROSS_RESULT": "success", "INSTALLERS_RESULT": "success",
        }
        result = subprocess.run(["bash", SCRIPTS / f"{name}.sh"], env=os.environ | env | overrides,
                                capture_output=True, text=True)
        return result, output.read_text(), statuses.read_text()

    def test_approval_and_owner_rehearsal(self):
        for args in ({}, {"EVENT": "pull_request_target", "author": OWNER},
                     {"DISPATCH_REF": "refs/heads/feature", "author": OWNER}):
            result, _, statuses = self.run_script("authorize-pr", **args)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("state=pending", statuses)
        for args in (
            {"EVENT": "pull_request_target"}, {"ACTOR": "contributor"}, {"REVIEWED_SHA": "c" * 40},
            {"PR_NUMBER": "../statuses"}, {"DISPATCH_REF": "refs/heads/feature"},
            {"pr_overrides": {"state": "closed"}}, {"target": "other"},
        ):
            result, output, statuses = self.run_script("authorize-pr", **args)
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual((output, statuses), ("", ""))

    def test_docs_skip_requires_all_paths_and_owner_dispatch(self):
        docs = [{"filename": "docs/new.md", "previous_filename": "docs/old.md"}]
        result, output, _ = self.run_script("authorize-pr", files=docs, SKIP_CHECKS="true")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("skip_checks=true", output)
        for args in (
            {"files": [{"filename": "docs/new.md", "previous_filename": "app/main.kt"}]},
            {"pr_overrides": {"changed_files": 2}}, {"pr_overrides": {"changed_files": 3001}},
            {"target": "production"}, {"FULL_INSTALLERS": "true"},
        ):
            result, _, _ = self.run_script("authorize-pr", SKIP_CHECKS="true", **args)
            self.assertNotEqual(result.returncode, 0)

    def test_site_only_needs_complete_diff_and_no_installer_or_profile_request(self):
        site = [{"filename": "site/src/page.ts"}]
        for args, expected in (
            ({}, "true"), ({"PROFILE_GRADLE": "true"}, "false"), ({"FULL_INSTALLERS": "true"}, "false"),
            ({"target": "production"}, "false"), ({"pr_overrides": {"changed_files": 2}}, "false"),
        ):
            result, output, _ = self.run_script("authorize-pr", files=site, **args)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn(f"site_only={expected}", output)
        for current, previous in (("site/src/app.ts", "app/main.kt"), ("app/main.kt", "site/src/app.ts")):
            result, output, _ = self.run_script("authorize-pr",
                                                files=[{"filename": current, "previous_filename": previous}])
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("site_only=false", output)

    def test_reporting_retains_required_checks(self):
        for args, expected in (
            ({}, "success"), ({"SKIP_CHECKS": "true", "SITE_RESULT": "skipped", "LINUX_RESULT": "skipped"}, "success"),
            ({"SITE_ONLY": "true", "LINUX_RESULT": "skipped"}, "success"),
            ({"SITE_RESULT": "failure"}, "failure"), ({"SITE_RESULT": "cancelled"}, "failure"),
            ({"LINUX_RESULT": "skipped"}, "failure"), ({"BASE_SHA": "c" * 40}, "failure"),
            ({"REVIEWED_SHA": "c" * 40}, "failure"), ({"BASE_BRANCH": "production"}, "failure"),
            ({"pr_overrides": {"state": "closed"}}, "failure"),
        ):
            result, _, statuses = self.run_script("report-status", **args)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn(f"state={expected} -f context=Azkar CI / Linux", statuses)
        for check in ("CROSS_RESULT", "INSTALLERS_RESULT"):
            result, _, statuses = self.run_script("report-status", INSTALLERS_REQUIRED="true", **{check: "failure"})
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("state=failure -f context=Azkar CI / Installers", statuses)
