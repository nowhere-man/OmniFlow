package com.omniflow.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.compose.serialization.serializers.MutableStateSerializer
import kotlinx.serialization.Serializable

@Serializable
sealed interface OmniRoute : NavKey {
    @Serializable
    data object Home : OmniRoute

    @Serializable
    data object Analytics : OmniRoute

    @Serializable
    data object Search : OmniRoute

    @Serializable
    data object More : OmniRoute

    @Serializable
    data object TransactionEditor : OmniRoute
}

internal val TopLevelRoutes = setOf<NavKey>(
    OmniRoute.Home,
    OmniRoute.Analytics,
    OmniRoute.Search,
    OmniRoute.More,
)

@Composable
internal fun rememberOmniNavigationState(): OmniNavigationState {
    val topLevelRoute = rememberSerializable(
        serializer = MutableStateSerializer(NavKeySerializer()),
    ) {
        mutableStateOf<NavKey>(OmniRoute.Home)
    }
    val backStacks = TopLevelRoutes.associateWith { route -> rememberNavBackStack(route) }
    return remember(topLevelRoute, backStacks) {
        OmniNavigationState(topLevelRoute, backStacks)
    }
}

internal class OmniNavigationState(
    topLevelRoute: MutableState<NavKey>,
    val backStacks: Map<NavKey, NavBackStack<NavKey>>,
) {
    var topLevelRoute: NavKey by topLevelRoute

    val currentBackStack: NavBackStack<NavKey>
        get() = backStacks.getValue(topLevelRoute)

    val currentRoute: NavKey
        get() = currentBackStack.last()

    fun navigate(route: NavKey) {
        if (route in backStacks) {
            topLevelRoute = route
        } else if (currentBackStack.lastOrNull() != route) {
            currentBackStack.add(route)
        }
    }

    fun goBack(): Boolean {
        if (currentBackStack.size > 1) {
            currentBackStack.removeLastOrNull()
            return true
        }
        if (topLevelRoute != OmniRoute.Home) {
            topLevelRoute = OmniRoute.Home
            return true
        }
        return false
    }
}

internal fun Modifier.readableContentWidth(maxWidth: Dp = 1040.dp): Modifier =
    widthIn(max = maxWidth).fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
