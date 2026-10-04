package com.custom.treadmill.ui

import androidx.compose.runtime.Composable
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

object Routes {
    const val MAIN = "main"
    const val PROGRAMS = "programs"
    const val WORKOUT = "workout"
    const val SETTINGS = "settings"
    const val DEBUG = "debug"
    const val ABOUT = "about"
    const val HISTORY = "history"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val mainVm: MainViewModel = viewModel()
    val programVm: ProgramViewModel = viewModel()

    NavHost(navController = navController, startDestination = Routes.MAIN) {

        composable(Routes.MAIN) {
            MainScreen(vm = mainVm, onNavigate = { navController.navigate(it) })
        }
        composable(Routes.PROGRAMS) {
            ProgramsScreen(
                mainVm = mainVm,
                programVm = programVm,
                onBack = { navController.popBackStack() },
                onStartWorkout = {
                    mainVm.startWorkout(it)
                    navController.navigate(Routes.WORKOUT)
                }
            )
        }
        composable(Routes.WORKOUT) {
            WorkoutScreen(vm = mainVm, onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(vm = mainVm, onBack = { navController.popBackStack() })
        }
        composable(Routes.DEBUG) {
            DebugScreen(vm = mainVm, onBack = { navController.popBackStack() })
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.HISTORY) {
            HistoryScreen(programVm = programVm, onBack = { navController.popBackStack() })
        }
    }
}
