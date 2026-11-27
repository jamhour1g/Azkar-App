"""Select a failed Dev finalization to retry while its inputs are available."""

import os

from release_support import api, api_pages, append, cli


def main():
    sha = os.environ["GITHUB_SHA"]
    query = f"branch=dev&event=schedule&head_sha={sha}&status=failure&per_page=1"
    runs = api(f"actions/workflows/release.yml/runs?{query}")["workflow_runs"]
    if not runs:
        return
    run = runs[0]
    run_id = run["id"]
    failed = [
        job["name"]
        for page in api_pages(f"actions/runs/{run_id}/jobs?per_page=100")
        for job in page["jobs"]
        if job["conclusion"] == "failure"
    ]
    if failed != ["Verify live repositories and publish release"]:
        return
    url = f"{os.environ['GITHUB_SERVER_URL']}/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{run_id}"
    if run["run_attempt"] == 1 and any(
        item["name"] == "repository-site-checksums" and item["expired"] is False
        for page in api_pages(f"actions/runs/{run_id}/artifacts?per_page=100")
        for item in page["artifacts"]
    ):
        append("GITHUB_OUTPUT", f"retry_run={run_id}\n")
        append("GITHUB_STEP_SUMMARY", f"Selected finalization retry on {url}.\n")
    else:
        append("GITHUB_STEP_SUMMARY",
               f"Release held for {sha} after finalization failed on {url}. "
               "Inspect the failed step, then dispatch Release manually to rebuild.\n")
    append("GITHUB_OUTPUT", "handled=true\n")


if __name__ == "__main__":
    cli(main)
