# Releasing

This repository publishes Kotlin Multiplatform artifacts to Maven Central through the Sonatype Central Portal.

## Artifacts

The release workflow publishes these coordinates:

- `io.zenwave360.jsonrefparser:json-schema-ref-parser-kmp`
- `io.zenwave360.jsonrefparser:json-schema-ref-parser-kmp-jvm`
- `io.zenwave360.jsonrefparser:json-schema-ref-parser-kmp-js`

## Repository Prerequisites

Before the first public release, make sure all of the following are in place:

1. The GitHub repository is public.
2. The `io.zenwave360.jsonrefparser` namespace is registered and verified in the Sonatype Central Portal.
3. A GPG keypair exists for artifact signing and the public key has been published.
4. GitHub Actions secrets are configured.

Supported secret names:

- `CENTRAL_PORTAL_USERNAME` or `CENTRAL_USERNAME`
- `CENTRAL_PORTAL_TOKEN` or `CENTRAL_TOKEN`
- `MAVEN_GPG_PRIVATE_KEY` or `SIGN_KEY`
- `MAVEN_GPG_PASSPHRASE` or `SIGN_KEY_PASS`

## Release Flow

There are two workflows involved:

1. `Create Gradle Release`
   Updates `build.gradle.kts` to the release version, creates tag `v<releaseVersion>`, bumps to the next snapshot version, and opens a release PR.
2. `Publish Release to Maven Central`
   Runs automatically when a `v*` tag is pushed and executes `./gradlew publishAndReleaseToMavenCentral`.

## Manual Release Steps

1. Run the `Create Gradle Release` workflow with:
   - `releaseVersion`, for example `1.0.0`
   - `developmentVersion`, for example `1.0.1-SNAPSHOT`
2. Verify that tag `v<releaseVersion>` was pushed.
3. Watch the `Publish Release to Maven Central` workflow.
4. After Central Portal validation and release complete, wait for Maven Central indexing.

## Snapshot Publishing

The `Build and Publish Snapshots` workflow uses `./gradlew publishToMavenCentral`. Keep the project version on a `-SNAPSHOT` suffix when using that workflow.
