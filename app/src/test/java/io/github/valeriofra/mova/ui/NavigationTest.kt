package io.github.valeriofra.mova.ui

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Navegação entre as abas (Início, Fila, Biblioteca, Ajustes) e o fluxo de download. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NavigationTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var nav: NavHostController

    private fun setUpNavHost() {
        compose.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = Routes.HOME) {
                composable(Routes.HOME) { Text("início") }
                composable(Routes.QUEUE) { Text("fila") }
                composable(Routes.LIBRARY) { Text("biblioteca") }
                composable(Routes.SETTINGS) { Text("ajustes") }
                composable(
                    Routes.ANALYSIS,
                    arguments = listOf(navArgument("input") { type = NavType.StringType; defaultValue = "" }),
                ) { Text("análise") }
            }
        }
    }

    private fun act(block: NavHostController.() -> Unit) = compose.runOnIdle { nav.block() }

    private fun assertRoute(route: String) = compose.runOnIdle { assertEquals(route, nav.currentDestination?.route) }

    @Test
    fun homeTabReturnsHomeAfterDownloadFlow() {
        // Caminho relatado: analisar → baixar (vai para a Fila) → tocar em Início.
        setUpNavHost()
        act { navigate(Routes.analysis("https://www.instagram.com/reel/ABC/")) }
        act { navigateToQueueAfterEnqueue() }
        assertRoute(Routes.QUEUE)
        act { navigateTopLevel(Routes.HOME) }
        assertRoute(Routes.HOME)
    }

    @Test
    fun switchingTabsAlwaysReachesTheChosenTab() {
        setUpNavHost()
        act { navigateTopLevel(Routes.QUEUE) }
        assertRoute(Routes.QUEUE)
        act { navigateTopLevel(Routes.LIBRARY) }
        assertRoute(Routes.LIBRARY)
        act { navigateTopLevel(Routes.HOME) }
        assertRoute(Routes.HOME)
        act { navigateTopLevel(Routes.QUEUE) }
        assertRoute(Routes.QUEUE)
        act { navigateTopLevel(Routes.SETTINGS) }
        assertRoute(Routes.SETTINGS)
        act { navigateTopLevel(Routes.HOME) }
        assertRoute(Routes.HOME)
        // Tocar em Início estando no Início não empilha nada.
        act { navigateTopLevel(Routes.HOME) }
        compose.runOnIdle { assertEquals(false, nav.popBackStack()) }
    }
}
