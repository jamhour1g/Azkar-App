"""Select release batches and versions using GitHub release metadata."""

import json
import os
import re
import subprocess
import sys
from datetime import datetime, timedelta, timezone

from urllib.request import HTTPRedirectHandler, Request, build_opener

from release_support import append, cli, repository_name, require

STABLE_TAG = re.compile(r"v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)", re.ASCII)
DEV_TAG = re.compile(r"v\d+\.\d+\.\d+-beta\.\d+(\.\d+)*", re.ASCII)

RELEASE_PATHS = (
    "app/src/main", "data/src/main", "domain/src/main", "app/packaging",
    ":(exclude)app/packaging/linux/gnomeExtension/previews",
    ":(exclude)app/packaging/linux/gnomeExtension/scripts/dev",
    ":(exclude)app/packaging/linux/gnomeExtension/tests",
    ":(exclude)app/packaging/linux/gnomeExtension/.gitignore",
    ":(exclude)app/packaging/linux/gnomeExtension/.prettierignore",
    "app/assets/icons", ".github/keys", "app/build.gradle.kts", "data/build.gradle.kts",
    "domain/build.gradle.kts", "build.gradle.kts", "settings.gradle.kts", "gradle.properties",
    "gradle", "gradlew", "gradlew.bat", "build-logic/build.gradle.kts",
    "build-logic/settings.gradle.kts", "build-logic/src/main", "package.json", "pnpm-lock.yaml",
    "pnpm-workspace.yaml", "site/package.json", "site/public/install.sh", "site/public/install.ps1",
)


def publication_date(value):
    require(isinstance(value, str), "Invalid release publication date")
    date = datetime.fromisoformat(value)
    require(date.tzinfo is not None, "Release publication date must include a timezone")
    return date


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, url):
        return None


def request(endpoint):
    url = f"https://api.github.com/repos/{repository_name()}/{endpoint}"
    headers = {
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "Azkar-release-scripts",
    }
    token = os.getenv("GH_TOKEN", "").strip() or os.getenv("GITHUB_TOKEN", "").strip()
    if token:
        headers["Authorization"] = f"Bearer {token}"
    with build_opener(NoRedirect()).open(Request(url, headers=headers), timeout=30) as response:
        return json.load(response), response.headers.get("Link", "")


def releases():
    entries = []
    page_number = 1
    while True:
        page, links = request(f"releases?per_page=100&page={page_number}")
        require(isinstance(page, list), "Invalid GitHub releases page")
        for entry in page:
            require(
                isinstance(entry, dict)
                and isinstance(entry.get("tag_name"), str)
                and type(entry.get("draft")) is bool
                and type(entry.get("prerelease")) is bool
                and "published_at" in entry,
                "Invalid GitHub release record",
            )
            if entry["published_at"] is not None:
                publication_date(entry["published_at"])
            entries.append(entry)
        if not any('rel="next"' in (part.strip() for part in link.split(";")[1:]) for link in links.split(",")):
            return entries
        # Increment the page without sending credentials to URLs in response headers.
        page_number += 1


def published(entries, prerelease, pattern):
    return (
        entry for entry in entries
        if not entry["draft"] and entry["prerelease"] == prerelease
        and entry["published_at"] is not None and pattern.fullmatch(entry["tag_name"])
    )


def stable_components(tag):
    return tuple(map(int, tag.removeprefix("v").split(".")))


def latest_stable(entries):
    return max(
        published(entries, False, STABLE_TAG),
        key=lambda entry: stable_components(entry["tag_name"]),
        default=None,
    )


def latest_dev(entries):
    return max(
        published(entries, True, DEV_TAG),
        key=lambda entry: publication_date(entry["published_at"]),
        default=None,
    )


