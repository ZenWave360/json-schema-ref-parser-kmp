package io.zenwave360.jsonrefparser

/**
 * Read the text content of a test resource file.
 *
 * @param path Relative path within `src/commonTest/resources`, e.g. `"GH-18.json"`.
 */
expect fun readTestFile(path: String): String

/**
 * Return a fully-qualified URI for a test resource file, suitable for passing
 * to [RefParser] so that cross-file `$ref` resolution can locate sibling files.
 *
 * @param path Relative path within `src/commonTest/resources`, e.g. `"GH-36/root.json"`.
 */
expect fun testResourceUri(path: String): String
