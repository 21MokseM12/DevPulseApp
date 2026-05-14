package com.devpulse.app.push

import com.devpulse.app.data.local.preferences.NotificationDigestMode

/**
 * Явный продуктовый контракт digest-доставки (R-2.1):
 * - при включенном digest instant-уведомления не доставляются;
 * - digest группирует обновления по нормализованному источнику;
 * - в тексте digest показываем ограниченное число групп источников.
 */
object DigestDeliveryContract {
    const val UNKNOWN_SOURCE = "unknown"
    const val MAX_SOURCES_IN_DIGEST_BODY = 3

    fun shouldDeliverInstantNotification(digestMode: NotificationDigestMode?): Boolean = digestMode == null

    fun normalizeSource(rawSource: String): String {
        return rawSource.trim().lowercase().ifBlank { UNKNOWN_SOURCE }
    }

    fun topSourcesForDigestBody(sourceBreakdown: Map<String, Int>): List<Map.Entry<String, Int>> {
        return sourceBreakdown.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(MAX_SOURCES_IN_DIGEST_BODY)
    }
}
