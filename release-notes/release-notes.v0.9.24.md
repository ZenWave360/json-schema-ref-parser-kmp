# json-schema-ref-parser-kmp 0.9.24

- Added browser and Web Worker support for the Kotlin/JS artifact. The library no longer imports Node.js `fs` when it is loaded, so it can be bundled and initialized outside Node.js.
- Added `RefParserPlatform` capability reporting, including the JavaScript `refParserPlatform()` export. Unsupported `file:` and `classpath:` URIs now fail with `CapabilityUnavailableException` on platforms that do not provide those capabilities.
- Added browser-runtime coverage with headless Chromium/Karma, ensuring the published JS bundle remains free of static Node.js imports.
- Added public npm package metadata, generated TypeScript declarations, and npm publication support for `@zenwave360/json-schema-ref-parser-kmp`.
- Aligned the CI and release workflows with the Kotlin/JS Node.js 22.12.0 runtime and fixed the Webpack module configuration used by browser testing.
