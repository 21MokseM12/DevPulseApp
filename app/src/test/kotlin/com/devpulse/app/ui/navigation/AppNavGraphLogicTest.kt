package com.devpulse.app.ui.navigation

import com.devpulse.app.ui.main.StartupDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AppNavGraphLogicTest {
    @Test
    fun resolveStartupRoute_returnsNull_whenDestinationIsLoading() {
        val route = resolveStartupRoute(StartupDestination.Loading, openUpdatesRequest = false)

        assertNull(route)
    }

    @Test
    fun resolveStartupRoute_returnsAuthRoute_whenDestinationIsAuth() {
        val route = resolveStartupRoute(StartupDestination.Auth, openUpdatesRequest = true)

        assertEquals(AppRoute.Auth.route, route)
    }

    @Test
    fun resolveStartupRoute_returnsSubscriptions_whenSessionExistsAndNoPushRequest() {
        val route = resolveStartupRoute(StartupDestination.Subscriptions, openUpdatesRequest = false)

        assertEquals(AppRoute.Subscriptions.route, route)
    }

    @Test
    fun resolveStartupRoute_returnsUpdates_whenSessionExistsAndPushRequested() {
        val route = resolveStartupRoute(StartupDestination.Subscriptions, openUpdatesRequest = true)

        assertEquals(AppRoute.Updates.route, route)
    }

    @Test
    fun resolveNonMainRouteContract_returnsSettingsBackTarget_forQuietHoursRoute() {
        val contract = resolveNonMainRouteContract(AppRoute.QuietHoursSchedule.route)

        assertNotNull(contract)
        assertEquals(AppRoute.Settings.route, contract?.backTargetRoute)
    }

    @Test
    fun resolveNonMainRouteContract_returnsRussianTitle_forQuietHoursRoute() {
        val contract = resolveNonMainRouteContract(AppRoute.QuietHoursSchedule.route)

        assertEquals("Расписание тихих часов", contract?.title)
    }

    @Test
    fun resolveNonMainRouteContract_returnsNull_forMainRoute() {
        val contract = resolveNonMainRouteContract(AppRoute.Subscriptions.route)

        assertNull(contract)
    }
}
