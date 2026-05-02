JSON Schema $Ref Parser for Kotlin Multiplatform
=================================================

[![Maven Central](https://img.shields.io/maven-central/v/io.zenwave360.jsonrefparser/json-schema-ref-parser-kmp.svg?label=Maven%20Central&logo=apachemaven)](https://search.maven.org/artifact/io.zenwave360.jsonrefparser/json-schema-ref-parser-kmp)
[![build](https://github.com/ZenWave360/json-schema-ref-parser-kmp/actions/workflows/main.yml/badge.svg?branch=main)](https://github.com/ZenWave360/json-schema-ref-parser-kmp/actions/workflows/main.yml)
[![GitHub](https://img.shields.io/github/license/ZenWave360/json-schema-ref-parser-kmp)](https://github.com/ZenWave360/json-schema-ref-parser-kmp/blob/main/LICENSE)

Parse, resolve, and dereference JSON Schema `$ref` pointers on the JVM and Node.js.

Inspired by the excellent Node.js [JSON Schema $Ref Parser](https://apidevtools.com/json-schema-ref-parser/) and its Java port [json-schema-ref-parser-jvm](https://github.com/ZenWave360/json-schema-ref-parser-jvm), this library brings `$ref` parsing and dereferencing to Kotlin Multiplatform with a coroutine-based API and an extended feature set built up over years of real-world use in [ZenWave360](https://github.com/ZenWave360) tooling.

## The Problem

If you work with JSON Schema, OpenAPI, or AsyncAPI specifications you know the pain: schemas spread across multiple files, `$ref` pointers everywhere, and you end up writing yet another ad hoc parser. This library handles all of that for you.

It is a full [JSON Reference](https://tools.ietf.org/html/draft-pbryan-zyp-json-ref-03) and [JSON Pointer](https://tools.ietf.org/html/rfc6901) implementation that crawls even the most complex schemas and returns a simple `Map` of nodes.

## Quick Start

```kotlin
val doc = RefParser("path/to/openapi.yml")
    .dereference()
    .mergeAllOf()
    .getParsedDocument()

val schema: Map<String, Any?> = doc.schema
```

## Usage

### Dereference

Resolves all `$ref` pointers and replaces them inline, including references to external files and remote URLs.

```kotlin
val doc = RefParser("path/to/schema.yml")
    .dereference()
    .getParsedDocument()
```

### Merge allOf

After dereferencing, merges every `allOf` array into its parent object, combining `properties` and `required` fields.

```kotlin
val doc = RefParser("path/to/schema.yml")
    .dereference()
    .mergeAllOf()
    .getParsedDocument()
```

### Circular references

By default circular references are resolved (the cycle is left in place as a shared object). You can also skip them or fail hard.

```kotlin
val doc = RefParser(
    uri = "path/to/schema.yml",
    options = RefParserOptions(onCircular = OnCircular.SKIP),
).dereference().getParsedDocument()

println(doc.hasCircularRefs) // true
```

### Missing references

```kotlin
val doc = RefParser(
    uri = "path/to/schema.yml",
    options = RefParserOptions(onMissing = OnMissing.SKIP),
).dereference().getParsedDocument()
```

### Authentication for remote files

```kotlin
val doc = RefParser(
    uri = "path/to/schema.yml",
    auth = listOf(
        AuthenticationValue(
            headerName = "Authorization",
            headerValue = "Bearer <token>",
            urlMatcher = { url -> url.contains("api.example.com") },
        )
    ),
).dereference().getParsedDocument()
```

### Classpath loading (JVM)

```kotlin
val doc = RefParser("classpath:/schemas/openapi.yml")
    .dereference()
    .getParsedDocument()
```

### Source locations

Every node in the parsed document carries its original file and line/column range, even after dereferencing across multiple files. Useful for error reporting and IDE tooling.

```kotlin
val doc = RefParser("path/to/schema.yml")
    .dereference()
    .getParsedDocument()

val location = doc.locations["/info"]
println("${location?.file}:${location?.line}:${location?.column}")
```

### Original ref tracking

After dereferencing you can look up which `$ref` string a given object came from.

```kotlin
val doc = RefParser("path/to/schema.yml")
    .dereference()
    .getParsedDocument()

val ref = doc.getOriginalRef(someSchemaObject)
println(ref?.refString) // e.g. "#/components/schemas/Pet"
```

### Loading from a string

Useful in tests or when you already have the document text in memory.

```kotlin
val yaml = """
    type: object
    properties:
      name:
        type: string
""".trimIndent()

val doc = RefParser.fromText(yaml).dereference().getParsedDocument()
```

## Java / Legacy API

A backwards-compatible API is provided for Java callers and for migration from [json-schema-ref-parser-jvm](https://github.com/ZenWave360/json-schema-ref-parser-jvm).

```java
File file = new File("src/test/resources/openapi/allOf.yml");
$Refs refs = new $RefParser(file)
        .withOptions(new $RefParserOptions().withOnCircular(SKIP))
        .dereference()
        .mergeAllOf()
        .getRefs();

Object schema = refs.schema();
```

## Features

- Parses JSON, YAML, and Avro schemas (avsc), or any mix of them
- Dereferences `$ref` pointers producing a plain `Map` tree
- Cross-file references: local files, remote URLs, and classpath resources (JVM)
- Object identity: two `$ref` pointers to the same target always resolve to the same object instance
- Source locations: every node maps back to the file, line, and column it came from
- Original ref tracking: look up the source `$ref` for any resolved object
- Merges `allOf` arrays into a single object
- Authentication headers for remote file loading
- Circular reference detection with three modes: resolve, skip, or fail
- Missing reference handling: skip or fail
- Runs on the JVM and Node.js (Kotlin Multiplatform)
- Minimal dependencies: just `snakeyaml-engine-kmp` and `kotlinx-coroutines`. No Jackson, no JsonPath, no Apache Commons. No version conflicts with your existing stack.

## Installation

Kotlin Multiplatform projects (Gradle):

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.zenwave360.jsonrefparser:json-schema-ref-parser-kmp:<version>")
}
```

JVM-only projects (Gradle or Maven), use the JVM artifact directly:

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.zenwave360.jsonrefparser:json-schema-ref-parser-kmp-jvm:<version>")
}
```

```xml
<!-- pom.xml -->
<dependency>
  <groupId>io.zenwave360.jsonrefparser</groupId>
  <artifactId>json-schema-ref-parser-kmp-jvm</artifactId>
  <version>${json-schema-ref-parser-kmp-jvm.version}</version>
</dependency>
```

## Release

Maven Central publishing is handled by GitHub Actions. Repository prerequisites and the release sequence are documented in [RELEASING.md](RELEASING.md).

## License

JSON Schema $Ref Parser KMP is free and open-source under the [MIT license](LICENSE).
