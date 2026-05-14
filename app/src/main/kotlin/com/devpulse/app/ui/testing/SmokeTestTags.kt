package com.devpulse.app.ui.testing

object SmokeTestTags {
    const val AUTH_TITLE = "auth_title"
    const val AUTH_LOGIN_INPUT = "auth_login_input"
    const val AUTH_PASSWORD_INPUT = "auth_password_input"
    const val AUTH_SUBMIT_BUTTON = "auth_submit_button"

    const val SUBSCRIPTIONS_TITLE = "subscriptions_title"
    const val SUBSCRIPTIONS_LINK_INPUT = "subscriptions_link_input"
    const val SUBSCRIPTIONS_TAGS_INPUT = "subscriptions_tags_input"
    const val SUBSCRIPTIONS_FILTERS_INPUT = "subscriptions_filters_input"
    const val SUBSCRIPTIONS_ADD_BUTTON = "subscriptions_add_button"
    const val SUBSCRIPTIONS_OPEN_UPDATES_BUTTON = "subscriptions_open_updates_button"
    const val SUBSCRIPTIONS_LOGOUT_BUTTON = "subscriptions_logout_button"
    const val SUBSCRIPTIONS_REMOVE_CONFIRM_BUTTON = "subscriptions_remove_confirm_button"

    const val UPDATES_TITLE = "updates_title"
    const val UPDATES_UNREAD_COUNT = "updates_unread_count"
    const val UPDATES_OPEN_SUBSCRIPTIONS_BUTTON = "updates_open_subscriptions_button"
    const val UPDATES_LOGOUT_BUTTON = "updates_logout_button"
    const val UPDATES_SEARCH_INPUT = "updates_search_input"
    const val UPDATES_RESET_FILTERS_BUTTON = "updates_reset_filters_button"

    fun subscriptionRow(id: Long): String = "subscriptions_row_$id"

    fun subscriptionRemoveButton(id: Long): String = "subscriptions_remove_button_$id"

    fun updateMarkReadButton(id: Long): String = "updates_mark_read_button_$id"
}
