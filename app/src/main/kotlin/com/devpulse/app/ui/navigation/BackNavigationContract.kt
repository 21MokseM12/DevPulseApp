package com.devpulse.app.ui.navigation

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

internal data class NonMainRouteContract(
    val title: String,
    val backTargetRoute: String,
)

/**
 * Единый navigation contract для non-main маршрутов.
 *
 * route                 | back target
 * --------------------- | -----------
 * quiet-hours-schedule  | settings
 *
 * Архитектурный выбор: централизованный top bar/back policy в AppNavGraph.
 * Это исключает рассинхрон локальных реализаций между экранами и упрощает
 * проверку одинакового поведения toolbar back и hardware back.
 */
private val nonMainRouteContracts =
    mapOf(
        AppRoute.QuietHoursSchedule.route to
            NonMainRouteContract(
                title = "Quiet hours schedule",
                backTargetRoute = AppRoute.Settings.route,
            ),
    )

internal fun resolveNonMainRouteContract(route: String?): NonMainRouteContract? {
    if (route == null) {
        return null
    }
    return nonMainRouteContracts[route]
}

internal fun NavHostController.navigateBackWithPolicy(currentRoute: String?) {
    val backTargetRoute = resolveNonMainRouteContract(currentRoute)?.backTargetRoute
    if (backTargetRoute == null) {
        navigateUp()
        return
    }

    val poppedToTarget = popBackStack(backTargetRoute, inclusive = false)
    if (!poppedToTarget) {
        navigate(backTargetRoute) {
            popUpTo(graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}
