import hashlib
import io
import json
import os
import subprocess
import sys
import tarfile
import unittest
from base64 import b64encode
from datetime import datetime, timezone
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch
from urllib.error import HTTPError

SCRIPTS = Path(__file__).resolve().parents[1]
RELEASE = SCRIPTS / "release"
sys.path.insert(0, str(RELEASE))

import check_release_validation as evidence
import merge_nucleus_release as merger
import metadata
import publish_linux_repositories as repositories
import recover_finalize


class Fixture(unittest.TestCase):
    def setUp(self):
        temp = TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)

    def shell(self, name, *args, env=None):
        return subprocess.run(
            ["bash", RELEASE / name, *args], cwd=self.root,
            env=os.environ | (env or {}), text=True, capture_output=True,
        )

    def git(self, *args):
        return subprocess.check_output(["git", *args], cwd=self.root, text=True).strip()


class MetadataTests(Fixture):
    @staticmethod
    def release(tag, prerelease=True, draft=False, published="2026-10-28T00:00:00Z"):
        return dict(tag_name=tag, prerelease=prerelease, draft=draft, published_at=published)

    def test_release_order_and_reserved_numbers(self):
        entries = [
            self.release("v1.2.10", False),
            self.release("v1.2.9", False),
            self.release("v1.2.11-beta.13", draft=True),
            self.release("v1.2.11-beta.10.1", published="2026-10-28T01:00:00Z"),
            self.release("v1.2.11-beta.12"),
        ]
        self.assertEqual(metadata.latest_stable(entries)["tag_name"], "v1.2.10")
        self.assertEqual(metadata.latest_dev(entries)["tag_name"], "v1.2.11-beta.10.1")
        self.assertEqual(metadata.next_dev_tag(entries, ["v1.2.11-beta.14.2"]), "v1.2.11-beta.15")
        self.assertEqual(metadata.next_dev_tag([], []), "v0.1.2-beta.1")

    def test_pagination_ignores_urls_and_keeps_drafts(self):
        first = self.release("v1.2.4-beta.3", draft=True)
        second = self.release("v1.2.4-beta.2")
        with patch.object(metadata, "request", side_effect=[
            ([first], '<https://example.invalid>; rel="next"'), ([second], ""),
        ]) as request:
            self.assertEqual(metadata.releases(), [first, second])
            self.assertEqual(request.call_args.args, ("releases?per_page=100&page=2",))

    def test_malformed_release_fails(self):
        for entry in ({}, self.release("v1", draft="false"), self.release("v1", published="unknown")):
            with self.subTest(entry=entry), patch.object(metadata, "request", return_value=([entry], "")):
                with self.assertRaises(ValueError):
                    metadata.releases()

    def test_api_does_not_follow_redirects_or_require_credentials(self):
        handler = metadata.NoRedirect()
        redirect = handler.redirect_request(None, None, 302, "Found", {}, "https://example.invalid")
        self.assertIsNone(redirect)
        for token in ("", "test-token"):
            with patch.dict(os.environ, {"GITHUB_REPOSITORY": "example/azkar", "GH_TOKEN": token}, clear=True):
                with patch.object(metadata, "build_opener") as build:
                    opener = build.return_value
                    response = opener.open.return_value.__enter__.return_value
                    response.read.return_value = b'{"status": "ahead"}'
                    response.headers = {}
                    self.assertEqual(metadata.request("compare/example")[0], {"status": "ahead"})
                    request = opener.open.call_args.args[0]
                    self.assertEqual(request.full_url, "https://api.github.com/repos/example/azkar/compare/example")
                    self.assertEqual(request.get_header("Authorization"), f"Bearer {token}" if token else None)
                    error = HTTPError(request.full_url, 403, "Forbidden", {}, io.BytesIO())
                    self.addCleanup(error.close)
                    opener.open.side_effect = error
                    with self.assertRaises(HTTPError):
                        metadata.request("compare/example")

    def test_batch_interval_status_and_changed_paths(self):
        previous = self.release("v1.2.4-beta.2")
        now = datetime(2026, 10, 28, 6, tzinfo=timezone.utc)
        self.assertTrue(metadata.batch_decision("schedule", None, now)[0])
        self.assertFalse(metadata.batch_decision("schedule", previous, now.replace(hour=5))[0])
        with patch.dict(os.environ, {"GITHUB_SHA": "a" * 40}):
            with patch.object(metadata, "request", return_value=({"status": "ahead"}, "")):
                with patch.object(metadata.subprocess, "check_output", return_value=b"") as git:
                    self.assertFalse(metadata.batch_decision("schedule", previous, now)[0])
                    self.assertIn("site/public/install.sh", git.call_args.args[0])
                    self.assertNotIn(".github", git.call_args.args[0])
                    git.return_value = b"app/src/main/new.kt\n"
                    self.assertTrue(metadata.batch_decision("workflow_dispatch", previous, now)[0])
            for status in ("identical", "behind", "diverged"):
                with patch.object(metadata, "request", return_value=({"status": status}, "")):
                    self.assertFalse(metadata.batch_decision("workflow_dispatch", previous, now)[0])
            with patch.object(metadata, "request", return_value=({"status": "unknown"}, "")):
                with self.assertRaises(ValueError):
                    metadata.batch_decision("workflow_dispatch", previous, now)

    def test_stable_and_local_skip_network(self):
        output = self.root / "output"
        summary = self.root / "summary"
        env = {"GITHUB_OUTPUT": str(output), "GITHUB_STEP_SUMMARY": str(summary)}
        with patch.dict(os.environ, env | {"ACT": "true"}, clear=True):
            metadata.select_batch()
            metadata.resolve_version()
            self.assertIn("app_version=0.0.0-beta.1", output.read_text())
        with patch.dict(os.environ, env | {"GITHUB_REF": "refs/tags/v1.2.3", "GITHUB_REF_NAME": "v1.2.3"}, clear=True):
            metadata.select_batch()
            metadata.resolve_version()
            self.assertTrue(output.read_text().endswith("channel=stable\nprerelease=false\n"))
            with patch.dict(os.environ, {"GITHUB_REF_NAME": "v1.2.3-beta.7"}):
                with self.assertRaises(ValueError):
                    metadata.resolve_version()


