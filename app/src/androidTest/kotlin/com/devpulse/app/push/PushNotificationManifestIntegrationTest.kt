package com.devpulse.app.push

import android.Manifest
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

class PushNotificationManifestIntegrationTest {
    @Test
    fun installedAppRequestsPostNotificationsPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageInfo = context.packageManager.readPackageInfoWithPermissions(context.packageName)
        val requestedPermissions = packageInfo.requestedPermissions?.toSet().orEmpty()

        assertTrue(
            "Установленный пакет должен запрашивать POST_NOTIFICATIONS",
            requestedPermissions.contains(Manifest.permission.POST_NOTIFICATIONS),
        )
    }
}

private fun PackageManager.readPackageInfoWithPermissions(packageName: String): PackageInfo {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(
            packageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
        )
    } else {
        @Suppress("DEPRECATION")
        getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
    }
}
