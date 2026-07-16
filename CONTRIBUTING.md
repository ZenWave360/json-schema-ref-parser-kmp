# Contributing to JSON Schema $Ref Parser KMP

## Building Locally

### Prerequisites

- JDK 17 or higher
- Node.js 18 or higher

### Build Commands

```bash
# Clean and build everything
./gradlew clean build

# Run JVM tests
./gradlew jvmTest

# Run JS tests
./gradlew jsTest

# Run tests with coverage reports
./gradlew build koverHtmlReport koverLog

# Print coverage in the console
./gradlew koverPrintCoverage

# Build the JS package and run Node.js integration tests
./gradlew nodeIntegrationTest

# Publish to Maven Local
./gradlew clean publishToMavenLocal
```

## Release Process

See [RELEASING.md](RELEASING.md) for the full flow. In short: add a `release-notes/release-notes.v<version>.md` file, then trigger the **Release from Notes** workflow from GitHub Actions with the release version, next development version, and target branch. It prepares the version bump, tags the release, uploads the deployment to Maven Central as USER_MANAGED, and creates the GitHub Release. A human still has to log into [central.sonatype.com](https://central.sonatype.com) and click **Publish** to make it live.

### Snapshot Releases

Snapshots are automatically built and published on pushes to `develop` and `next` through the **Build and Publish Snapshots** workflow.

That workflow:

- Runs `./gradlew build koverHtmlReport koverLog`
- Prints coverage to the console
- Uploads coverage reports
- Publishes snapshot artifacts to Maven Central

### 4. Main Branch Verification

Pushes to `main` trigger the **Verify Main and Publish Coverage** workflow, which:

- Builds and tests the project
- Generates Kover coverage reports
- Publishes coverage badges to the `badges` branch

## Required Secrets

The following GitHub secrets must be configured for publishing:

- `CENTRAL_USERNAME` - Maven Central username
- `CENTRAL_TOKEN` - Maven Central token
- `SIGN_KEY` - GPG signing key
- `SIGN_KEY_PASS` - GPG signing key password
- ~~`NPM_TOKEN` - npm authentication token~~, not needed while npm publishing is disabled