class InstallerTests(Fixture):
    def fixture(self, version):
        inputs = self.root / "inputs"
        channel = "beta" if "-beta." in version else "latest"
        for platform, formats in merger.FORMATS.items():
            for format_name in formats:
                directory = inputs / platform / format_name
                directory.mkdir(parents=True)
                name = merger.installer_name(version, platform, format_name)
                data = f"{platform} {format_name}".encode()
                (directory / name).write_bytes(data)
                digest = b64encode(hashlib.sha512(data).digest()).decode()
                manifest = directory / merger.manifest_name(channel, platform)
                manifest.write_text(
                    f"version: {version}\nfiles:\n  - url: {name}\n    sha512: {digest}\n"
                    f"    size: {len(data)}\n    blockMapSize: 1\nreleaseDate: '2026-10-25T00:00:00.000Z'\n"
                )
        return inputs

    def test_merges_native_versions_and_checksums(self):
        version = "1.2.3-beta.4.1"
        inputs = self.fixture(version)
        for path, native in (("windows-x64/exe/beta.yml", "1.2.3"), ("macos-x64/dmg/beta-mac.yml", "2.2.3")):
            manifest = inputs / path
            manifest.write_text(manifest.read_text().replace(f"version: {version}", f"version: {native}"))
        output = self.root / "output"
        merger.merge_release(inputs, output, version)
        manifest_version, files, date = merger.read_manifest(output / "beta-mac.yml")
        self.assertEqual(manifest_version, version)
        self.assertEqual(len(files), 4)
        self.assertEqual(date, "2026-10-25T00:00:00.000Z")
        self.assertTrue(all(file.block_map_size == 1 for file in files))
        subprocess.run(["sha256sum", "--check", "--status", "SHA256SUMS"], cwd=output, check=True)

    def test_stable_manifest(self):
        output = self.root / "output"
        merger.merge_release(self.fixture("1.2.3"), output, "1.2.3")
        self.assertTrue((output / "latest-linux.yml").is_file())

    def test_rejects_invalid_installers_before_creating_output(self):
        version = "1.2.3-beta.4.1"
        for scenario in ("missing", "duplicate", "checksum", "unexpected", "symlink", "unsafe-url",
                         "blockmap-size", "wrong-version", "missing-manifest", "wrong-channel", "wrong-windows-name"):
            with self.subTest(scenario=scenario), TemporaryDirectory() as directory:
                self.root = Path(directory)
                inputs = self.fixture(version)
                artifact = next(inputs.glob("linux-x64/deb/*.deb"))
                manifest = artifact.parent / "beta-linux.yml"
                if scenario == "missing":
                    artifact.unlink()
                elif scenario == "duplicate":
                    (artifact.parent.parent / artifact.name).write_bytes(artifact.read_bytes())
                elif scenario == "checksum":
                    artifact.write_bytes(b"x" * artifact.stat().st_size)
                elif scenario == "unexpected":
                    (inputs / "unrelated").touch()
                elif scenario == "symlink":
                    (inputs / "link").symlink_to(artifact)
                elif scenario == "unsafe-url":
                    manifest.write_text(manifest.read_text().replace("url: azkar", "url: ../azkar"))
                elif scenario == "blockmap-size":
                    manifest.write_text(manifest.read_text().replace("blockMapSize: 1", "blockMapSize: 999"))
                elif scenario == "wrong-version":
                    manifest.write_text(manifest.read_text().replace(version, "9.0.0", 1))
                elif scenario == "missing-manifest":
                    manifest.unlink()
                elif scenario == "wrong-channel":
                    manifest.rename(manifest.with_name("latest-linux.yml"))
                elif scenario == "wrong-windows-name":
                    exe = next(inputs.glob("windows-x64/exe/*.exe"))
                    exe.rename(exe.with_name(exe.name.replace("-nsis", "")))
                output = self.root / "output"
                with self.assertRaises(ValueError):
                    merger.merge_release(inputs, output, version)
                self.assertFalse(output.exists())


