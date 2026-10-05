package com.custom.treadmill.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.custom.treadmill.ui.screens.AboutScreen
import com.custom.treadmill.ui.screens.DebugScreen
import com.custom.treadmill.ui.screens.HistoryScreen
import com.custom.treadmill.ui.screens.MainScreen
import com.custom.treadmill.ui.screens.ProgramsScreen
import com.custom.treadmill.ui.screens.SettingsScreen
import com.custom.treadmill.ui.screens.WorkoutScreen
import com.custom.treadmill.ui.viewmodels.MainViewModel
import com.custom.treadmill.ui.viewmodels.ProgramViewModel
import kotlinx.coroutines.launch

object Routes {
    const val TABS = "tabs"
    const val WORKOUT = "workout"
    const val DEBUG = "debug"
    const val ABOUT = "about"
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

    NavHost(navController = nav, startDestination = Routes.TABS) {
        composable(Routes.TABS) {
            MainTabs(
                mainVm = mainVm,
                programVm = programVm,
                onOpenWorkout = { nav.navigate(Routes.WORKOUT) },
                onOpenDebug = { nav.navigate(Routes.DEBUG) },
                onOpenAbout = { nav.navigate(Routes.ABOUT) }
            )
        }
        composable(Routes.WORKOUT) { WorkoutScreen(vm = mainVm, onBack = { nav.popBackStack() }) }
        composable(Routes.DEBUG) { DebugScreen(vm = mainVm, onBack = { nav.popBackStack() }) }
        composable(Routes.ABOUT) { AboutScreen(onBack = { nav.popBackStack() }) }
    }
}

private data class Tab(val title: String, val icon: ImageVector)

@OptIn(ExperimentalFoundationApi::class)   // ← ВОТ ЭТА СТРОКА ЛЕЧИТ ОШИБКИ
@Composable
private fun MainTabs(
    mainVm: MainViewModel,
    programVm: ProgramViewModel,
    onOpenWorkout: () -> Unit,
    onOpenDebug: () -> Unit,
    onOpenAbout: () -> Unit,
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
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) { page ->
            when (page) {
                0 -> MainScreen(
                    vm = mainVm,
                    onNavigate = { route ->
                        when (route) {
                            Routes.PROGRAMS -> scope.launch { pagerState.animateScrollToPage(1) }
                            Routes.HISTORY  -> scope.launch { pagerState.animateScrollToPage(2) }
                            Routes.SETTINGS -> scope.launch { pagerState.animateScrollToPage(3) }
                            Routes.WORKOUT  -> onOpenWorkout()
                            Routes.DEBUG    -> onOpenDebug()
                            Routes.ABOUT    -> onOpenAbout()
                        }
                    }
                )
                1 -> ProgramsScreen(
                    mainVm = mainVm, programVm = programVm,
                    onBack = null,
                    onStartWorkout = { mainVm.startWorkout(it); onOpenWorkout() }
                )
                2 -> HistoryScreen(programVm = programVm, onBack = null)
                3 -> SettingsScreen(vm = mainVm, onBack = null)
            }
        }
    }
}
