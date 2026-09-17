import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.vanniktech.maven.publish)
    alias(libs.plugins.kotlinx.kover)
}

group = "io.zenwave360.jsonrefparser"
version = "1.0.0-SNAPSHOT"

val npmVersion = providers.gradleProperty("npmVersion")
    .getOrElse(version.toString().replace("-SNAPSHOT", "-next.0"))

repositories {
    mavenCentral()
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    js(IR) {
        nodejs()
        browser {
            testTask {
                // The common test suite reads its fixtures from the filesystem, so it runs on
                // Node only. In the browser, JsRuntimeTest loads the library and exercises it
                // without a Node runtime; a static Node import anywhere in the bundle fails here.
                filter.includeTestsMatching("io.zenwave360.jsonrefparser.JsRuntimeTest")
                useKarma {
                    useChromeHeadless()
                }
            }
        }
        binaries.executable()
        useEsModules()
        generateTypeScriptDefinitions()
        compilations["main"].packageJson {
            customField("name", "@zenwave360/json-schema-ref-parser-kmp")
            customField("version", npmVersion)
            customField("type", "module")
            customField("types", "kotlin/json-schema-ref-parser-kmp.d.mts")
            customField("files", listOf("kotlin/", "README.md", "LICENSE"))
            customField("homepage", "https://github.com/ZenWave360/json-schema-ref-parser-kmp")
            customField("repository", mapOf(
                "type" to "git",
                "url" to "https://github.com/ZenWave360/json-schema-ref-parser-kmp"
            ))
            customField("publishConfig", mapOf(
                "access" to "public",
                "registry" to "https://registry.npmjs.org/",
                "tag" to if (npmVersion.contains("-")) "next" else "latest"
            ))
            customField("description", "JSON Schema \$ref parser for Kotlin Multiplatform (JVM, Node.js and browsers)")
            customField("license", "MIT")
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.snakeyaml.engine.kmp)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val jvmTest by getting
        val jsMain by getting
        val jsTest by getting
    }
}

tasks.named("jsProductionExecutableCompileSync") {
    inputs.files("README.md", "LICENSE").withPropertyName("npmDocumentation")
    doLast {
        copy {
            from("README.md", "LICENSE")
            into(layout.buildDirectory.dir("js/packages/json-schema-ref-parser-kmp"))
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

val nodeIntegrationTestInstall = tasks.register<Exec>("nodeIntegrationTestInstall") {
    group = "verification"
    description = "Install dependencies for Node.js integration tests"

    dependsOn("jsProductionExecutableCompileSync", "jsPackageJson", "kotlinNodeJsSetup")

    workingDir = file("nodejs-test-project")

    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    val npmCmd = if (isWindows) "npm.cmd" else "npm"

    commandLine(npmCmd, "install")
}

val nodeIntegrationTest = tasks.register<Exec>("nodeIntegrationTest") {
    group = "verification"
    description = "Run Node.js integration tests for the local JS package"

    dependsOn("nodeIntegrationTestInstall")

    workingDir = file("nodejs-test-project")

    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    val npmCmd = if (isWindows) "npm.cmd" else "npm"

    commandLine(npmCmd, "test")
}

// Test fixtures are read through process.getBuiltinModule (Node 22.3+), which keeps the test
// bundle free of static Node imports so the same bundle loads in the browser test run.
plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin> {
    the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec>().version.set("22.12.0")
}

// Karma launches Chrome through CHROME_BIN. When it is not set, fall back to a locally
// installed Chromium-based browser (Chrome, Chromium or Microsoft Edge).
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest>()
    .matching { it.name == "jsBrowserTest" }
    .configureEach {
        if (System.getenv("CHROME_BIN").isNullOrBlank()) {
            val candidates = listOf(
                "C:/Program Files/Google/Chrome/Application/chrome.exe",
                "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe",
                "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
                "C:/Program Files/Microsoft/Edge/Application/msedge.exe",
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
                "/usr/bin/google-chrome",
                "/usr/bin/chromium",
                "/usr/bin/chromium-browser",
                "/usr/bin/microsoft-edge",
            )
            candidates.firstOrNull { file(it).exists() }?.let { environment("CHROME_BIN", it) }
        }
    }

tasks.named("check") {
    dependsOn("nodeIntegrationTest")
}

val hasSigningCredentials = sequenceOf(
    "signingInMemoryKey",
    "signingKey",
    "signing.secretKeyRingFile"
).any { !providers.gradleProperty(it).orNull.isNullOrBlank() }

// Local staging repository used by the release workflow: the build job publishes
// here with NO credentials (task: publishAllPublicationsToLocalStagingRepository),
// and a separate privileged job signs and uploads the result to the Central
// Portal without executing any Gradle code. See docs/release-security.md.
publishing {
    repositories {
        maven {
            name = "localStaging"
            url = uri(layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    if (hasSigningCredentials) {
        signAllPublications()
    }
    pom {
        name.set("JSON Schema Ref Parser KMP")
        description.set("JSON Schema \$ref parser, resolver and dereferencer for Kotlin Multiplatform (JVM and JS/Node.js)")
        url.set("https://github.com/ZenWave360/json-schema-ref-parser-kmp")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("ivangsa")
                name.set("Ivan Garcia Sainz-Aja")
                email.set("ivangsa@gmail.com")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/ZenWave360/json-schema-ref-parser-kmp.git")
            developerConnection.set("scm:git:ssh://github.com/ZenWave360/json-schema-ref-parser-kmp.git")
            url.set("https://github.com/ZenWave360/json-schema-ref-parser-kmp")
        }
    }
}
