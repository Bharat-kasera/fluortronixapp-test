package com.fluortronix.fluortronixapp.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import androidx.hilt.navigation.compose.hiltViewModel
import com.fluortronix.fluortronixapp.presentation.components.BottomNavigationBar
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.unit.dp

const val onBoardingNavigationRoute = "onboarding_route"

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // NavHost without bottom padding to allow content to extend behind the bottom navigation bar
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.fillMaxSize() // Removed padding here
        ) {
            composable("home") {
                HomeScreen(
                    onAddDeviceClick = {
                        navController.navigate(onBoardingNavigationRoute)
                    },
                    onCreateRoomClick = {
                        navController.navigate("rooms")
                    },
                    onDeviceClick = { deviceId ->
                        navController.navigate("device_details/$deviceId")
                    },
                    onRoomClick = { roomId ->
                        navController.navigate("room_details/$roomId")
                    }
                )
            }
            composable(
                route = "device_details/{deviceId}",
                arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
            ) { backStackEntry ->
                val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
                DeviceDetailsScreen(
                    deviceId = deviceId,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            composable("rooms") {
                RoomsScreen(
                    navController = navController,
                    onRoomClick = { roomId ->
                        navController.navigate("room_details/$roomId")
                    }
                )
            }
            composable(
                route = "room_details/{roomId}",
                arguments = listOf(navArgument("roomId") { type = NavType.StringType })
            ) { backStackEntry ->
                val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
                RoomDetailsScreen(
                    roomId = roomId,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onDeviceClick = { deviceId ->
                        navController.navigate("device_details/$deviceId")
                    }
                )
            }
            composable("schedule") {
                ScheduleScreen(navController = navController)
            }
            composable(
                route = "add_edit_routine?roomId={roomId}",
                arguments = listOf(navArgument("roomId") { 
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) {
                AddEditRoutineScreen(navController = navController)
            }
            composable(
                route = "add_edit_routine/{routineId}?roomId={roomId}",
                arguments = listOf(
                    navArgument("routineId") { type = NavType.IntType },
                    navArgument("roomId") { 
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) {
                AddEditRoutineScreen(navController = navController)
            }
            composable("profile") {
                ProfileScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            onBoardingGraph(navController)
        }

        // Bottom navigation bar overlaid at the bottom center
        BottomNavigationBar(
            currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route ?: "home",
            onNavigate = { route ->
                if (route == "home") {
                    // Special handling for home navigation - clear the entire back stack
                    navController.navigate("home") {
                        popUpTo(0) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                } else {
                    // Normal navigation for other routes
                    navController.navigate(route) {
                        popUpTo("home") {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
fun NavGraphBuilder.onBoardingGraph(navController: NavHostController) {
    navigation(
        startDestination = "esp_device_selection",
        route = onBoardingNavigationRoute
    ) {
        composable("esp_device_selection") { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(onBoardingNavigationRoute)
            }
            ESPDeviceSelectionScreen(
                navController = navController,
                viewModel = hiltViewModel(parentEntry)
            )
        }
        composable("find_device") { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(onBoardingNavigationRoute)
            }
            FindDeviceScreen(
                navController = navController,
                viewModel = hiltViewModel(parentEntry)
            )
        }
        composable(
            route = "enter_credentials/{ssid}",
            arguments = listOf(navArgument("ssid") { type = NavType.StringType })
        ) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(onBoardingNavigationRoute)
            }
            EnterCredentialsScreen(
                navController = navController,
                viewModel = hiltViewModel(parentEntry)
            )
        }
        composable("provisioning") { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(onBoardingNavigationRoute)
            }
            ProvisioningScreen(
                navController = navController,
                viewModel = hiltViewModel(parentEntry)
            )
        }
        composable("assign_room") { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(onBoardingNavigationRoute)
            }
            AssignRoomScreen(
                navController = navController,
                viewModel = hiltViewModel(parentEntry)
            )
        }
    }
} 