def next_dev_tag(entries, tags):
    stable = latest_stable(entries)
    major, minor, patch = stable_components(stable["tag_name"]) if stable else (0, 1, 1)
    prefix = f"v{major}.{minor}.{patch + 1}-beta."
    # Drafts and existing tags reserve their number, including legacy beta.N.ATTEMPT.
    matching = (
        int(tag.removeprefix(prefix).split(".")[0])
        for tag in (*[entry["tag_name"] for entry in entries], *tags)
        if tag.startswith(prefix) and DEV_TAG.fullmatch(tag)
    )
    return f"{prefix}{max(matching, default=0) + 1}"


def batch_decision(event, previous, now):
    if previous is None:
        return True, "First Dev release"
    if event == "schedule" and now - publication_date(previous["published_at"]) < timedelta(hours=6):
        return False, "Waiting for the six-hour Dev release interval"
    tag = previous["tag_name"]
    head = os.environ["GITHUB_SHA"]
    require(re.fullmatch(r"[a-fA-F0-9]{40}", head), "Expected a full commit SHA")
    response, _ = request(f"compare/{tag}...{head}")
    comparison = response["status"]
    require(comparison in ("ahead", "identical", "behind", "diverged"),
            f"Unexpected GitHub comparison status: {comparison}")
    if comparison != "ahead":
        return False, f"No forward changes since {tag} ({comparison})"
    changes = subprocess.check_output(["git", "diff", "--name-only", "--no-renames", tag, head, "--", *RELEASE_PATHS])
    if changes:
        return True, f"App or installer inputs changed since {tag}"
    return False, f"No app or installer changes since {tag}"


def select_batch():
    if os.getenv("ACT") == "true" or os.environ["GITHUB_REF"].startswith("refs/tags/v"):
        publish, reason = True, "Stable tag or local rehearsal"
    else:
        require(os.environ["GITHUB_REF"] == "refs/heads/dev", "Dev releases must build dev")
        event = os.environ["EVENT"]
        require(event in ("schedule", "workflow_dispatch"), f"Unsupported Dev release event: {event}")
        publish, reason = batch_decision(event, latest_dev(releases()), datetime.now(timezone.utc))
    flag = str(publish).lower()
    append("GITHUB_OUTPUT", f"publish={flag}\n")
    append("GITHUB_STEP_SUMMARY", f"Dev/Stable release decision: {reason} (publish={flag})\n")
    print(reason)


def resolve_version():
    channel = "dev"
    if os.getenv("ACT") == "true":
        tag = "v0.0.0-beta.1"
    elif os.environ["GITHUB_REF"].startswith("refs/tags/v"):
        tag = os.environ["GITHUB_REF_NAME"]
        require(STABLE_TAG.fullmatch(tag), f"Stable tags must be vMAJOR.MINOR.PATCH: {tag}")
        channel = "stable"
    else:
        require(os.environ["GITHUB_REF"] == "refs/heads/dev", "Dev releases must build dev")
        entries = releases()
        tags = subprocess.check_output(["git", "tag", "--list", "v*-beta.*"], text=True).splitlines()
        tag = next_dev_tag(entries, tags)
    prerelease = str(channel == "dev").lower()
    append("GITHUB_OUTPUT", f"app_version={tag[1:]}\nrelease_tag={tag}\nchannel={channel}\nprerelease={prerelease}\n")


def main():
    require(len(sys.argv) == 2, "Expected one metadata command")
    command = sys.argv[1]
    if command == "select-batch":
        select_batch()
    elif command == "resolve-version":
        resolve_version()
    elif command in ("latest-dev-tag", "latest-stable-tag"):
        selector = latest_dev if command == "latest-dev-tag" else latest_stable
        entry = selector(releases())
        print(entry["tag_name"] if entry else "")
    elif command == "verify-stable-source":
        subprocess.run(["git", "fetch", "origin", "production"], check=True)
        subprocess.run(["git", "merge-base", "--is-ancestor", os.environ["GITHUB_SHA"], "origin/production"], check=True)
    else:
        raise ValueError(f"Unknown metadata command: {command}")


if __name__ == "__main__":
    cli(main)
