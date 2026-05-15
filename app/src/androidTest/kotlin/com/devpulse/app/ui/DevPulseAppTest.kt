package com.devpulse.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.devpulse.app.MainActivity
import com.devpulse.app.data.remote.dto.NotificationDto
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
        composeRule.onNodeWithTag(SmokeTestTags.AUTH_LOGIN_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(SmokeTestTags.AUTH_REGISTER_BUTTON).assertIsDisplayed()
        captureScreenshot("smoke_cold_start_auth", SmokeTestTags.AUTH_TITLE)
    }

    @Test
    fun authActions_areDistinct_andLockedDuringLoading() {
        smokeDataSource.setRegisterDelayForTesting(delayMs = 1_500L)

        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.AUTH_TITLE)
        composeRule.onNodeWithTag(SmokeTestTags.AUTH_LOGIN_INPUT).performTextInput("moksem")
        composeRule.onNodeWithTag(SmokeTestTags.AUTH_PASSWORD_INPUT).performTextInput("secret")

        composeRule.onNodeWithTag(SmokeTestTags.AUTH_LOGIN_BUTTON).performClick()
        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.AUTH_LOGIN_LOADER)
        composeRule.waitUntilNodeWithTextExists("Входим...")
        composeRule.onNodeWithTag(SmokeTestTags.AUTH_LOGIN_BUTTON).assertIsNotEnabled()
        composeRule.onNodeWithTag(SmokeTestTags.AUTH_REGISTER_BUTTON).assertIsNotEnabled()
        composeRule.onNodeWithText("Зарегистрироваться").assertIsDisplayed()

        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.SUBSCRIPTIONS_TITLE)
    }

    @Test
    fun authValidation_blankCredentials_showsLoginValidationState() {
        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.AUTH_TITLE)

        composeRule.onNodeWithTag(SmokeTestTags.AUTH_LOGIN_BUTTON).performClick()

        composeRule.waitUntilNodeWithTextExists("Для входа заполните логин и пароль.")
        composeRule.waitUntilNodeWithTextExists("Повторить вход")
        composeRule.onNodeWithText("Зарегистрироваться").assertIsDisplayed()
    }

    @Test
    fun authRegisterFailure_showsRegisterErrorButtonText() {
        smokeDataSource.setRegisterFailureForTesting(message = "Пользователь уже существует")

        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.AUTH_TITLE)
        composeRule.onNodeWithTag(SmokeTestTags.AUTH_LOGIN_INPUT).performTextInput("moksem")
        composeRule.onNodeWithTag(SmokeTestTags.AUTH_PASSWORD_INPUT).performTextInput("secret")

        composeRule.onNodeWithTag(SmokeTestTags.AUTH_REGISTER_BUTTON).performClick()

        composeRule.waitUntilNodeWithTextExists("Повторить регистрацию")
        composeRule.waitUntilNodeWithTextExists("Не удалось зарегистрироваться. Пользователь уже существует")
    }

    @Test
    fun authToSubscriptions_happyPath_isStable() {
        login()
        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.SUBSCRIPTIONS_TITLE)
        captureScreenshot("smoke_auth_to_subscriptions", SmokeTestTags.SUBSCRIPTIONS_TITLE)
    }

    @Test
    fun registerSuccess_navigatesToSubscriptions_smoke() {
        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.AUTH_TITLE)
        fillAuthCredentials(login = "moksem", password = "secret")
        composeRule.onNodeWithTag(SmokeTestTags.AUTH_REGISTER_BUTTON).performClick()

        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.SUBSCRIPTIONS_TITLE)
        composeRule.waitUntilNodeWithTagMissing(SmokeTestTags.AUTH_TITLE)
    }

    @Test
    fun authSuccess_clearsAuthFromBackStack() {
        login()
        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.SUBSCRIPTIONS_TITLE)

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }

        composeRule.waitUntilNodeWithTagMissing(SmokeTestTags.AUTH_TITLE)
    }

    @Test
    fun authRequest_duringRotation_isCancelledAndStaysOnAuth() {
        smokeDataSource.setRegisterDelayForTesting(delayMs = 1_500L)

        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.AUTH_TITLE)
        fillAuthCredentials(login = "moksem", password = "secret")
        composeRule.onNodeWithTag(SmokeTestTags.AUTH_LOGIN_BUTTON).performClick()
        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.AUTH_LOGIN_LOADER)

        composeRule.activityRule.scenario.recreate()

        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.AUTH_TITLE)
        composeRule.waitUntilNodeWithTagMissing(SmokeTestTags.AUTH_LOGIN_LOADER)
        composeRule.onNodeWithText("Войти").assertIsDisplayed()
    }

    @Test
    fun authRequest_afterBackgroundResume_isCancelledAndStaysOnAuth() {
        smokeDataSource.setRegisterDelayForTesting(delayMs = 1_500L)

        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.AUTH_TITLE)
        fillAuthCredentials(login = "moksem", password = "secret")
        composeRule.onNodeWithTag(SmokeTestTags.AUTH_LOGIN_BUTTON).performClick()
        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.AUTH_LOGIN_LOADER)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.AUTH_TITLE)
        composeRule.waitUntilNodeWithTagMissing(SmokeTestTags.AUTH_LOGIN_LOADER)
        composeRule.onNodeWithText("Войти").assertIsDisplayed()
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
    fun subscriptionsSearch_filtersAndResetResultList() {
        val kotlinUrl = "https://example.dev/kotlin-news"
        val backendUrl = "https://example.dev/backend-feed"

        login()
        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.SUBSCRIPTIONS_TITLE)

        composeRule.onNodeWithTag(SmokeTestTags.SUBSCRIPTIONS_LINK_INPUT).performTextInput(kotlinUrl)
        composeRule.onNodeWithTag(SmokeTestTags.SUBSCRIPTIONS_TAGS_INPUT).performTextInput("kotlin,news")
        composeRule.onNodeWithTag(SmokeTestTags.SUBSCRIPTIONS_FILTERS_INPUT).performTextInput("contains:kotlin")
        composeRule.onNodeWithTag(SmokeTestTags.SUBSCRIPTIONS_ADD_BUTTON).performClick()
        composeRule.waitUntilNodeWithTextExists(kotlinUrl)

        composeRule.onNodeWithTag(SmokeTestTags.SUBSCRIPTIONS_LINK_INPUT).performTextInput(backendUrl)
        composeRule.onNodeWithTag(SmokeTestTags.SUBSCRIPTIONS_TAGS_INPUT).performTextInput("backend")
        composeRule.onNodeWithTag(SmokeTestTags.SUBSCRIPTIONS_FILTERS_INPUT).performTextInput("contains:alerts")
        composeRule.onNodeWithTag(SmokeTestTags.SUBSCRIPTIONS_ADD_BUTTON).performClick()
        composeRule.waitUntilNodeWithTextExists(backendUrl)

        composeRule.onNodeWithTag(SmokeTestTags.SUBSCRIPTIONS_SEARCH_INPUT).performTextInput("tag:kotlin")
        composeRule.waitUntilNodeWithTextExists(kotlinUrl)
        composeRule.waitUntilNodeWithTextMissing(backendUrl)

        composeRule.onNodeWithTag(SmokeTestTags.SUBSCRIPTIONS_CLEAR_SEARCH_BUTTON).performClick()
        composeRule.waitUntilNodeWithTextExists(kotlinUrl)
        composeRule.waitUntilNodeWithTextExists(backendUrl)
    }

    @Test
    fun subscriptionsSearch_activeFilters_keepAddAndRemoveStable() {
        val kotlinUrl = "https://example.dev/kotlin-active"
        val backendUrl = "https://example.dev/backend-active"
        val kotlinSecondUrl = "https://example.dev/kotlin-active-second"

        login()
        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.SUBSCRIPTIONS_TITLE)

        composeRule.onNodeWithTag(SmokeTestTags.SUBSCRIPTIONS_LINK_INPUT).performTextInput(kotlinUrl)
        composeRule.onNodeWithTag(SmokeTestTags.SUBSCRIPTIONS_TAGS_INPUT).performTextInput("kotlin,news")
        composeRule.onNodeWithTag(SmokeTestTags.SUBSCRIPTIONS_FILTERS_INPUT).performTextInput("contains:kotlin")
        composeRule.onNodeWithTag(SmokeTestTags.SUBSCRIPTIONS_ADD_BUTTON).performClick()
        composeRule.waitUntilNodeWithTextExists(kotlinUrl)

        composeRule.onNodeWithTag(SmokeTestTags.SUBSCRIPTIONS_LINK_INPUT).performTextInput(backendUrl)
        composeRule.onNodeWithTag(SmokeTestTags.SUBSCRIPTIONS_TAGS_INPUT).performTextInput("backend")
        composeRule.onNodeWithTag(SmokeTestTags.SUBSCRIPTIONS_FILTERS_INPUT).performTextInput("contains:alerts")
        composeRule.onNodeWithTag(SmokeTestTags.SUBSCRIPTIONS_ADD_BUTTON).performClick()
        composeRule.waitUntilNodeWithTextExists(backendUrl)

        composeRule.onNodeWithTag(SmokeTestTags.SUBSCRIPTIONS_PRESET_WITH_FILTERS).performClick()
        composeRule.onNodeWithTag(SmokeTestTags.SUBSCRIPTIONS_SEARCH_INPUT).performTextInput("tag:kotlin")
        composeRule.waitUntilNodeWithTextExists(kotlinUrl)
        composeRule.waitUntilNodeWithTextMissing(backendUrl)

        composeRule.onNodeWithTag(SmokeTestTags.SUBSCRIPTIONS_LINK_INPUT).performTextInput(kotlinSecondUrl)
        composeRule.onNodeWithTag(SmokeTestTags.SUBSCRIPTIONS_TAGS_INPUT).performTextInput("kotlin,mobile")
        composeRule.onNodeWithTag(SmokeTestTags.SUBSCRIPTIONS_FILTERS_INPUT).performTextInput("contains:kotlin")
        composeRule.onNodeWithTag(SmokeTestTags.SUBSCRIPTIONS_ADD_BUTTON).performClick()
        composeRule.waitUntilNodeWithTextExists(kotlinSecondUrl)
        composeRule.waitUntilNodeWithTextMissing(backendUrl)

        composeRule.onAllNodesWithText("Удалить").onFirst().performClick()
        composeRule.onNodeWithTag(SmokeTestTags.SUBSCRIPTIONS_REMOVE_CONFIRM_BUTTON).performClick()
        composeRule.waitUntilAnyTextExists(kotlinUrl, kotlinSecondUrl)
        composeRule.waitUntilNotAllTextsExist(kotlinUrl, kotlinSecondUrl)
        composeRule.waitUntilNodeWithTextExists(kotlinUrl)
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

    @Test
    fun updatesEmptyState_showsMicrocopy() {
        smokeDataSource.setNotificationsForTesting(emptyList())

        login()
        openUpdates()
        composeRule.waitUntilNodeWithTextExists("Пока нет событий")
        composeRule.waitUntilNodeWithTextExists("Новые уведомления из Bot API появятся здесь")
    }

    @Test
    fun updatesNoResultsAndReset_restoresFeed() {
        smokeDataSource.setNotificationsForTesting(
            listOf(
                NotificationDto(
                    id = 2001L,
                    title = "Backend release",
                    description = "Prod deploy",
                    url = "https://devpulse.app/backend",
                    unread = true,
                    linkId = 301L,
                    receivedAt = "2026-05-14T10:00:00Z",
                    readAt = null,
                ),
                NotificationDto(
                    id = 2002L,
                    title = "Frontend release",
                    description = "UI deploy",
                    url = "https://devpulse.app/frontend",
                    unread = true,
                    linkId = 302L,
                    receivedAt = "2026-05-14T11:00:00Z",
                    readAt = null,
                ),
            ),
        )

        login()
        openUpdates()
        composeRule.onNodeWithTag(SmokeTestTags.UPDATES_SEARCH_INPUT).performTextInput("missing-token")
        composeRule.waitUntilNodeWithTextExists("Ничего не найдено")
        composeRule.waitUntilNodeWithTextExists("Измените фильтры или сбросьте их")

        composeRule.onNodeWithTag(SmokeTestTags.UPDATES_RESET_FILTERS_BUTTON).performClick()
        composeRule.waitUntilNodeWithTextExists("Backend release")
        composeRule.waitUntilNodeWithTextExists("Frontend release")
    }

    @Test
    fun updatesControls_haveMinimumTapTarget() {
        login()
        openUpdates()

        composeRule.onNodeWithText("Quick: unread").performClick()
        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.UPDATES_RESET_FILTERS_BUTTON)

        composeRule
            .onNodeWithText("Quick: unread")
            .assertHasClickAction()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule
            .onNodeWithText("Непрочитанные")
            .assertHasClickAction()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule
            .onNodeWithTag(SmokeTestTags.UPDATES_RESET_FILTERS_BUTTON)
            .assertHasClickAction()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    private fun login() {
        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.AUTH_TITLE)
        fillAuthCredentials(login = "moksem", password = "secret")
        composeRule.onNodeWithTag(SmokeTestTags.AUTH_LOGIN_BUTTON).performClick()
    }

    private fun fillAuthCredentials(
        login: String,
        password: String,
    ) {
        composeRule.onNodeWithTag(SmokeTestTags.AUTH_LOGIN_INPUT).performTextInput(login)
        composeRule.onNodeWithTag(SmokeTestTags.AUTH_PASSWORD_INPUT).performTextInput(password)
    }

    private fun openUpdates() {
        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.SUBSCRIPTIONS_OPEN_UPDATES_BUTTON)
        composeRule.onNodeWithTag(SmokeTestTags.SUBSCRIPTIONS_OPEN_UPDATES_BUTTON).performClick()
        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.UPDATES_TITLE)
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

private fun ComposeContentTestRule.waitUntilNodeWithTagMissing(tag: String) {
    waitUntil(timeoutMillis = 5_000) {
        onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
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

private fun ComposeContentTestRule.waitUntilAnyTextExists(vararg texts: String) {
    waitUntil(timeoutMillis = 5_000) {
        texts.any { text -> onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}

private fun ComposeContentTestRule.waitUntilNotAllTextsExist(vararg texts: String) {
    waitUntil(timeoutMillis = 5_000) {
        texts.count { text -> onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } < texts.size
    }
}
