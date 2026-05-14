package com.devpulse.app.push

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import com.devpulse.app.data.local.preferences.NotificationDigestMode
import com.devpulse.app.data.local.preferences.NotificationPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkManagerDigestSchedulerIntegrationTest {
    @Test
    fun sync_digestDisabled_cancelsUniqueWork() {
        val gateway = RecordingGateway()
        val scheduler = createScheduler(gateway)

        scheduler.sync(NotificationPreferences(enabled = true, digestMode = null))

        assertEquals(listOf(DIGEST_WORK_NAME), gateway.cancelCalls)
        assertTrue(gateway.enqueueCalls.isEmpty())
    }

    @Test
    fun sync_digestEnabled_enqueuesUniqueWorkWithUpdatePolicy() {
        val gateway = RecordingGateway()
        val scheduler = createScheduler(gateway)

        scheduler.sync(
            NotificationPreferences(
                enabled = true,
                digestMode = NotificationDigestMode.Hourly,
            ),
        )

        assertEquals(1, gateway.enqueueCalls.size)
        assertEquals(DIGEST_WORK_NAME, gateway.enqueueCalls.single().workName)
        assertEquals(ExistingPeriodicWorkPolicy.UPDATE, gateway.enqueueCalls.single().policy)
    }

    @Test
    fun sync_afterReboot_enqueuesSameUniqueWorkAgain() {
        val gateway = RecordingGateway()
        val schedulerBeforeReboot = WorkManagerDigestScheduler(gateway)
        val schedulerAfterReboot = WorkManagerDigestScheduler(gateway)
        val preferences =
            NotificationPreferences(
                enabled = true,
                digestMode = NotificationDigestMode.Daily,
            )

        schedulerBeforeReboot.sync(preferences)
        schedulerAfterReboot.sync(preferences)

        assertEquals(2, gateway.enqueueCalls.size)
        assertTrue(gateway.enqueueCalls.all { it.workName == DIGEST_WORK_NAME })
        assertTrue(gateway.enqueueCalls.all { it.policy == ExistingPeriodicWorkPolicy.UPDATE })
    }

    private fun createScheduler(gateway: RecordingGateway): WorkManagerDigestScheduler {
        return WorkManagerDigestScheduler(gateway)
    }

    private class RecordingGateway : DigestWorkManagerGateway {
        val cancelCalls = mutableListOf<String>()
        val enqueueCalls = mutableListOf<EnqueueCall>()

        override fun cancelUniqueWork(workName: String) {
            cancelCalls += workName
        }

        override fun enqueueUniquePeriodicWork(
            workName: String,
            policy: ExistingPeriodicWorkPolicy,
            request: PeriodicWorkRequest,
        ) {
            enqueueCalls += EnqueueCall(workName = workName, policy = policy, request = request)
        }
    }

    private data class EnqueueCall(
        val workName: String,
        val policy: ExistingPeriodicWorkPolicy,
        val request: PeriodicWorkRequest,
    )
}
