# Release Security Model

This document describes how json-schema-ref-parser-kmp releases are produced,
which security boundaries protect the publication credentials (Maven Central
**and** npm), the required GitHub/npm configuration, and the residual risks.
It follows the same model as `ZenWave360/zenwave-sdk` (see that repository's
`docs/release-security.md`), adapted to Gradle/Kotlin Multiplatform and
extended with npm publication.

## The solo-developer release flow

```text
1. Write release-notes/release-notes.v<VERSION>.md and commit it (with any
   intended release changes) to the protected 'main' branch.
2. Actions → "Release from Notes" → Run workflow (branch: main)
   → version: e.g. 1.0.0              (required)
   → developmentVersion: optional     (defaults to next patch SNAPSHOT)
   → publishNpm: default false        (flip when npm is set up, see below)
3. The workflow automatically: validates → creates the release commit and tag
   → builds and tests (JVM + JS + Node integration tests) → waits for approval
   of the 'maven-central-upload' environment → signs and uploads the Central
   deployment (autoPublish=false) → creates the GitHub release → syncs main
   back to develop.
4. Review the deployment at https://central.sonatype.com/publishing/deployments
   and click Publish. Nothing is public on Maven Central until you do.
5. (When npm is enabled) approve the 'npm-publish' environment — its own,
   separate approval — and the verified tarball is published to npm via OIDC
   trusted publishing with provenance. You can approve this after clicking
   Publish in the Central Portal to keep the ecosystems in lockstep.
```

Human decision points: workflow dispatch → Central environment approval →
Portal Publish click → (optional) npm environment approval. Everything else is
automated.

### Accepted version formats

| Input                | Accepted                       | Rejected                                                            |
| -------------------- | ------------------------------ | ------------------------------------------------------------------- |
| `version`            | `1.0.0`, `1.0.0-rc.1`          | `v1.0.0`, `1.0`, `latest`, `main`, `../x`, anything with spaces/`;` |
| `developmentVersion` | empty (auto), `1.0.1-SNAPSHOT` | anything not `X.Y.Z-SNAPSHOT`                                        |
| `publishNpm`         | boolean checkbox               | —                                                                    |

Inputs are passed to scripts via environment variables and validated with a
strict regex before any other use. No branch, ref, SHA, repository or path
inputs exist.

### Release notes convention

Notes must exist at `release-notes/release-notes.v<VERSION>.md` **before**
dispatching. The path is derived from the validated version. Validation fails
if the file is missing, empty, a symlink, or never mentions the version
string. The exact same file becomes the GitHub release body.

## Jobs, code execution and credentials

| Job | Executes repository-controlled code? | Credentials |
| --- | --- | --- |
| `validate` | No build logic (git/grep/curl/jq only) | `GITHUB_TOKEN` read-only |
| `prepare-release` | **No** — version bump is a `sed` edit of `build.gradle.kts`, no Gradle runs | `GITHUB_TOKEN` `contents:write`, injected only into the final push step via `GIT_ASKPASS`; checkout uses `persist-credentials: false` |
| `build-and-test` | Yes — full `./gradlew build` + local staging + `npm pack` (Gradle plugins, Kotlin/JS tooling and the Node integration tests are arbitrary code execution) | `GITHUB_TOKEN` read-only; **no** Central/npm/signing secrets |
| `sign-and-upload-central` | **No** — no checkout; fixed tooling (`gpg`, `sha256sum`, `zip`, `curl`) over verified immutable artifacts | `CENTRAL_USERNAME`, `CENTRAL_TOKEN`, `SIGN_KEY`, `SIGN_KEY_PASS` from the `maven-central-upload` environment; `GITHUB_TOKEN` read-only |
| `create-github-release` | No build logic (`gh release create`) | `GITHUB_TOKEN` `contents:write` |
| `publish-npm` | **No** — no checkout; publishes the verified tarball with `--ignore-scripts` | **No stored credential at all**: OIDC (`id-token: write`) via the `npm-publish` environment |
| `sync-develop` | No checkout; GitHub API only | `GITHUB_TOKEN` `contents:write`, `pull-requests:write`, `actions:write` |

Maven Central credentials and npm publication capability are **never present
in the same job**, and neither ever coexists with a write-capable GitHub
token. The two privileged jobs check out no repository code: the only code
running while credentials are available is the audited shell inline in the
workflow file (itself protected on `main`).

## Trusted commit capture and the "main moved" policy

Identical to zenwave-sdk: `github.sha` is resolved once at dispatch; the run
refuses non-`main` dispatch, verifies reachability from `origin/main`, and
threads the SHA (and derived release commit) through every job. **Policy: fail
if `main` moved** — `prepare-release` re-checks `origin/main` before its
atomic, non-fast-forward push; if you pushed mid-release, the run fails before
any tag or commit exists.

## Release commit and tag

