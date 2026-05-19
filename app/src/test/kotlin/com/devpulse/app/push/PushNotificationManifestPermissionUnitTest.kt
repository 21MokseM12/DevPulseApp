package com.devpulse.app.push

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class PushNotificationManifestPermissionUnitTest {
    @Test
    fun manifest_declaresPostNotificationsPermission() {
        val manifestContent = String(Files.readAllBytes(resolveManifestPath()))

        assertTrue(
            "AndroidManifest должен содержать разрешение POST_NOTIFICATIONS",
            manifestContent.contains("<uses-permission android:name=\"android.permission.POST_NOTIFICATIONS\""),
        )
    }

    private fun resolveManifestPath(): Path {
        val modulePath = Paths.get("src/main/AndroidManifest.xml")
        if (Files.exists(modulePath)) return modulePath

        val projectPath = Paths.get("app/src/main/AndroidManifest.xml")
        if (Files.exists(projectPath)) return projectPath

        error("Не удалось найти AndroidManifest.xml для проверки разрешений")
    }
}
