package com.devpulse.app.push

import android.content.Context
import android.provider.Settings
import com.devpulse.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class PushTokenMetadata(
    val appVersion: String,
    val deviceId: String?,
)

@Singleton
class PushTokenMetadataProvider
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : PushTokenMetadataSource {
        override fun getMetadata(): PushTokenMetadata {
            return PushTokenMetadata(
                appVersion = BuildConfig.VERSION_NAME,
                deviceId = resolveDeviceId(),
            )
        }

        private fun resolveDeviceId(): String? {
            return runCatching {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull()
        }
    }

interface PushTokenMetadataSource {
    fun getMetadata(): PushTokenMetadata
}
