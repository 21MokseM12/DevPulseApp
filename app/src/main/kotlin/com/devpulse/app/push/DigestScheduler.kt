package com.devpulse.app.push

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.PeriodicWorkRequest
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
    private constructor(
        private val context: Context?,
        private val gatewayOverride: DigestWorkManagerGateway?,
    ) : DigestScheduler {
        @Inject
        constructor(
            @ApplicationContext context: Context,
        ) : this(context = context, gatewayOverride = null)

        internal constructor(gateway: DigestWorkManagerGateway) : this(context = null, gatewayOverride = gateway)

        private val gateway: DigestWorkManagerGateway by lazy {
            gatewayOverride ?: AndroidDigestWorkManagerGatewayFactory.create(requireNotNull(context))
        }

        override fun sync(preferences: NotificationPreferences) {
            val mode = preferences.digestMode
            if (!preferences.enabled || mode == null) {
                gateway.cancelUniqueWork(DIGEST_WORK_NAME)
                return
            }
            val repeatMinutes = digestRepeatMinutes(mode)
            val flexMinutes = digestFlexMinutes(repeatMinutes)
            gateway.enqueueUniquePeriodicWork(
                DIGEST_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                buildDigestWorkRequest(
                    repeatMinutes = repeatMinutes,
                    flexMinutes = flexMinutes,
                ),
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
            return runDigestWorkerCycle(
                updatesRepository = entryPoint.updatesRepository(),
                notificationPreferencesStore = entryPoint.notificationPreferencesStore(),
                digestUpdateAggregator = entryPoint.digestUpdateAggregator(),
                pushNotifier = entryPoint.pushNotifier(),
            )
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

internal suspend fun runDigestWorkerCycle(
    updatesRepository: UpdatesRepository,
    notificationPreferencesStore: NotificationPreferencesStore,
    digestUpdateAggregator: DigestUpdateAggregator,
    pushNotifier: PushNotifier,
    nowEpochMs: Long = System.currentTimeMillis(),
): ListenableWorker.Result {
    val preferences =
        runCatching { notificationPreferencesStore.getPreferences() }
            .getOrElse { return ListenableWorker.Result.retry() }
    val digestMode = preferences.digestMode ?: return ListenableWorker.Result.success()
    if (!preferences.enabled) return ListenableWorker.Result.success()

    val updates =
        runCatching { updatesRepository.observeUpdates().first() }
            .getOrElse { return ListenableWorker.Result.retry() }
    val summary =
        digestUpdateAggregator.aggregate(
            updates = updates,
            periodStartExclusiveEpochMs = preferences.digestLastProcessedAtEpochMs,
            periodEndInclusiveEpochMs = nowEpochMs,
        )
    if (summary != null) {
        pushNotifier.showDigestNotification(
            summary = summary,
            digestMode = digestMode,
        )
    }
    runCatching {
        notificationPreferencesStore.setDigestLastProcessedAt(nowEpochMs)
    }.getOrElse {
        return ListenableWorker.Result.retry()
    }
    return ListenableWorker.Result.success()
}

internal fun buildDigestWorkRequest(
    repeatMinutes: Long,
    flexMinutes: Long,
): PeriodicWorkRequest {
    return PeriodicWorkRequestBuilder<DigestWorker>(
        repeatInterval = repeatMinutes,
        repeatIntervalTimeUnit = TimeUnit.MINUTES,
        flexTimeInterval = flexMinutes,
        flexTimeIntervalUnit = TimeUnit.MINUTES,
    ).build()
}

internal interface DigestWorkManagerGateway {
    fun cancelUniqueWork(workName: String)

    fun enqueueUniquePeriodicWork(
        workName: String,
        policy: ExistingPeriodicWorkPolicy,
        request: PeriodicWorkRequest,
    )
}

internal fun interface DigestWorkManagerGatewayFactory {
    fun create(context: Context): DigestWorkManagerGateway
}

internal object AndroidDigestWorkManagerGatewayFactory : DigestWorkManagerGatewayFactory {
    override fun create(context: Context): DigestWorkManagerGateway {
        val workManager = WorkManager.getInstance(context)
        return object : DigestWorkManagerGateway {
            override fun cancelUniqueWork(workName: String) {
                workManager.cancelUniqueWork(workName)
            }

            override fun enqueueUniquePeriodicWork(
                workName: String,
                policy: ExistingPeriodicWorkPolicy,
                request: PeriodicWorkRequest,
            ) {
                workManager.enqueueUniquePeriodicWork(workName, policy, request)
            }
        }
    }
}
