package com.custom.treadmill.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.custom.treadmill.DebugLog
import com.custom.treadmill.ui.screens.*
import com.custom.treadmill.ui.viewmodels.MainViewModel
import com.custom.treadmill.ui.viewmodels.ProgramViewModel
import kotlinx.coroutines.launch

object Routes {
    const val TABS = "tabs"
    const val WORKOUT = "workout"
    const val DEBUG = "debug"
    const val ABOUT = "about"
    const val PROFILES = "profiles"
    const val TTS = "tts"
    const val PROGRAMS = "tab_programs"
    const val HISTORY = "tab_history"
    const val SETTINGS = "tab_settings"
    const val MAIN = "tab_main"
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    val mainVm: MainViewModel = viewModel()
    val programVm: ProgramViewModel = viewModel()

    LaunchedEffect(nav) {
        nav.addOnDestinationChangedListener { _, destination, arguments ->
            val route = destination.route ?: "unknown"
            val args = arguments?.keySet()
                ?.joinToString { "$it=${arguments.get(it)}" }
                .orEmpty()
            DebugLog.d("NAV", "→ $route" + if (args.isNotEmpty()) " ($args)" else "")
        }
    }

    NavHost(navController = nav, startDestination = Routes.TABS) {
        composable(Routes.TABS) {
            MainTabs(
                mainVm = mainVm,
                programVm = programVm,
                onOpenWorkout = { nav.navigate(Routes.WORKOUT) },
                onOpenDebug = { nav.navigate(Routes.DEBUG) },
                onOpenAbout = { nav.navigate(Routes.ABOUT) },
                onOpenProfiles = { nav.navigate(Routes.PROFILES) },
                onOpenTts = { nav.navigate(Routes.TTS) }
            )
        }
        composable(Routes.WORKOUT) {
            WorkoutScreen(vm = mainVm, onBack = { nav.popBackStack() }, onOpenDebug = { nav.navigate(Routes.DEBUG) })
        }
        composable(Routes.DEBUG) { DebugScreen(vm = mainVm, onBack = { nav.popBackStack() }) }
        composable(Routes.ABOUT) { AboutScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.PROFILES) { ProfilesScreen(vm = mainVm, onBack = { nav.popBackStack() }) }
        composable(Routes.TTS) { TtsSettingsScreen(vm = mainVm, onBack = { nav.popBackStack() }) }
    }
}

private data class Tab(val title: String, val icon: ImageVector)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainTabs(
    mainVm: MainViewModel,
    programVm: ProgramViewModel,
    onOpenWorkout: () -> Unit,
    onOpenDebug: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenTts: () -> Unit
) {
    val tabs = listOf(
        Tab("Главная", Icons.Default.Home),
        Tab("Программы", Icons.AutoMirrored.Filled.List),
        Tab("Журнал", Icons.Default.History),
        Tab("Настройки", Icons.Default.Settings),
    )
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            DebugLog.d("NAV", "tab tap: ${tab.title}")
                            scope.launch { pagerState.scrollToPage(index) }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title, fontSize = 11.sp) }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenDebug,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ) {
                Icon(Icons.Default.BugReport, contentDescription = "Debug")
            }
        }
    ) { padding ->
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize().padding(padding)) { page ->
            when (page) {
                0 -> MainScreen(
                    vm = mainVm,
                    onNavigate = { route ->
                        when (route) {
                            Routes.PROGRAMS -> scope.launch { pagerState.scrollToPage(1) }
                            Routes.HISTORY  -> scope.launch { pagerState.scrollToPage(2) }
                            Routes.SETTINGS -> scope.launch { pagerState.scrollToPage(3) }
                            Routes.WORKOUT  -> onOpenWorkout()
                            Routes.DEBUG    -> onOpenDebug()
                            Routes.ABOUT    -> onOpenAbout()
                        }
                    }
                )
                1 -> ProgramsScreen(
                    mainVm = mainVm, programVm = programVm, onBack = null,
                    onStartWorkout = { mainVm.startWorkout(it); onOpenWorkout() }
                )
                2 -> HistoryScreen(programVm = programVm, onBack = null)
                3 -> SettingsScreen(
                    vm = mainVm, onBack = null, onOpenDebug = onOpenDebug, onOpenAbout = onOpenAbout,
                    onOpenProfiles = onOpenProfiles, onOpenTts = onOpenTts
                )
            }
        }
    }
}
