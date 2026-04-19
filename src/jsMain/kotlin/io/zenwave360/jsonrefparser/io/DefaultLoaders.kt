package io.zenwave360.jsonrefparser.io

import io.zenwave360.jsonrefparser.model.AuthenticationValue

actual fun defaultLoaders(auth: List<AuthenticationValue>): List<DocumentLoader> =
    listOf(NodeFsLoader(), FetchLoader(auth))
