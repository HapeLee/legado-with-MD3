package io.legado.app.ui.replace

import android.content.Context
import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.legado.app.base.BaseComposeActivity
import io.legado.app.ui.replace.edit.ReplaceEditScreen
import io.legado.app.ui.replace.edit.ReplaceEditViewModel
import io.legado.app.ui.theme.AppTheme
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
        AppTheme {
            val navController = rememberNavController()
            val context = LocalActivity.current

            LaunchedEffect(Unit) {
                context?.setResult(RESULT_OK)
            }

            val startRouteJson = intent.getStringExtra(EXTRA_START_ROUTE)
            val initialEditRoute = remember(startRouteJson) {
                startRouteJson?.let { Json.decodeFromString<ReplaceEditRoute>(it) }
            }

            val startDestination: Any = initialEditRoute ?: ReplaceRuleRoute

            SharedTransitionLayout {
                NavHost(
                    navController = navController,
                    startDestination = startDestination
                ) {
                    composable<ReplaceRuleRoute> {
                        ReplaceRuleScreen(
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@composable,
                            onBackClick = { finish() },
                            onNavigateToEdit = { route -> navController.navigate(route) }
                        )
                    }

                    composable<ReplaceEditRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<ReplaceEditRoute>()
                        val viewModel: ReplaceEditViewModel = koinViewModel { parametersOf(route) }

                        ReplaceEditScreen(
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@composable,
                            viewModel = viewModel,
                            onBack = {
                                // 如果编辑页是唯一的页面，点击返回应关闭 Activity
                                if (navController.previousBackStackEntry == null) {
                                    finish()
                                } else {
                                    navController.popBackStack()
                                }
                            },
                            onSaveSuccess = {
                                if (navController.previousBackStackEntry == null) finish()
                                else navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }
}

