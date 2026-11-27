# GitHub Actions scripts

Run these commands from the repository root. The Ubuntu 24.04 jobs use the runner's
`python3` and the standard library. Bash scripts use Git, GitHub CLI, jq, curl, or the
platform packaging tools as needed.

- `ci/` contains PR authorization, status reporting, workflow lint, and Maven throttling retries.
  Jobs with status write permissions load their scripts from the commit that defines the workflow.
- `release/` contains release metadata, installer and evidence validation, GitHub publication,
  and Linux repository signing and Pages verification.
- `tests/` contains local fixtures for the CI and release entry points.

```bash
python3 -B -m unittest discover -s .github/scripts/tests
./.github/scripts/ci/check_workflows.sh
```

Workflow lint uses ShellCheck and actionlint. It downloads the pinned actionlint binary with a
checksum check on Linux x64 when actionlint is absent.