The version bump is a deterministic `sed` replacement of the single top-level
`version = "..."` line in `build.gradle.kts` — deliberately **not** a Gradle
invocation, so the only job with a write token before publication executes no
repository-controlled code at all. The job fails if the file does not contain
exactly one version line, if the result does not match the expected line, or
if anything other than `build.gradle.kts` changed. Both commits (release +
next SNAPSHOT) and the annotated tag `vX.Y.Z` are pushed atomically. The
privileged jobs later re-verify tag → release-commit → reachable-from-main via
the GitHub API.

## Artifact trust boundary

`build-and-test` runs the wrapper-validated Gradle build (`build` = all tests
including the Node.js integration tests) and stages the exact Maven
publications with `publishAllPublicationsToLocalStagingRepository` into a
local `file://` repository — no credentials, no signing (the build only signs
when signing credentials are configured, and none are present). It also packs
the Kotlin/JS-generated npm package (`npm pack --ignore-scripts`) after
verifying its `name` and `version`. One artifact is uploaded:

```text
release-staging/
  maven/io/zenwave360/jsonrefparser/...   # unsigned Maven publications
  npm/zenwave360-json-schema-ref-parser-kmp-<V>.tgz
  SHA256SUMS
  release-manifest.json                   # binds everything to repo, version,
                                          # tag, both SHAs, run id, tarball name
```

Both privileged jobs download it **by fixed name from the same run** and,
before touching anything, reject unexpected entries/symlinks/traversal,
verify every manifest field against the current run, and verify every
checksum. The Central job signs only allowlisted extensions
(`.pom`, `.jar`, `.module`, `.klib`, `.json` — the KMP publication set) and
fails loudly on any surprise file type. The npm job additionally verifies the
tarball contains only `package/` entries, that the embedded `package.json`
matches the expected name/version, and that it declares **no lifecycle
scripts**. Downloaded content is never executed.

## Maven Central boundary

Same as zenwave-sdk: the bundle is uploaded via the Central Portal Publisher
API with `publishingType=USER_MANAGED` (the API equivalent of
`autoPublish=false`; the vanniktech Gradle plugin is not used for the release
upload). The workflow never calls the publish operation — you click
**Publish** in the Portal.

> The GitHub Environment approval protects access to the credentials. The
> Maven Central Portal Publish button controls the normal release process.
> Because the Central token can technically publish, the Portal button is not
> an absolute security boundary against malicious code already executing with
> that token.

## npm publication

### Design

- Separate job (`publish-npm`), separate protected environment
  (`npm-publish`) with its **own required reviewer** — approving the Maven
  Central upload does not approve npm.
- Gated by the `publishNpm` dispatch input, **default `false`** while the npm
  account does not exist. Enabling npm for a release is an explicit checkbox;
  once npm is fully set up, flip the input's `default:` to `true` in a
  reviewed one-line change.
- **OIDC trusted publishing only — no npm token is ever stored.** The job has
  `id-token: write`; npm validates GitHub's OIDC claims (repository, workflow
  file, environment) and issues a short-lived credential for that single
  publish. Provenance attestations are generated automatically. This is the
  strongest granularity npm offers: there is no long-lived secret to leak,
  scope, or rotate.
- The job publishes the **verified immutable tarball** from the build job with
  `npm publish <tarball> --ignore-scripts --access public`. No checkout, no
  `npm install`, no lifecycle scripts.
- Ordered after the Central upload so both ecosystems release the same
  immutable release commit; approve it whenever you are ready (e.g. after
  clicking Publish in the Portal). Rejecting or ignoring the approval leaves
  Maven Central and the GitHub release unaffected.

### One-time npm setup (when the account exists) {#first-npm-publish}

OIDC trusted publishing can only be configured on an **existing** package, so
the very first publish is manual, from your machine:

1. Create the npm account and the `zenwave360` organization (the package is
   scoped: `@zenwave360/json-schema-ref-parser-kmp`). Enable 2FA.
2. Build the tarball from the release tag — never from a working tree:
   `git checkout vX.Y.Z && ./gradlew build && npm pack --ignore-scripts ./build/js/packages/json-schema-ref-parser-kmp`
3. `npm publish <tarball> --access public` (npm will prompt for 2FA).
4. On npmjs.com → package → Settings → **Trusted Publisher**: GitHub Actions,
   organization `ZenWave360`, repository `json-schema-ref-parser-kmp`,
   workflow `release-from-notes.yml`, environment `npm-publish`.
5. Package Settings → Publishing access: **Require two-factor authentication
   or an automation or granular access token** (or the trusted-publisher-only
   setting when available) so a stolen password alone cannot publish.
6. In GitHub, create the `npm-publish` environment (reviewer: you; deployment
   branches: `main` only). No secrets are needed in it.
7. Future releases: tick `publishNpm` (and later flip its default to `true`).

### npm residual risk

Anyone able to get a run of this workflow's `publish-npm` job approved can
publish — the boundary is the environment's required reviewer plus npm's
OIDC claim validation (repo + workflow file + environment). There is no token
to steal; compromise requires control of protected `main` **and** an approval.

## Required GitHub configuration

### Environments

