package io.legado.app.ui.replace

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import io.legado.app.base.BaseComposeActivity
import io.legado.app.ui.replace.edit.ReplaceEditRouteScreen
import io.legado.app.ui.replace.edit.ReplaceEditViewModel
import io.legado.app.ui.theme.LocalAppUiConfiguration
import kotlinx.serialization.json.Json
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

class ReplaceRuleActivity : BaseComposeActivity() {

    companion object {
        const val EXTRA_START_ROUTE = "start_route"
        fun startIntent(
            context: Context,
            editRoute: ReplaceEditRoute? = null
        ): Intent = Intent(context, ReplaceRuleActivity::class.java).apply {
            editRoute?.let {
                putExtra(EXTRA_START_ROUTE, Json.encodeToString(it))
            }
        }
    }

    @Composable
    override fun Content() {
            val configuration = LocalAppUiConfiguration.current
            val context = LocalActivity.current
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current
        fun leaveScreen(action: () -> Unit) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            action()
        }
            val startRouteJson = intent.getStringExtra(EXTRA_START_ROUTE)
            val backStack = rememberNavBackStack(
                remember(startRouteJson) {
                    resolveStartRoute(startRouteJson)
                }
            )

            LaunchedEffect(Unit) {
                context?.setResult(RESULT_OK)
            }

            NavDisplay(
                backStack = backStack,
                transitionSpec = {
                    (slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(
                            durationMillis = 480,
                            easing = FastOutSlowInEasing
                        ),
                        initialOffset = { fullWidth -> fullWidth }
                    ) + fadeIn(
                        animationSpec = tween(
                            durationMillis = 360,
                            easing = LinearOutSlowInEasing
                        )
                    )) togetherWith (slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(
                            durationMillis = 480,
                            easing = FastOutSlowInEasing
                        ),
                        targetOffset = { fullWidth -> fullWidth / 4 }
                    ) + fadeOut(
                        animationSpec = tween(
                            durationMillis = 360,
                            easing = LinearOutSlowInEasing
                        )
                    ))
                },
                popTransitionSpec = {
                    (slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(
                            durationMillis = 480,
                            easing = FastOutSlowInEasing
                        ),
                        initialOffset = { fullWidth -> -fullWidth / 4 }
                    ) + fadeIn(
                        animationSpec = tween(
                            durationMillis = 360,
                            easing = LinearOutSlowInEasing
                        )
                    )) togetherWith (scaleOut(
                        targetScale = 0.8f,
                        animationSpec = tween(
                            durationMillis = 480,
                            easing = FastOutSlowInEasing
                        )
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 360)
                    ))
                },
                predictivePopTransitionSpec = { _ ->
                    (slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(
                            easing = FastOutSlowInEasing
                        ),
                        initialOffset = { fullWidth -> -fullWidth / 4 }
                    ) + fadeIn(
                        animationSpec = tween(
                            easing = LinearOutSlowInEasing
                        )
                    )) togetherWith (scaleOut(
                        targetScale = 0.8f,
                        animationSpec = tween(
                            easing = FastOutSlowInEasing
                        )
                    ) + fadeOut(
                        animationSpec = tween()
                    ))
                },
                onBack = {
                    leaveScreen {
                        if (backStack.size > 1) {
                            backStack.removeLastOrNull()
                        } else {
                            finish()
                        }
                    }
                },
                entryProvider = entryProvider {
                    entry<ReplaceRuleRoute> {
                        ReplaceRuleRouteScreen(
                            onBackClick = { leaveScreen { finish() } },
                            onNavigateToEdit = { route -> backStack.add(route) }
                        )
                    }

                    entry<ReplaceEditRoute> { route ->
                        val viewModel: ReplaceEditViewModel = koinViewModel(
                            key = "replace_edit_${route.sessionId}"
                        ) { parametersOf(route) }

                        ReplaceEditRouteScreen(
                            viewModel = viewModel,
                            onBack = {
                                leaveScreen {
                                    if (backStack.size > 1) {
                                        backStack.removeLastOrNull()
                                    } else {
                                        finish()
                                    }
                                }
                            },
                            onSaveSuccess = {
                                leaveScreen {
                                    if (backStack.size > 1) {
                                        backStack.removeLastOrNull()
                                    } else {
                                        finish()
                                    }
                                }
                            }
                        )
                    }
                }
            )
            BackHandler(enabled = !configuration.appShell.predictiveBackEnabled) {
                leaveScreen {
                    if (backStack.size > 1) {
                        backStack.removeLastOrNull()
                    } else {
                        finish()
                    }
                }
            }
    }

    private fun resolveStartRoute(route: String?): NavKey {
        return route?.let { Json.decodeFromString<ReplaceEditRoute>(it) } ?: ReplaceRuleRoute
    }
}
