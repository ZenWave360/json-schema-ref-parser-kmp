import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform") version "2.0.21"
    id("com.vanniktech.maven.publish") version "0.34.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.4"
}

group = "io.zenwave360.jsonrefparser"
version = "0.9.21"

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
        binaries.executable()
        useEsModules()
        compilations["main"].packageJson {
            customField("name", "@zenwave360/json-schema-ref-parser-kmp")
            customField("description", "JSON Schema \$ref parser for Kotlin Multiplatform (JVM and JS/Node.js)")
            customField("license", "MIT")
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("it.krzeminski:snakeyaml-engine-kmp:3.0.2")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            }
        }
        val jvmTest by getting
        val jsMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin-wrappers:kotlin-node:18.16.12-pre.610")
            }
        }
        val jsTest by getting
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

tasks.named("check") {
    dependsOn("nodeIntegrationTest")
}

val hasSigningCredentials = sequenceOf(
    "signingInMemoryKey",
    "signingKey",
    "signing.secretKeyRingFile"
).any { !providers.gradleProperty(it).orNull.isNullOrBlank() }

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
