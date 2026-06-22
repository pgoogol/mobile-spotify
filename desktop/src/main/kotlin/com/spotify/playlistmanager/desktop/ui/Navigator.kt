package com.spotify.playlistmanager.desktop.ui

import androidx.compose.runtime.mutableStateListOf

/**
 * Trasy nawigacji desktopu — odpowiednik `Screen` z `:app/NavGraph.kt`.
 * Cztery zakładki dolnego paska + ekrany wtórne (push na stos).
 */
sealed interface Route {
    /** Trasa pokazywana w dolnym pasku nawigacji. */
    sealed interface Tab : Route {
        val label: String
    }

    data object Playlists : Tab {
        override val label = "Playlisty"
    }

    data object Generate : Tab {
        override val label = "Generuj"
    }

    data object Stepwise : Tab {
        override val label = "Krok"
    }

    data object Profile : Tab {
        override val label = "Profil"
    }

    data class Tracks(val playlistId: String, val playlistName: String) : Route
    data object Settings : Route
    data object CsvImport : Route
}

/**
 * Prosty stos nawigacji (desktopowy odpowiednik NavController). Zakładki
 * resetują stos do danej trasy (jak `popUpTo(start)` w mobile), ekrany wtórne
 * są wkładane na stos i zdejmowane przyciskiem „wstecz".
 */
class Navigator {
    private val stack = mutableStateListOf<Route>(Route.Playlists)

    val current: Route get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    fun selectTab(tab: Route.Tab) {
        stack.clear()
        stack.add(tab)
    }

    fun push(route: Route) {
        stack.add(route)
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
}
