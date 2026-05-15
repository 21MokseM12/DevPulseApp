package com.devpulse.app.ui.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.devpulse.app.MainActivity
import com.devpulse.app.data.local.preferences.SessionStore
import com.devpulse.app.testing.FakeSmokeRemoteDataSource
import com.devpulse.app.ui.testing.SmokeTestTags
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import javax.inject.Inject

@HiltAndroidTest
class AuthUiIntegrationTest {
    private val hiltRule = HiltAndroidRule(this)
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hiltRule).around(composeRule)

    @Inject
    lateinit var smokeDataSource: FakeSmokeRemoteDataSource

    @Inject
    lateinit var sessionStore: SessionStore

    @Before
    fun setUp() {
        hiltRule.inject()
        smokeDataSource.reset()
        runBlocking {
            sessionStore.clearSession()
        }
    }

    @Test
    fun registerHappyPath_navigatesToSubscriptions() {
        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.AUTH_TITLE)
        fillAuthCredentials(login = "moksem", password = "secret")

        composeRule.onNodeWithTag(SmokeTestTags.AUTH_REGISTER_BUTTON).performClick()

        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.SUBSCRIPTIONS_TITLE)
        composeRule.waitUntilNodeWithTagMissing(SmokeTestTags.AUTH_TITLE)
    }

    @Test
    fun loginValidation_blankCredentials_showsValidationError() {
        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.AUTH_TITLE)

        composeRule.onNodeWithTag(SmokeTestTags.AUTH_LOGIN_BUTTON).performClick()

        composeRule.waitUntilNodeWithTextExists("Для входа заполните логин и пароль.")
        composeRule.waitUntilNodeWithTextExists("Повторить вход")
        composeRule.onNodeWithTag(SmokeTestTags.AUTH_REGISTER_BUTTON).assertIsDisplayed()
    }

    @Test
    fun loginNetworkError_thenRetrySuccess_completesAuthorization() {
        smokeDataSource.setRegisterFailureForTesting(message = "Временная сеть недоступна")
        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.AUTH_TITLE)
        fillAuthCredentials(login = "moksem", password = "secret")

        composeRule.onNodeWithTag(SmokeTestTags.AUTH_LOGIN_BUTTON).performClick()
        composeRule.waitUntilNodeWithTextExists("Не удалось войти. Временная сеть недоступна")
        composeRule.waitUntilNodeWithTextExists("Повторить вход")

        smokeDataSource.setRegisterSuccessForTesting()
        composeRule.onNodeWithTag(SmokeTestTags.AUTH_LOGIN_BUTTON).performClick()

        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.SUBSCRIPTIONS_TITLE)
        composeRule.waitUntilNodeWithTagMissing(SmokeTestTags.AUTH_TITLE)
    }

    private fun fillAuthCredentials(
        login: String,
        password: String,
    ) {
        composeRule.onNodeWithTag(SmokeTestTags.AUTH_LOGIN_INPUT).performTextInput(login)
        composeRule.onNodeWithTag(SmokeTestTags.AUTH_PASSWORD_INPUT).performTextInput(password)
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
