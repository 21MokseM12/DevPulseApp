package com.devpulse.app.ui.updates

import java.net.URI

internal fun resolveNavigableUpdateLink(rawUrl: String): String? {
    val normalized = rawUrl.trim()
    if (normalized.isEmpty()) {
        return null
    }

    parseHttpUri(normalized)?.let { return it }
    if (hasAnyScheme(normalized)) {
        return null
    }

    // Backend may return links without scheme (example.com/path).
    val httpsFallback = "https://$normalized"
    return parseHttpUri(httpsFallback)
}

private fun hasAnyScheme(candidate: String): Boolean {
    val uri = runCatching { URI(candidate) }.getOrNull() ?: return false
    return !uri.scheme.isNullOrBlank()
}

private fun parseHttpUri(candidate: String): String? {
    val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null
    if (scheme != "http" && scheme != "https") {
        return null
    }
    if (uri.host.isNullOrBlank()) {
        return null
    }
    return candidate
}
