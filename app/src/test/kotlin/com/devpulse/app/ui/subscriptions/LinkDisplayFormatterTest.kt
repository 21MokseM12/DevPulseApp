package com.devpulse.app.ui.subscriptions

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkDisplayFormatterTest {
    // GitHub

    @Test
    fun github_ownerAndRepo_formatsWithoutSlash() {
        assertEquals(
            "torvalds linux",
            formatLinkDisplayName("https://github.com/torvalds/linux"),
        )
    }

    @Test
    fun github_ownerRepoWithSubpath_showsOnlyOwnerAndRepo() {
        assertEquals(
            "octocat Hello-World",
            formatLinkDisplayName("https://github.com/octocat/Hello-World/issues/123"),
        )
    }

    @Test
    fun github_onlyOwner_showsOwner() {
        assertEquals(
            "torvalds",
            formatLinkDisplayName("https://github.com/torvalds"),
        )
    }

    @Test
    fun github_subdomain_gist_formatsCorrectly() {
        assertEquals(
            "user abc123",
            formatLinkDisplayName("https://gist.github.com/user/abc123"),
        )
    }

    @Test
    fun github_emptyPath_fallsBackToUrl() {
        val url = "https://github.com/"
        assertEquals(url, formatLinkDisplayName(url))
    }

    @Test
    fun github_httpScheme_formatsCorrectly() {
        assertEquals(
            "owner repo",
            formatLinkDisplayName("http://github.com/owner/repo"),
        )
    }

    // StackOverflow

    @Test
    fun stackoverflow_questionWithSlug_showsSlugWithSpaces() {
        assertEquals(
            "how to center a div in css",
            formatLinkDisplayName("https://stackoverflow.com/questions/356897/how-to-center-a-div-in-css"),
        )
    }

    @Test
    fun stackoverflow_questionWithoutSlug_fallsBackToUrl() {
        val url = "https://stackoverflow.com/questions/356897"
        assertEquals(url, formatLinkDisplayName(url))
    }

    @Test
    fun stackoverflow_rootPath_fallsBackToUrl() {
        val url = "https://stackoverflow.com/"
        assertEquals(url, formatLinkDisplayName(url))
    }

    @Test
    fun stackoverflow_subdomain_formatsCorrectly() {
        assertEquals(
            "what is the difference between var and let",
            formatLinkDisplayName(
                "https://meta.stackoverflow.com/questions/123/what-is-the-difference-between-var-and-let",
            ),
        )
    }

    @Test
    fun stackoverflow_slugWithoutDashes_returnsSlugAsIs() {
        assertEquals(
            "kotlinnullpointerexception",
            formatLinkDisplayName("https://stackoverflow.com/questions/999/kotlinnullpointerexception"),
        )
    }

    // Other domains

    @Test
    fun otherDomain_returnsOriginalUrl() {
        val url = "https://example.com/some/path"
        assertEquals(url, formatLinkDisplayName(url))
    }

    @Test
    fun invalidUrl_returnsOriginalString() {
        val url = "not-a-url"
        assertEquals(url, formatLinkDisplayName(url))
    }
}