| Environment | Reviewer | Deployment branches | Secrets |
| --- | --- | --- | --- |
| `maven-central-upload` | required (you) | `main` only | `CENTRAL_USERNAME`, `CENTRAL_TOKEN`, `SIGN_KEY`, `SIGN_KEY_PASS` |
| `maven-central-snapshots` | none (automatic) | `develop`, `next` | same four (ideally a separate Central token) |
| `npm-publish` | required (you) | `main` only | **none** (OIDC) |

**After creating the environments, delete the repository-level copies of the
secrets** — repository-level secrets are readable by any workflow on any
branch, which defeats the design. Do not allow administrators to bypass
required reviewers.

### Repository rules

Same as zenwave-sdk:

- **`main` ruleset**: require PR (approvals 0 while solo — you cannot approve
  your own PRs; enable code-owner review once a second maintainer exists),
  require status checks, block force pushes and deletion, enforce for admins,
  bypass **only** for the GitHub Actions app (so `prepare-release` can push
  the release commits), Settings → Actions → Workflow permissions set to
  **read-only** default.
- **Tag ruleset `v*`**: creation only via the bypass actor; updates and
  deletions blocked for everyone (immutable tags).
- **CODEOWNERS** (`.github/CODEOWNERS`) covers `.github/`, Gradle build files
  and wrapper, `kotlin-js-store/` (Kotlin/JS yarn lockfile),
  `nodejs-test-project/`, `release-notes/` and the release docs. Ineffective
  until code-owner review is required by the ruleset.

## Concurrency, duplicates and reruns

Identical model to zenwave-sdk:

- one `release-<repo>` concurrency group, never cancelled;
- `validate` fails on existing tag / GitHub release / Maven Central version /
  npm version (when `publishNpm=true`); the privileged jobs re-check
  `published` state (Central Portal API, npm registry) immediately before
  uploading/publishing;
- reruns replay the captured SHA, job outputs and same-run artifacts — a rerun
  can never rebuild a different commit under the same version. "Re-run all
  jobs" after `prepare-release` succeeded fails fast in `validate` (tag
  exists): intentional duplicate protection. Artifacts are retained 14 days.

## Snapshot publication

`publish-maven-snapshots.yml` publishes `-SNAPSHOT` builds on push to
`develop`/`next` via `./gradlew publishToMavenCentral` (the vanniktech plugin
routes SNAPSHOT versions to the Central snapshots repository). Hardening:
pinned actions, Gradle wrapper validation, `permissions: contents: read`,
`persist-credentials: false`, concurrency group, a version guard that refuses
non-SNAPSHOT versions, and secrets scoped to the publish step and sourced from
the `maven-central-snapshots` environment.

> Residual risk: any code merged into the protected snapshot branch executes
> (via Gradle plugins, dependencies and Kotlin/JS tooling) with the snapshot
> publishing credentials present while `./gradlew publishToMavenCentral` runs.

## Credential rotation and incident response

Same procedures as zenwave-sdk (`docs/release-security.md` there):

- rotate `CENTRAL_TOKEN` at central.sonatype.com/account; rotate the GPG key
  pair and publish a revocation certificate if exposure is suspected;
- npm: nothing to rotate (OIDC); if the trusted publisher configuration is
  suspected compromised, remove it on npmjs.com and re-add it; npm packages
  can be deprecated (`npm deprecate`) and, within 72h, unpublished;
- check the Central Portal for deployments you did not create and drop them;
  audit Actions run history and environment approval logs;
- the workflows never write secrets to the workspace, never upload key
  material, shred the ephemeral `GNUPGHOME` even on failure, and pass
  credentials via files/stdin rather than argv.

## Residual risks (explicit)

1. **The Central token can publish** — `USER_MANAGED` is procedural; see the
   Maven Central boundary section.
2. **Snapshot credentials trust `develop`/`next`** — see snapshot section.
3. **npm publication trusts the environment approval + OIDC claims** — no
   token exists, but an approved run of this workflow from `main` can publish.
4. **GitHub Actions app bypass on `main`** — narrowed by read-only default
   workflow permissions and CODEOWNERS on `.github/`; use a dedicated GitHub
   App to narrow further.
5. **Trust in `main` itself** — everything on `main` is by definition trusted;
   branch protection and reviews are what make that hold.

## Supplementary controls

- Gradle wrapper validation (`gradle/actions/wrapper-validation`) runs before
  any `./gradlew` invocation — the committed wrapper jar is executable code.
- Dependabot (`.github/dependabot.yml`): github-actions SHA pins, Gradle deps,
  and the Node integration-test project.
- `kotlin-js-store/yarn.lock` locks the Kotlin/JS toolchain's npm dependency
  tree; keep it committed and reviewed.
- The `nodejs-test-project` has no lockfile: its `npm install` (build job
  only, no secrets) resolves freshly on every build. Consider committing a
  `package-lock.json` for reproducibility.
- Consider `actionlint`/`zizmor` in CI over `.github/workflows/`, and GitHub
  artifact attestations for the staged artifacts (npm provenance is already
  automatic via trusted publishing).
