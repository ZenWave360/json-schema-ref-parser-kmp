# json-schema-ref-parser-kmp 0.9.21

- Parses JSON, YAML, and Avro schemas, or any mix of them
- Dereferences `$ref` pointers into a plain object tree
- Cross-file references: local files, remote URLs, and classpath resources on the JVM
- Object identity: two `$ref` pointers to the same target resolve to the same object instance
- Merges `allOf` arrays into a single object
- Circular reference handling with resolve, skip, and fail modes
- Missing reference handling with skip and fail modes
- Source locations for every parsed node, even after dereferencing across multiple files
- Original `$ref` tracking for resolved objects
- Authentication headers and query parameters for remote loading
- Configurable loader chains, including classpath loading on the JVM
- Loading from in-memory text, useful in tests or when the document text is already available
- `RefParser` as the primary suspend-first API for Kotlin and Node.js
- `JavaRefParser` as the blocking JVM facade for Java and Kotlin/JVM callers
- `$RefParser`/`$Refs`/`$Ref` JVM compatibility layer for existing `json-schema-ref-parser-jvm` users
- JVM and Node.js support through Kotlin Multiplatform
- RFC 7396 JSON Merge Patch utility (non-mutating, graph-safe)
