package com.devpulse.app.push

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.devpulse.app.data.local.preferences.NotificationDigestMode
import com.devpulse.app.data.local.preferences.NotificationPreferences
import com.devpulse.app.data.local.preferences.NotificationPreferencesStore
import com.devpulse.app.data.local.preferences.intervalMinutes
import com.devpulse.app.domain.repository.UpdatesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

interface DigestScheduler {
    fun sync(preferences: NotificationPreferences)
}

@Singleton
class WorkManagerDigestScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : DigestScheduler {
        private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }

        override fun sync(preferences: NotificationPreferences) {
            val mode = preferences.digestMode
            if (!preferences.enabled || mode == null) {
                workManager.cancelUniqueWork(DIGEST_WORK_NAME)
                return
            }
            val repeatMinutes = digestRepeatMinutes(mode)
            val flexMinutes = digestFlexMinutes(repeatMinutes)
            val request =
                PeriodicWorkRequestBuilder<DigestWorker>(
                    repeatInterval = repeatMinutes,
                    repeatIntervalTimeUnit = TimeUnit.MINUTES,
                    flexTimeInterval = flexMinutes,
                    flexTimeIntervalUnit = TimeUnit.MINUTES,
                ).build()
            workManager.enqueueUniquePeriodicWork(
                DIGEST_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }

class DigestWorker
    constructor(
        appContext: Context,
        workerParams: WorkerParameters,
    ) : CoroutineWorker(appContext, workerParams) {
        override suspend fun doWork(): Result {
            val entryPoint =
                EntryPointAccessors.fromApplication(
                    applicationContext,
                    DigestWorkerDependencies::class.java,
                )
            val updatesRepository = entryPoint.updatesRepository()
            val notificationPreferencesStore = entryPoint.notificationPreferencesStore()
            val digestUpdateAggregator = entryPoint.digestUpdateAggregator()
            val pushNotifier = entryPoint.pushNotifier()
            val preferences =
                runCatching { notificationPreferencesStore.getPreferences() }
                    .getOrElse { return Result.retry() }
            val digestMode = preferences.digestMode ?: return Result.success()
            if (!preferences.enabled) return Result.success()

            val now = System.currentTimeMillis()
            val updates =
                runCatching { updatesRepository.observeUpdates().first() }
                    .getOrElse { return Result.retry() }
            val summary =
                digestUpdateAggregator.aggregate(
                    updates = updates,
                    periodStartExclusiveEpochMs = preferences.digestLastProcessedAtEpochMs,
                    periodEndInclusiveEpochMs = now,
                )
            if (summary != null) {
                pushNotifier.showDigestNotification(
                    summary = summary,
                    digestMode = digestMode,
                )
            }
            runCatching {
                notificationPreferencesStore.setDigestLastProcessedAt(now)
            }.getOrElse {
                return Result.retry()
            }
            return Result.success()
        }
    }

const val DIGEST_WORK_NAME = "devpulse_digest_dispatch_work"

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DigestWorkerDependencies {
    fun updatesRepository(): UpdatesRepository

    fun notificationPreferencesStore(): NotificationPreferencesStore

    fun digestUpdateAggregator(): DigestUpdateAggregator

    fun pushNotifier(): PushNotifier
}

internal fun digestRepeatMinutes(mode: NotificationDigestMode): Long {
    return mode.intervalMinutes
}

internal fun digestFlexMinutes(repeatMinutes: Long): Long {
    return max(15L, repeatMinutes / 4L)
}
