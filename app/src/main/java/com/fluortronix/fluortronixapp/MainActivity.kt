package com.fluortronix.fluortronixapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fluortronix.fluortronixapp.presentation.screens.LoadingScreen
import com.fluortronix.fluortronixapp.presentation.screens.OnboardingScreen
import com.fluortronix.fluortronixapp.presentation.screens.MainScreen
import com.fluortronix.fluortronixapp.presentation.viewmodels.OnboardingViewModel
import com.fluortronix.fluortronixapp.ui.theme.FluortronixappTheme
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val onboardingViewModel: OnboardingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Enable edge-to-edge without specifying SystemBarStyle
        setContent {
            FluortronixappTheme {
                // Configure system bars
                val systemUiController = rememberSystemUiController()
                val view = LocalView.current
                SideEffect {
                    val window = view.context as android.app.Activity
                    WindowCompat.setDecorFitsSystemWindows(window.window, false)
                    systemUiController.setStatusBarColor(
                        color = Color.Transparent,
                        darkIcons = true
                    )
                    systemUiController.setNavigationBarColor(
                        color = Color.Transparent,
                        darkIcons = true,
                        navigationBarContrastEnforced = false
                    )
                }
                FluortronixApp(onboardingViewModel)
            }
        }
    }
}

@Composable
fun FluortronixApp(onboardingViewModel: OnboardingViewModel) {
    val navController = rememberNavController()
    val hasCompletedOnboarding by onboardingViewModel.hasCompletedOnboarding.collectAsState()

    NavHost(
        navController = navController,
        startDestination = "loading",
        modifier = Modifier.fillMaxSize()
    ) {
        composable("loading") {
            LoadingScreen {
                if (hasCompletedOnboarding) {
                    navController.navigate("main") {
                        popUpTo("loading") { inclusive = true }
                    }
                } else {
                    navController.navigate("onboarding") {
                        popUpTo("loading") { inclusive = true }
                    }
                }
            }
        }
        composable("onboarding") {
            OnboardingScreen(
                navController = navController,
                onComplete = {
                    onboardingViewModel.setOnboardingCompleted()
                    navController.navigate("main") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }
        composable("main") {
            MainScreen()
        }
    }
}