package com.devpulse.app.ui.navigation

sealed interface AppRoute {
    val route: String

    data object Splash : AppRoute {
        override val route: String = "splash"
    }

    data object Auth : AppRoute {
        override val route: String = "auth"
    }

    data object Subscriptions : AppRoute {
        override val route: String = "subscriptions"
    }

    data object Updates : AppRoute {
        override val route: String = "updates"
    }

    data object Settings : AppRoute {
        override val route: String = "settings"
    }
}