class RepositoryTests(Fixture):
    def test_validates_packages_and_channel(self):
        assets = self.root / "packages"
        assets.mkdir()
        version = "1.2.4-beta.5.1"
        sums = []
        for extension in ("deb", "rpm"):
            path = assets / f"azkar-{version}-linux-x64.{extension}"
            path.write_text(extension)
            sums.append(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}")
        (assets / "SHA256SUMS").write_text("\n".join(sums))
        self.assertEqual(repositories.channel_packages("dev", assets)[0], version)
        with self.assertRaisesRegex(ValueError, "Wrong channel"):
            repositories.channel_packages("stable", assets)
        path.write_text("tampered")
        with self.assertRaisesRegex(ValueError, "Checksum mismatch"):
            repositories.channel_packages("dev", assets)
        (assets / "azkar-1.2.3-linux-x64.rpm").touch()
        with self.assertRaisesRegex(ValueError, "Expected one"):
            repositories.channel_packages("dev", assets)

    def test_copies_only_public_site(self):
        website = self.root / "site"
        pages = self.root / "pages"
        public = ("download.html", "css/download.css", "js/theme.js", "js/download.js",
                  "images/preview.webp", "install.sh", "install.ps1")
        for name in (*public, "src/download.ts", "node_modules/dep/index.js", "package.json"):
            path = website / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(name)
        repositories.copy_website(website, pages)
        self.assertEqual({str(path.relative_to(pages)) for path in pages.rglob("*") if path.is_file()},
                         {*public, "index.html"})
        self.assertEqual((pages / "index.html").read_text(), "download.html")
        (website / "js/download.js").unlink()
        with self.assertRaisesRegex(ValueError, "Website asset missing"):
            repositories.copy_website(website, self.root / "other")

    def test_archive_matches_expected_paths(self):
        site = self.root / "site"
        site.mkdir()
        (site / "index.html").write_text("site")
        (site / ".nojekyll").touch()
        digest = hashlib.sha256((site / "index.html").read_bytes()).hexdigest()
        sums = self.root / "site-SHA256SUMS"
        sums.write_text(f"{digest}  ./index.html\n")
        archive = self.root / "artifact.tar"
        env = {"RUNNER_TEMP": str(self.root)}
        with tarfile.open(archive, "w") as tar:
            tar.add(site / "index.html", arcname="./index.html")
        result = self.shell("repository-site.sh", "verify-archive", env=env)
        self.assertEqual(result.returncode, 0, result.stderr)
        sums.write_text(f"{digest}  ./index.html\n{digest}  ./nested/.missing\n")
        self.assertNotEqual(self.shell("repository-site.sh", "verify-archive", env=env).returncode, 0)
        sums.write_text(f"{digest}  ./index.html\n")
        with tarfile.open(archive, "a") as tar:
            tar.add(site / ".nojekyll", arcname="./.nojekyll")
        self.assertNotEqual(self.shell("repository-site.sh", "verify-archive", env=env).returncode, 0)


