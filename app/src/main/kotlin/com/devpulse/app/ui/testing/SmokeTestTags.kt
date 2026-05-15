package com.devpulse.app.ui.testing

object SmokeTestTags {
    const val AUTH_TITLE = "auth_title"
    const val AUTH_LOGIN_INPUT = "auth_login_input"
    const val AUTH_PASSWORD_INPUT = "auth_password_input"
    const val AUTH_LOGIN_BUTTON = "auth_login_button"
    const val AUTH_REGISTER_BUTTON = "auth_register_button"
    const val AUTH_LOGIN_LOADER = "auth_login_loader"
    const val AUTH_REGISTER_LOADER = "auth_register_loader"

    const val SUBSCRIPTIONS_TITLE = "subscriptions_title"
    const val SUBSCRIPTIONS_LINK_INPUT = "subscriptions_link_input"
    const val SUBSCRIPTIONS_TAGS_INPUT = "subscriptions_tags_input"
    const val SUBSCRIPTIONS_FILTERS_INPUT = "subscriptions_filters_input"
    const val SUBSCRIPTIONS_ADD_BUTTON = "subscriptions_add_button"
    const val SUBSCRIPTIONS_OPEN_UPDATES_BUTTON = "subscriptions_open_updates_button"
    const val SUBSCRIPTIONS_LOGOUT_BUTTON = "subscriptions_logout_button"
    const val SUBSCRIPTIONS_REMOVE_CONFIRM_BUTTON = "subscriptions_remove_confirm_button"
    const val SUBSCRIPTIONS_SEARCH_INPUT = "subscriptions_search_input"
    const val SUBSCRIPTIONS_PRESET_ONLY_TAGGED = "subscriptions_preset_only_tagged"
    const val SUBSCRIPTIONS_PRESET_WITH_FILTERS = "subscriptions_preset_with_filters"
    const val SUBSCRIPTIONS_PRESET_RECENTLY_ADDED = "subscriptions_preset_recently_added"
    const val SUBSCRIPTIONS_SORT_BY_URL = "subscriptions_sort_by_url"
    const val SUBSCRIPTIONS_CLEAR_SEARCH_BUTTON = "subscriptions_clear_search_button"

    const val UPDATES_TITLE = "updates_title"
    const val UPDATES_UNREAD_COUNT = "updates_unread_count"
    const val UPDATES_OPEN_SUBSCRIPTIONS_BUTTON = "updates_open_subscriptions_button"
    const val UPDATES_LOGOUT_BUTTON = "updates_logout_button"
    const val UPDATES_SEARCH_INPUT = "updates_search_input"
    const val UPDATES_RESET_FILTERS_BUTTON = "updates_reset_filters_button"

    fun subscriptionRow(id: Long): String = "subscriptions_row_$id"

    fun subscriptionRemoveButton(id: Long): String = "subscriptions_remove_button_$id"

    fun subscriptionTagFilter(tag: String): String = "subscriptions_tag_filter_${tag.lowercase()}"

    fun updateMarkReadButton(id: Long): String = "updates_mark_read_button_$id"
}
