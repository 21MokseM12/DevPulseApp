package com.devpulse.app.push

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushAnalyticsLogger
    @Inject
    constructor() : PushAnalyticsTracker {
        override fun tokenRegistered(
            statusCode: Int,
            reason: String,
            source: String,
        ) {
            Log.i(
                LOG_TAG,
                "analytics_event=token_registered status_code=$statusCode reason=$reason source=$source",
            )
        }

        override fun tokenRefresh(source: String) {
            Log.i(LOG_TAG, "analytics_event=token_refresh source=$source")
        }

        override fun pushReceived(
            result: PushHandleResult,
            messageId: String?,
        ) {
            Log.i(
                LOG_TAG,
                "analytics_event=push_received result=${result.name.lowercase()} message_id=$messageId",
            )
        }

        override fun pushOpened(
            eventId: String?,
            url: String?,
        ) {
            Log.i(
                LOG_TAG,
                "analytics_event=push_opened event_id=$eventId has_url=${!url.isNullOrBlank()}",
            )
        }

        private companion object {
            const val LOG_TAG = "PushAnalytics"
        }
    }

interface PushAnalyticsTracker {
    fun tokenRegistered(
        statusCode: Int,
        reason: String,
        source: String,
    )

    fun tokenRefresh(source: String)

    fun pushReceived(
        result: PushHandleResult,
        messageId: String?,
    )

    fun pushOpened(
        eventId: String?,
        url: String?,
    )
}