class RecoveryTests(Fixture):
    def test_selects_only_first_finalization_retry_with_artifact(self):
        output = self.root / "output"
        summary = self.root / "summary"
        env = {
            "GITHUB_OUTPUT": str(output), "GITHUB_STEP_SUMMARY": str(summary), "GITHUB_SHA": "a" * 40,
            "GITHUB_SERVER_URL": "https://github.com", "GITHUB_REPOSITORY": "example/azkar",
        }
        for attempt, expired, job, expected in (
            (1, False, "Verify live repositories and publish release", "retry_run=42\nhandled=true\n"),
            (2, False, "Verify live repositories and publish release", "handled=true\n"),
            (1, True, "Verify live repositories and publish release", "handled=true\n"),
            (1, False, "Package (linux-x64)", ""),
        ):
            output.write_text("")
            run = {"workflow_runs": [{"id": 42, "run_attempt": attempt}]}
            jobs = [{"total_count": 1, "jobs": [{"name": job, "conclusion": "failure"}]}]
            files = [{"artifacts": [{"name": "repository-site-checksums", "expired": expired}]}]
            with patch.dict(os.environ, env), patch.object(recover_finalize, "api", return_value=run):
                with patch.object(recover_finalize, "api_pages", side_effect=[jobs, files]):
                    recover_finalize.main()
            self.assertEqual(output.read_text(), expected)


class EvidenceTests(Fixture):
    def test_requires_ancestry_and_unchanged_packaging(self):
        self.git("init", "-q")
        self.git("config", "user.name", "Test")
        self.git("config", "user.email", "test@example.invalid")
        script = self.root / ".github/scripts/release.py"
        script.parent.mkdir(parents=True)
        script.write_text("original")
        self.git("add", ".")
        self.git("commit", "-qm", "Tested")
        commit = self.git("rev-parse", "HEAD")
        record = self.root / "docs/release-validation.properties"
        record.parent.mkdir()
        valid = (
            f"sourceCommit={commit}\nartifactVersion=1.2.3-beta.4.1\n"
            "evidence=Recorded disposable-system install and update checks\n"
            + "".join(f"{key}=passed\n" for key in evidence.CHECKS)
        )
        record.write_text(valid)
        evidence.verify_evidence(self.root)
        record.write_text(valid.replace("deb=passed", "deb=pending"))
        with self.assertRaisesRegex(ValueError, "validation: deb"):
            evidence.verify_evidence(self.root)
        record.write_text(valid.replace(commit, "0" * 40))
        with self.assertRaisesRegex(ValueError, "ancestor"):
            evidence.verify_evidence(self.root)
        record.write_text(valid)
        script.write_text("changed")
        self.git("add", ".github")
        self.git("commit", "-qm", "Release script changed")
        with self.assertRaisesRegex(ValueError, "sources changed"):
            evidence.verify_evidence(self.root)


