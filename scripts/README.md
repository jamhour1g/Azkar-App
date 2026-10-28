# Repository scripts

Run scripts from the repository root. Kotlin entry points use `kotlinc -script` and need
the same Kotlin compiler configured in CI. Shell scripts need Bash; workflow checks also
require ShellCheck and actionlint (downloaded with a pinned checksum on Linux x64 if absent).
Release shell entry points also use Kotlin and GitHub CLI for their delegated commands.
The metadata and HTTP-test shebangs use `release/kotlin.sh`, which loads the serialization
compiler plugin bundled with the selected compiler. The launcher needs `realpath` and can also
be called explicitly: `./scripts/release/kotlin.sh SCRIPT.main.kts [arguments...]`.

- **`ci/`** — workflow linting and shell checks.
- **`release/`** — release decisions, installer validation, GitHub publication, and signed Linux repositories.
- **`tests/`** — isolated Kotlin test harnesses with mocked external commands and temporary fixtures.

## Entry points

- `ci/check_workflows.sh`: validate workflow YAML, expressions, embedded shell, and release shell scripts.
- `release/kotlin.sh`: run a Kotlin script with its matching serialization compiler plugin.
- `release/metadata.main.kts`: `select-batch`, `resolve-version`, `latest-dev-tag`, `latest-stable-tag`, or `verify-stable-source`.
- `release/github-release.sh`: `prepare-draft` or `publish`.
- `release/repository-site.sh`: `import-key`, `collect-packages`, `build`, or `verify`.
- `release/merge_nucleus_release.main.kts`: merge and validate installers and update manifests.
- `release/publish_linux_repositories.main.kts`: build and sign the repository site.
- `release/check_release_validation.main.kts`: verify recorded installed-release evidence.

`release/release-rules.main.kts` contains the pure Kotlin rules. `release/github-client.main.kts`
reads GitHub JSON through Ktor and kotlinx.serialization; both are imported helpers, not entry points.
Dependencies are pinned to the project catalog (Ktor 3.3.3, serialization 1.9.0, coroutines 1.11.0) and resolved by
`@file:DependsOn` on first execution. Keep those pins and the HTTP test dependency aligned when
updating the catalog; the client tests check this. The client uses `@Serializable` response models
and Ktor's typed body decoding; unknown API fields are ignored, while required fields remain strict.
Client methods and their callers suspend; only the CLI and test entry points bridge with
`runBlocking`. Client lifetime is scoped with `use`, and HTTP tests verify caller cancellation.

Metadata reads `GH_TOKEN`, falling back to `GITHUB_TOKEN`. Public repositories can be read without
a token under GitHub's public API limits; the metadata client does not read GitHub CLI credentials.
Workflow steps supply credentials and release inputs through their existing environment variables.
See [Releasing](../docs/releasing.md) for publication requirements and recovery.

## Local verification

These commands validate fixtures without publishing releases or deploying a site:

```bash
./scripts/ci/check_workflows.sh
./scripts/tests/test_ci_authorization.main.kts
./scripts/tests/test_release_workflow.main.kts
./scripts/tests/test_github_client.main.kts
./scripts/release/merge_nucleus_release.main.kts self-test
./scripts/release/publish_linux_repositories.main.kts self-test
./scripts/release/check_release_validation.main.kts self-test
./gradlew :spotlessKotlinCheck --console=plain
```

Keep new scripts in the matching folder. Spotless covers all `scripts/**/*.main.kts`;
the installed-release evidence check tracks the entire scripts directory.
