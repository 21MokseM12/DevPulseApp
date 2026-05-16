package com.devpulse.app.ui.subscriptions

import java.net.URI

fun formatLinkDisplayName(url: String): String {
    val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return url
    val host = uri.host?.lowercase() ?: return url

    return when {
        host == "github.com" || host.endsWith(".github.com") -> formatGithubUrl(uri, url)
        host == "stackoverflow.com" || host.endsWith(".stackoverflow.com") -> formatStackOverflowUrl(uri, url)
        else -> url
    }
}

private fun formatGithubUrl(
    uri: URI,
    fallback: String,
): String {
    val segments =
        uri.path
            .split("/")
            .filter { it.isNotBlank() }
    return when {
        segments.size >= 2 -> "${segments[0]} ${segments[1]}"
        segments.size == 1 -> segments[0]
        else -> fallback
    }
}

private fun formatStackOverflowUrl(
    uri: URI,
    fallback: String,
): String {
    val segments =
        uri.path
            .split("/")
            .filter { it.isNotBlank() }
    val slug = segments.lastOrNull() ?: return fallback
    if (slug.all { it.isDigit() }) return fallback
    return slug.replace('-', ' ')
}
