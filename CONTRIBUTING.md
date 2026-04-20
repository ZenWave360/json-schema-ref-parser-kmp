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

### 1. Create a Release

Trigger the **Create Gradle Release** workflow from GitHub Actions:

1. Go to **Actions** → **Create Gradle Release** → **Run workflow**
2. Enter the release version, for example `0.2.0`
3. Enter the next development version, for example `0.3.0-SNAPSHOT`

This workflow will:

- Update `version` in `build.gradle.kts` to the release version
- Create a git tag `v{version}`
- Update `version` in `build.gradle.kts` to the next development version
- Push a release branch
- Create a pull request against `main`
- Enable PR auto-merge
- Push the release tag

### 2. Publish the Release

After the release tag is created, publish a GitHub Release:

1. Go to **Releases** → **Draft a new release**
2. Select the tag created in step 1, for example `v0.2.0`
3. Generate release notes or write your own
4. Publish the release

This automatically triggers the **Publish Release to Maven Central and NPM** workflow, which:

- Checks out the released tag
- Builds and tests the project
- Publishes all Maven publications to Maven Central
- Uploads build artifacts
- ~~Publishes the JS package to npm~~, currently disabled in the workflow

### 3. Snapshot Releases

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
