import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.vanniktech.maven.publish)
    alias(libs.plugins.kotlinx.kover)
}

group = "io.zenwave360.jsonrefparser"
version = "1.0.0-SNAPSHOT"

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
        val jsMain by getting {
            dependencies {
                implementation(libs.kotlin.node)
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
