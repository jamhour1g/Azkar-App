"""Common operations for release scripts."""

import json
import os
import re
import subprocess
import sys
from pathlib import Path


def require(condition, message):
    if not condition:
        raise ValueError(message)


def cli(main):
    try:
        main()
    except (ValueError, KeyError, OSError, subprocess.SubprocessError) as error:
        print(error, file=sys.stderr)
        raise SystemExit(1) from None


def append(variable, text):
    with Path(os.environ[variable]).open("a", encoding="utf-8") as output:
        output.write(text)


def repository_name():
    repository = os.environ["GITHUB_REPOSITORY"]
    require(re.fullmatch(r"[A-Za-z0-9_-]+/[A-Za-z0-9_.-]+", repository)
            and repository.split("/")[1] not in (".", ".."), "Invalid GitHub repository")
    return repository


def github_api(endpoint, *flags):
    repository = repository_name()
    return json.loads(subprocess.check_output(
        ["gh", "api", "--hostname", "github.com", *flags, f"repos/{repository}/{endpoint}"],
        timeout=120,
    ))


def api(endpoint):
    return github_api(endpoint)


def api_pages(endpoint):
    pages = github_api(endpoint, "--paginate", "--slurp")
    require(isinstance(pages, list) and pages, "Invalid paginated GitHub response")
    return pages


def empty_output(path):
    require(not path.exists() or (path.is_dir() and not any(path.iterdir())),
            f"Output must be empty: {path}")
