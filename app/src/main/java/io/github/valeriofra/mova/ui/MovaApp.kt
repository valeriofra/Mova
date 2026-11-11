package io.github.valeriofra.mova.ui

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.valeriofra.mova.CrashReporter
import io.github.valeriofra.mova.IncomingEvent
import io.github.valeriofra.mova.MainActivity
import io.github.valeriofra.mova.R
import io.github.valeriofra.mova.ui.analysis.AnalysisScreen
import io.github.valeriofra.mova.ui.analysis.AnalysisViewModel
import io.github.valeriofra.mova.ui.analysis.ItemSelectionScreen
import io.github.valeriofra.mova.ui.home.HomeScreen
import io.github.valeriofra.mova.ui.library.LibraryScreen
import io.github.valeriofra.mova.ui.queue.QueueScreen
import io.github.valeriofra.mova.ui.settings.SettingsScreen
import io.github.valeriofra.mova.ui.theme.MovaColors
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val QUEUE = "queue"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val ANALYSIS = "analysis?input={input}"
    const val SELECTION = "analysis/selection"

    fun analysis(input: String) = "analysis?input=${Uri.encode(input)}"
}

private data class TopLevel(val route: String, val icon: ImageVector, val label: Int)

private val topLevels = listOf(
    TopLevel(Routes.HOME, Icons.Rounded.Home, R.string.nav_home),
    TopLevel(Routes.QUEUE, Icons.AutoMirrored.Rounded.FormatListBulleted, R.string.nav_queue),
    TopLevel(Routes.LIBRARY, Icons.Rounded.VideoLibrary, R.string.nav_library),
    TopLevel(Routes.SETTINGS, Icons.Rounded.Tune, R.string.nav_settings),
)

@Composable
fun MovaApp(
    incomingEvents: StateFlow<IncomingEvent?>,
    onEventConsumed: (IncomingEvent) -> Unit,
) {
    val navController = rememberNavController()
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = topLevels.any { it.route == currentRoute }

    var sharedText by remember { mutableStateOf<IncomingEvent.SharedText?>(null) }
    var crashReport by remember { mutableStateOf(CrashReporter.pending(context)) }
    val event by incomingEvents.collectAsStateWithLifecycle()
    LaunchedEffect(event) {
        when (val e = event) {
            is IncomingEvent.SharedText -> {
                sharedText = e
                navController.navigateTopLevel(Routes.HOME)
                onEventConsumed(e)
            }
            is IncomingEvent.OpenDestination -> {
                val route = when (e.destination) {
                    MainActivity.DESTINATION_LIBRARY -> Routes.LIBRARY
                    else -> Routes.QUEUE
                }
                navController.navigateTopLevel(route)
                onEventConsumed(e)
            }
            null -> Unit
        }
    }

    crashReport?.let { report ->
        CrashReportDialog(
            report = report,
            onShare = {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(android.content.Intent.EXTRA_SUBJECT, "Relatório de falha do MOVA")
                    .putExtra(android.content.Intent.EXTRA_TEXT, report)
                context.startActivity(android.content.Intent.createChooser(send, null))
                CrashReporter.clear(context)
                crashReport = null
            },
            onDismiss = {
                CrashReporter.clear(context)
                crashReport = null
            },
        )
    }

    Scaffold(
        containerColor = MovaColors.Background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MovaColors.Surface, tonalElevation = 0.dp) {
                    topLevels.forEach { item ->
                        val selected = backStack?.destination?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateTopLevel(item.route) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.label)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MovaColors.Accent,
                                selectedTextColor = MovaColors.Accent,
                                indicatorColor = MovaColors.Blue.copy(alpha = 0.22f),
                                unselectedIconColor = MovaColors.TextSecondary,
                                unselectedTextColor = MovaColors.TextSecondary,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            // consumeWindowInsets: o espaço da barra inferior já foi aplicado, então o
            // teclado não soma esse espaço de novo (evita a faixa preta acima dele).
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    viewModel = viewModel(factory = MovaViewModels.factory),
                    sharedText = sharedText,
                    onSharedTextConsumed = { sharedText = null },
                    onAnalyze = { input -> navController.navigate(Routes.analysis(input)) },
                    onOpenSettings = { navController.navigateTopLevel(Routes.SETTINGS) },
                )
            }
            composable(
                route = Routes.ANALYSIS,
                arguments = listOf(navArgument("input") { type = NavType.StringType; defaultValue = "" }),
            ) {
                AnalysisScreen(
                    viewModel = viewModel(factory = MovaViewModels.factory),
                    onBack = { navController.popBackStack() },
                    onSelectItems = { navController.navigate(Routes.SELECTION) },
                    onEnqueued = { count -> navController.afterEnqueue(count, snackbarHost, scope, context) },
                )
            }
            composable(Routes.SELECTION) { entry ->
                val parent = remember(entry) { navController.getBackStackEntry(Routes.ANALYSIS) }
                val analysisViewModel: AnalysisViewModel = viewModel(parent, factory = MovaViewModels.factory)
                ItemSelectionScreen(
                    viewModel = analysisViewModel,
                    onBack = { navController.popBackStack() },
                    onEnqueued = { count -> navController.afterEnqueue(count, snackbarHost, scope, context) },
                )
            }
            composable(Routes.QUEUE) {
                QueueScreen(
                    viewModel = viewModel(factory = MovaViewModels.factory),
                    onOpenHome = { navController.navigateTopLevel(Routes.HOME) },
                    onOpenLibrary = { navController.navigateTopLevel(Routes.LIBRARY) },
                )
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    viewModel = viewModel(factory = MovaViewModels.factory),
                    snackbarHost = snackbarHost,
                    onOpenHome = { navController.navigateTopLevel(Routes.HOME) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(viewModel = viewModel(factory = MovaViewModels.factory), snackbarHost = snackbarHost)
            }
        }
    }
}

internal fun NavHostController.navigateTopLevel(route: String) {
    val start = graph.findStartDestination()
    if (route == start.route) {
        // Início é a base da pilha: basta voltar até ele. Usar saveState/restoreState
        // aqui fazia o app "restaurar" a tela anterior (ex.: Fila) em vez do Início.
        if (!popBackStack(start.id, inclusive = false)) navigate(route) { launchSingleTop = true }
        return
    }
    navigate(route) {
        popUpTo(start.id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Depois de adicionar à fila: mostra a Fila, com o Início como tela anterior. */
internal fun NavHostController.navigateToQueueAfterEnqueue() {
    navigate(Routes.QUEUE) {
        popUpTo(graph.findStartDestination().id) { saveState = false }
        launchSingleTop = true
    }
}

private fun NavHostController.afterEnqueue(
    count: Int,
    snackbarHost: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context,
) {
    navigateToQueueAfterEnqueue()
    val message = context.resources.getQuantityString(R.plurals.enqueued_message, count, count)
    scope.launch { snackbarHost.showSnackbar(message) }
}

@Composable
private fun CrashReportDialog(report: String, onShare: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.crash_title)) },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(stringResource(R.string.crash_message))
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 12.dp))
                Text(
                    report.lineSequence().drop(5).firstOrNull { it.isNotBlank() }.orEmpty(),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = MovaColors.TextSecondary,
                    maxLines = 4,
                )
            }
        },
        confirmButton = {
            io.github.valeriofra.mova.ui.components.AccentTextButton(stringResource(R.string.crash_share), onShare)
        },
        dismissButton = {
            io.github.valeriofra.mova.ui.components.AccentTextButton(stringResource(R.string.crash_dismiss), onDismiss)
        },
        containerColor = MovaColors.SurfaceHigh,
    )
}
