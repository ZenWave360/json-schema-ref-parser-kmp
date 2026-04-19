package io.zenwave360.jsonrefparser

import kotlinx.coroutines.runBlocking

/** Blocking convenience wrapper around [RefParser.parse]. JVM only. */
fun RefParser.parseBlocking(): RefParser = runBlocking { parse() }

/** Blocking convenience wrapper around [RefParser.dereference]. JVM only. */
fun RefParser.dereferenceBlocking(): RefParser = runBlocking { dereference() }

/** Blocking convenience wrapper around [RefParser.mergeAllOf]. JVM only. */
fun RefParser.mergeAllOfBlocking(): RefParser = runBlocking { mergeAllOf() }
