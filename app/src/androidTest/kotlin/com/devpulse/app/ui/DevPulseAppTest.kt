package com.devpulse.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.devpulse.app.MainActivity
import com.devpulse.app.testing.FakeSmokeRemoteDataSource
import com.devpulse.app.ui.testing.SmokeTestTags
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltAndroidTest
class DevPulseAppTest {
    private val hiltRule = HiltAndroidRule(this)
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hiltRule).around(composeRule)

    @Inject
    lateinit var smokeDataSource: FakeSmokeRemoteDataSource

    @Before
    fun setUp() {
        hiltRule.inject()
        smokeDataSource.reset()
    }

    @Test
    fun coldStart_opensAuthRoute() {
        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.AUTH_TITLE)
        captureScreenshot("smoke_cold_start_auth", SmokeTestTags.AUTH_TITLE)
    }

    @Test
    fun authToSubscriptions_happyPath_isStable() {
        login()
        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.SUBSCRIPTIONS_TITLE)
        captureScreenshot("smoke_auth_to_subscriptions", SmokeTestTags.SUBSCRIPTIONS_TITLE)
    }

    @Test
    fun addAndRemoveSubscription_flowWorks() {
        val smokeUrl = "https://example.dev/smoke-case"

        login()
        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.SUBSCRIPTIONS_TITLE)
        composeRule.onNodeWithTag(SmokeTestTags.SUBSCRIPTIONS_LINK_INPUT).performTextInput(smokeUrl)
        composeRule.onNodeWithTag(SmokeTestTags.SUBSCRIPTIONS_TAGS_INPUT).performTextInput("android,ci")
        composeRule.onNodeWithTag(SmokeTestTags.SUBSCRIPTIONS_FILTERS_INPUT).performTextInput("news")
        composeRule.onNodeWithTag(SmokeTestTags.SUBSCRIPTIONS_ADD_BUTTON).performClick()
        composeRule.waitUntilNodeWithTextExists(smokeUrl)

        composeRule.onNodeWithTag(SmokeTestTags.subscriptionRemoveButton(2L)).performClick()
        composeRule.onNodeWithTag(SmokeTestTags.SUBSCRIPTIONS_REMOVE_CONFIRM_BUTTON).performClick()
        composeRule.waitUntilNodeWithTextMissing(smokeUrl)
        captureScreenshot("smoke_add_remove_subscription", SmokeTestTags.SUBSCRIPTIONS_TITLE)
    }

    @Test
    fun updatesMarkRead_changesUnreadCounter() {
        login()
        composeRule.onNodeWithTag(SmokeTestTags.SUBSCRIPTIONS_OPEN_UPDATES_BUTTON).performClick()
        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.UPDATES_TITLE)
        composeRule.waitUntilNodeWithTextExists("Непрочитанных: 1")

        composeRule.onNodeWithTag(SmokeTestTags.updateMarkReadButton(1001L)).performClick()
        composeRule.waitUntilNodeWithTextExists("Непрочитанных: 0")
        captureScreenshot("smoke_updates_mark_read", SmokeTestTags.UPDATES_TITLE)
    }

    private fun login() {
        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.AUTH_TITLE)
        composeRule.onNodeWithTag(SmokeTestTags.AUTH_LOGIN_INPUT).performTextInput("moksem")
        composeRule.onNodeWithTag(SmokeTestTags.AUTH_PASSWORD_INPUT).performTextInput("secret")
        composeRule.onNodeWithTag(SmokeTestTags.AUTH_SUBMIT_BUTTON).performClick()
    }

    private fun captureScreenshot(
        name: String,
        anchorTag: String,
    ) {
        composeRule.waitForIdle()
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val screenshotsDir =
            requireNotNull(targetContext.getExternalFilesDir("smoke-screenshots")) {
                "Unable to resolve external files directory"
            }
        val screenshotFile = File(screenshotsDir, "$name.png")
        FileOutputStream(screenshotFile).use { stream ->
            composeRule
                .onNodeWithTag(anchorTag)
                .captureToImage()
                .asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }
}

private fun ComposeContentTestRule.waitUntilNodeWithTagExists(tag: String) {
    waitUntil(timeoutMillis = 5_000) {
        onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
}

private fun ComposeContentTestRule.waitUntilNodeWithTextExists(text: String) {
    waitUntil(timeoutMillis = 5_000) {
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
}

private fun ComposeContentTestRule.waitUntilNodeWithTextMissing(text: String) {
    waitUntil(timeoutMillis = 5_000) {
        onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
    }
}
