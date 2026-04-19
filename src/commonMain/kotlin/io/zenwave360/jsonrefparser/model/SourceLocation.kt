package io.zenwave360.jsonrefparser.model

data class SourceLocation(
    /** URI string of the origin file. */
    val file: String,
    /** 0-based line of the start mark. */
    val line: Int,
    /** 0-based column of the start mark. */
    val column: Int,
    val endLine: Int,
    val endColumn: Int,
)
