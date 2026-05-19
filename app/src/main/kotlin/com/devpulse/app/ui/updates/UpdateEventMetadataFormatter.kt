package com.devpulse.app.ui.updates

import com.devpulse.app.domain.model.UpdateEvent
import java.net.URI

internal fun buildUpdateEventMetadata(event: UpdateEvent): List<String> {
    val lines = mutableListOf<String>()
    val author = event.source.trim().takeIf { it.isNotEmpty() }
    if (author != null) {
        lines += "Автор: $author"
    }

    val uri = runCatching { URI(event.linkUrl.trim()) }.getOrNull()
    val host = uri?.host?.lowercase()
    when {
        host == null -> Unit
        host == "github.com" || host.endsWith(".github.com") -> {
            extractGithubRepositoryName(uri)?.let { repository ->
                lines += "Репозиторий: $repository"
            }
        }
        host == "stackoverflow.com" || host.endsWith(".stackoverflow.com") -> {
            extractStackOverflowQuestionTitle(uri)?.let { questionTitle ->
                lines += "Вопрос: $questionTitle"
            }
        }
    }

    return lines
}

private fun extractGithubRepositoryName(uri: URI): String? {
    val segments = uri.path.split("/").filter { it.isNotBlank() }
    if (segments.size < 2) return null
    return "${segments[0]}/${segments[1]}"
}

private fun extractStackOverflowQuestionTitle(uri: URI): String? {
    val segments = uri.path.split("/").filter { it.isNotBlank() }
    val questionsIndex = segments.indexOf("questions")
    if (questionsIndex == -1) return null
    val slug = segments.getOrNull(questionsIndex + 2)?.takeIf { it.isNotBlank() } ?: return null
    if (slug.all(Char::isDigit)) return null
    return slug.replace('-', ' ')
}
