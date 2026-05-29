JSON Schema $Ref Parser for JVM and Node.js
===========================================

[![Maven Central](https://img.shields.io/maven-central/v/io.zenwave360.jsonrefparser/json-schema-ref-parser-kmp.svg?label=Maven%20Central&logo=apachemaven)](https://search.maven.org/artifact/io.zenwave360.jsonrefparser/json-schema-ref-parser-kmp)
[![build](https://github.com/ZenWave360/json-schema-ref-parser-kmp/actions/workflows/main.yml/badge.svg?branch=main)](https://github.com/ZenWave360/json-schema-ref-parser-kmp/actions/workflows/main.yml)
[![line coverage](https://raw.githubusercontent.com/ZenWave360/json-schema-ref-parser-kmp/badges/coverage.svg)](https://github.com/ZenWave360/json-schema-ref-parser-kmp/actions/workflows/main.yml)
[![branch coverage](https://raw.githubusercontent.com/ZenWave360/json-schema-ref-parser-kmp/badges/branches.svg)](https://github.com/ZenWave360/json-schema-ref-parser-kmp/actions/workflows/main.yml)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](https://github.com/ZenWave360/json-schema-ref-parser-kmp/blob/main/LICENSE)

Parse, resolve, and dereference JSON Schema `$ref` pointers on the JVM and Node.js.

This library is the Kotlin Multiplatform evolution of [json-schema-ref-parser-jvm](https://github.com/ZenWave360/json-schema-ref-parser-jvm). It provides:

- `RefParser` as the primary API for Kotlin, JVM, and Node.js integrations
- a JVM compatibility layer for existing `$RefParser` and `$Refs` users
- JS exports for Node.js runtimes

## Project Status

This is the library to choose for new JVM and Node.js integrations.

- New users should start with `json-schema-ref-parser-kmp`.
- Existing `json-schema-ref-parser-jvm` users can migrate incrementally using the JVM compatibility layer.
- The `$RefParser` API remains available on the JVM for compatibility, but it is not the primary API going forward.

Current status:

- The Kotlin Multiplatform core is working and is intended to match the JVM implementation behavior.
- The main API has been reshaped around `RefParser`.
- Circular reference handling has been reworked in the new implementation and still benefits from broader real-world validation.

## The Problem

If you work with JSON Schema, OpenAPI, or AsyncAPI specifications, you know the pain: schemas spread across multiple files, `$ref` pointers everywhere, and yet another ad hoc parser seems inevitable.

This library handles all of that for you. It is a full [JSON Reference](https://tools.ietf.org/html/draft-pbryan-zyp-json-ref-03) and [JSON Pointer](https://tools.ietf.org/html/rfc6901) implementation that crawls even complex schemas and returns a simple object tree with source locations and resolved ref tracking.

## Quick Start

### Kotlin with `RefParser`

```kotlin
val doc = RefParser("path/to/openapi.yml")
    .dereference()
    .mergeAllOf()
    .getParsedDocument()

val schema: Map<String, Any?> = doc.schema
```

### Java with `RefParser`

For Java callers, `RefParser` is still the primary API, but the pipeline is suspend-first. On the JVM, use the blocking helper functions.

```java
import static io.zenwave360.jsonrefparser.RefParserBlockingKt.dereferenceBlocking;
import static io.zenwave360.jsonrefparser.RefParserBlockingKt.mergeAllOfBlocking;
import static io.zenwave360.jsonrefparser.io.DefaultLoadersKt.defaultLoaders;

import io.zenwave360.jsonrefparser.RefParser;
import io.zenwave360.jsonrefparser.model.OnCircular;
import io.zenwave360.jsonrefparser.model.OnMissing;
import io.zenwave360.jsonrefparser.model.ParsedDocument;
import io.zenwave360.jsonrefparser.model.RefParserOptions;
import java.io.File;
import java.util.Collections;
import java.util.Map;

File file = new File("src/main/resources/openapi.yml");

RefParser parser = new RefParser(
        file.toURI().toString(),
        new RefParserOptions(OnCircular.SKIP, OnMissing.FAIL),
        Collections.emptyList(),
        defaultLoaders(Collections.emptyList())
);

ParsedDocument doc = mergeAllOfBlocking(dereferenceBlocking(parser)).getParsedDocument();
Map<String, Object> schema = (Map<String, Object>) doc.getSchema();
```

### Node.js

```js
import { dereferenceSchema } from "@zenwave360/json-schema-ref-parser-kmp";

const doc = await dereferenceSchema("file:///workspace/openapi.yml", true);
console.log(doc.schema);
```

## Installation

### Java and Kotlin on the JVM

Gradle:

```kotlin
dependencies {
    implementation("io.zenwave360.jsonrefparser:json-schema-ref-parser-kmp-jvm:<version>")
}
```

Maven:

```xml
<dependency>
  <groupId>io.zenwave360.jsonrefparser</groupId>
  <artifactId>json-schema-ref-parser-kmp-jvm</artifactId>
  <version>${json-schema-ref-parser-kmp-jvm.version}</version>
</dependency>
```

### Kotlin Multiplatform

```kotlin
dependencies {
    implementation("io.zenwave360.jsonrefparser:json-schema-ref-parser-kmp:<version>")
}
```

### Node.js

The Node.js API is available from the JS target exports:

- `parseSchemaText(input, baseUri?)`
- `dereferenceSchema(uri, mergeAllOf?)`
- `dereferenceSchemaText(input, baseUri?, mergeAllOf?)`

The npm package name is `@zenwave360/json-schema-ref-parser-kmp`. npm publishing is not enabled yet, so this repository currently publishes Maven Central artifacts only.

## Usage

### `RefParser` Primary API

`RefParser` is the main API for Kotlin Multiplatform, Kotlin/JVM, and new JVM integrations.

### Dereference

Resolves all `$ref` pointers and replaces them inline, including references to external files and remote URLs.

```kotlin
val doc = RefParser("path/to/schema.yml")
    .dereference()
    .getParsedDocument()
```

### Merge `allOf`

After dereferencing, merges every `allOf` array into its parent object, combining fields such as `properties` and `required`.

```kotlin
val doc = RefParser("path/to/schema.yml")
    .dereference()
    .mergeAllOf()
    .getParsedDocument()
```

### Circular references

By default, circular references are resolved by preserving object identity. You can also skip them or fail fast.

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
            key = "Authorization",
            value = "Bearer <token>",
            urlMatcher = { url -> url.contains("api.example.com") },
        )
    ),
).dereference().getParsedDocument()
```

### Classpath loading on the JVM

```kotlin
val doc = RefParser("classpath:/schemas/openapi.yml")
    .dereference()
    .getParsedDocument()
```

### Source locations

Every node in the parsed document carries its original file and line and column range, even after dereferencing across multiple files.

```kotlin
val doc = RefParser("path/to/schema.yml")
    .dereference()
    .getParsedDocument()

val location = doc.locations["/info"]
println("${location?.file}:${location?.line}:${location?.column}")
```

### Original ref tracking

After dereferencing, you can look up which `$ref` string a given object came from.

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

### Java on the JVM

Java callers can use the same `RefParser` model, plus the JVM blocking helpers:

```java
import static io.zenwave360.jsonrefparser.RefParserBlockingKt.dereferenceBlocking;
import static io.zenwave360.jsonrefparser.RefParserBlockingKt.mergeAllOfBlocking;
import static io.zenwave360.jsonrefparser.io.DefaultLoadersKt.defaultLoaders;

import io.zenwave360.jsonrefparser.RefParser;
import io.zenwave360.jsonrefparser.model.OnCircular;
import io.zenwave360.jsonrefparser.model.OnMissing;
import io.zenwave360.jsonrefparser.model.ParsedDocument;
import io.zenwave360.jsonrefparser.model.RefParserOptions;
import java.io.File;
import java.util.Collections;

File file = new File("src/main/resources/openapi.yml");

RefParser parser = new RefParser(
        file.toURI().toString(),
        new RefParserOptions(OnCircular.SKIP, OnMissing.FAIL),
        Collections.emptyList(),
        defaultLoaders(Collections.emptyList())
);

ParsedDocument doc = mergeAllOfBlocking(dereferenceBlocking(parser)).getParsedDocument();
System.out.println(doc.getSchema());
```

### `$Ref` Compatibility API on the JVM

The JVM module still ships the legacy compatibility API for existing `json-schema-ref-parser-jvm` users:

- `$RefParser`
- `$Refs`
- `$Ref`
- `$RefParserOptions`

Use this when you want a low-friction migration path from the old JVM library. For new JVM code, prefer `RefParser`.

```java
import static io.zenwave360.jsonrefparser.$RefParserOptions.OnCircular.SKIP;

import io.zenwave360.jsonrefparser.$RefParser;
import io.zenwave360.jsonrefparser.$RefParserOptions;
import io.zenwave360.jsonrefparser.$Refs;
import java.io.File;

File file = new File("src/main/resources/openapi.yml");

$Refs refs = new $RefParser(file)
        .withOptions(new $RefParserOptions().withOnCircular(SKIP))
        .dereference()
        .mergeAllOf()
        .getRefs();

Object schema = refs.schema();
```

### Source locations with `$Refs`

```java
import io.zenwave360.jsonrefparser.$RefParser;
import java.io.File;

File file = new File("src/main/resources/openapi.yml");

var range = new $RefParser(file)
        .parse()
        .getRefs()
        .getJsonLocationRange("$.info");
```

### Node.js

The JS target exports plain object APIs designed for ESM runtimes in Node.js.

Dereference a file:

```js
import { dereferenceSchema } from "@zenwave360/json-schema-ref-parser-kmp";

const doc = await dereferenceSchema("file:///workspace/openapi.yml", true);

console.log(doc.hasCircularRefs);
console.log(doc.locations["/info"]);
```

Dereference in-memory text:

```js
import { dereferenceSchemaText } from "@zenwave360/json-schema-ref-parser-kmp";

const yaml = `
type: object
properties:
  pet:
    $ref: "#/definitions/Pet"
definitions:
  Pet:
    type: object
    properties:
      name:
        type: string
`;

const doc = await dereferenceSchemaText(yaml, "memory://pet.yml", true);
console.log(doc.schema);
```

Parse without dereferencing:

```js
import { parseSchemaText } from "@zenwave360/json-schema-ref-parser-kmp";

const doc = parseSchemaText("{\"type\":\"object\"}", "memory://schema.json");
console.log(doc.locations[""]);
```

## Features

- Parses JSON, YAML, and Avro schemas, or any mix of them
- Dereferences `$ref` pointers into a plain object tree
- Cross-file references: local files, remote URLs, and classpath resources on the JVM
- Object identity: two `$ref` pointers to the same target resolve to the same object instance
- Source locations for every parsed node
- Original ref tracking for resolved objects
- Merges `allOf` arrays into a single object
- Authentication headers and query parameters for remote loading
- Circular reference detection with resolve, skip, and fail modes
- Missing reference handling with skip and fail modes
- JVM and Node.js support through Kotlin Multiplatform
- JVM compatibility layer for existing `json-schema-ref-parser-jvm` users

## Release

Maven Central publishing is handled by GitHub Actions. Repository prerequisites and the release sequence are documented in [RELEASING.md](RELEASING.md).

## License

JSON Schema $Ref Parser KMP is free and open-source under the [MIT license](LICENSE).