class GithubReleaseTests(Fixture):
    def setUp(self):
        super().setUp()
        self.log = self.root / "gh.log"
        gh = self.root / "gh"
        gh.write_text("""#!/usr/bin/env python3
import json
import os
import sys
from pathlib import Path

args = sys.argv[1:]
with Path(os.environ["MOCK_LOG"]).open("a") as log:
    log.write(" ".join(args) + "\\n")
if args[0] == "release":
    sys.exit(0)
if any("generate-notes" in arg for arg in args):
    print("Fixture release notes")
elif any("/releases/42/assets" in arg for arg in args):
    print(os.environ["MOCK_ASSETS"])
elif os.environ["MOCK_LOOKUP_FAILURE"] == "true":
    sys.exit("GitHub API unavailable")
else:
    print(os.environ["MOCK_RELEASES"])
""")
        gh.chmod(0o755)
        assets = self.root / "release-assets"
        assets.mkdir()
        (assets / "installer.deb").write_text("installer")
        self.digest = hashlib.sha256(b"installer").hexdigest()
        (self.root / "expected").write_text(f"{self.digest}  installer.deb\n")
        self.git("init", "-q")
        self.git("config", "user.name", "Test")
        self.git("config", "user.email", "test@example.invalid")
        self.git("commit", "-qm", "Release", "--allow-empty")
        self.env = {
            "PATH": f"{self.root}:{os.environ['PATH']}", "MOCK_LOG": str(self.log),
            "MOCK_RELEASES": "[]", "MOCK_LOOKUP_FAILURE": "false", "MOCK_ASSETS": "[]",
            "GITHUB_REPOSITORY": "example/azkar", "GITHUB_SHA": self.git("rev-parse", "HEAD"),
            "RELEASE_TAG": "v1.2.4", "APP_VERSION": "1.2.4", "RELEASE_CHANNEL": "stable",
            "PRE_RELEASE": "false", "RUNNER_TEMP": str(self.root),
        }

    def run_release(self, *args, **overrides):
        self.log.write_text("")
        result = self.shell("github-release.sh", *args, env=self.env | overrides)
        return result, self.log.read_text()

    def test_only_successful_missing_lookup_can_create_draft(self):
        result, calls = self.run_release("prepare-draft")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(f"--draft --title Azkar 1.2.4 --notes-file {self.root}/release-notes.md", calls)
        self.assertIn(f"--target {self.env['GITHUB_SHA']}", calls)
        result, calls = self.run_release("prepare-draft", MOCK_LOOKUP_FAILURE="true")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("GitHub API unavailable", result.stderr)
        self.assertNotIn("release create", calls)

    def test_resumes_drafts_and_preserves_published_assets(self):
        for state in ("true", "false"):
            records = f'[{{"tag_name":"v1.2.4","id":42,"draft":{state}}}]'
            result, calls = self.run_release("prepare-draft", MOCK_RELEASES=records)
            if state == "true":
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertIn("--clobber", calls)
                self.assertIn(f"--target {self.env['GITHUB_SHA']}", calls)
            else:
                self.assertNotEqual(result.returncode, 0)
                self.assertNotIn("release upload", calls)

    def test_publication_requires_exact_uploaded_assets(self):
        for tag, prerelease in (("v1.2.4", "false"), ("v1.2.4-beta.24", "true")):
            records = json.dumps([{"tag_name": tag, "id": 42, "draft": True}])
            asset = {"name": "installer.deb", "state": "uploaded", "digest": f"sha256:{self.digest}"}
            for assets, valid in (
                ([asset], True), ([], False), ([asset, asset | {"name": "old.deb"}], False),
                ([asset | {"digest": "sha256:" + "0" * 64}], False),
                ([asset | {"state": "starter", "digest": None}], False),
            ):
                result, calls = self.run_release("publish", "expected", RELEASE_TAG=tag, PRE_RELEASE=prerelease,
                                                 MOCK_RELEASES=records, MOCK_ASSETS=json.dumps(assets))
                self.assertEqual(result.returncode == 0, valid, result.stderr)
                self.assertEqual("release edit" in calls, valid)
                if valid:
                    latest = "false" if prerelease == "true" else "true"
                    self.assertIn(f"--draft=false --prerelease={prerelease} --latest={latest}", calls)
