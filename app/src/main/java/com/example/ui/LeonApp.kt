package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ai.AliAiBrain
import com.example.automation.LeonActionExecutor
import com.example.data.FirebaseCloudSync
import com.example.data.LeonDatabase
import com.example.data.LeonRepository
import com.example.service.VoiceServiceBridge
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.DeepObsidian
import com.example.ui.theme.MidnightSlate

const val ROUTE_HUD = "hud_dashboard"
const val ROUTE_CONTROL = "control_deck"
const val ROUTE_RADAR = "sixth_sense_radar"
const val ROUTE_MEMORY = "memory_vault"
const val ROUTE_SECURITY = "security_vault"
const val ROUTE_NEWS = "live_news"
const val ROUTE_OFFLINE_VISION = "offline_vision"
const val ROUTE_SOCIAL_POSTER = "social_poster"
const val ROUTE_LIFE_COMPASS = "life_compass"
const val ROUTE_CALL_ASSISTANT = "call_assistant"

private data class LeonNavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val leonDestinations = listOf(
    LeonNavDestination(ROUTE_HUD, "LEON", Icons.Filled.Hub, Icons.Outlined.Hub, "nav_hud"),
    LeonNavDestination(ROUTE_CONTROL, "Control", Icons.Filled.Bolt, Icons.Outlined.Bolt, "nav_control"),
    LeonNavDestination(ROUTE_RADAR, "Radar", Icons.Filled.Radar, Icons.Outlined.Radar, "nav_radar"),
    LeonNavDestination(ROUTE_MEMORY, "Memory", Icons.Filled.Psychology, Icons.Outlined.Psychology, "nav_memory"),
    LeonNavDestination(ROUTE_SECURITY, "Security", Icons.Filled.Security, Icons.Outlined.Security, "nav_security")
)

@Composable
fun LeonAppNavigation() {
    val viewModel: LeonViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val db = LeonDatabase.getInstance(app.applicationContext)
                val repo = LeonRepository(db, app.applicationContext)
                val cloudSync = FirebaseCloudSync(app.applicationContext, repo)
                LeonViewModel(
                    repository = repo,
                    aliBrain = AliAiBrain(),
                    executor = LeonActionExecutor(app.applicationContext),
                    appContext = app.applicationContext,
                    cloudSync = cloudSync
                )
            }
        }
    )

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_HUD
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val openOfflineVisionTrigger by viewModel.openOfflineVisionTrigger.collectAsStateWithLifecycle()
    val openSocialPosterTrigger by viewModel.openSocialPosterTrigger.collectAsStateWithLifecycle()
    val openLifeCompassTrigger by viewModel.openLifeCompassTrigger.collectAsStateWithLifecycle()
    val openCallAssistantTrigger by viewModel.openCallAssistantTrigger.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> VoiceServiceBridge.setForegroundUiActive(true)
                Lifecycle.Event.ON_STOP -> VoiceServiceBridge.setForegroundUiActive(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            VoiceServiceBridge.setForegroundUiActive(false)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissBanner()
        }
    }

    LaunchedEffect(openOfflineVisionTrigger) {
        if (openOfflineVisionTrigger > 0) {
            navController.navigate(ROUTE_OFFLINE_VISION) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(openSocialPosterTrigger) {
        if (openSocialPosterTrigger > 0) {
            navController.navigate(ROUTE_SOCIAL_POSTER) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(openLifeCompassTrigger) {
        if (openLifeCompassTrigger > 0) {
            navController.navigate(ROUTE_LIFE_COMPASS) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(openCallAssistantTrigger) {
        if (openCallAssistantTrigger > 0) {
            navController.navigate(ROUTE_CALL_ASSISTANT) {
                launchSingleTop = true
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            containerColor = DeepObsidian,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(containerColor = MidnightSlate) {
                        leonDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = DeepObsidian,
                                    selectedTextColor = ArcCyan,
                                    indicatorColor = ArcCyan,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(containerColor = MidnightSlate) {
                        leonDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = DeepObsidian,
                                    selectedTextColor = ArcCyan,
                                    indicatorColor = ArcCyan,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_HUD
                    ) {
                        composable(ROUTE_HUD) {
                            HudDashboardScreen(
                                viewModel = viewModel,
                                onOpenNews = {
                                    navController.navigate(ROUTE_NEWS) {
                                        launchSingleTop = true
                                    }
                                },
                                onOpenOfflineVision = {
                                    navController.navigate(ROUTE_OFFLINE_VISION) {
                                        launchSingleTop = true
                                    }
                                },
                                onOpenSocialPoster = {
                                    navController.navigate(ROUTE_SOCIAL_POSTER) {
                                        launchSingleTop = true
                                    }
                                },
                                onOpenLifeCompass = {
                                    navController.navigate(ROUTE_LIFE_COMPASS) {
                                        launchSingleTop = true
                                    }
                                },
                                onOpenCallAssistant = {
                                    navController.navigate(ROUTE_CALL_ASSISTANT) {
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                        composable(ROUTE_CONTROL) {
                            AutomationScreen(
                                viewModel = viewModel,
                                onOpenSocialPoster = {
                                    navController.navigate(ROUTE_SOCIAL_POSTER) {
                                        launchSingleTop = true
                                    }
                                },
                                onOpenCallAssistant = {
                                    navController.navigate(ROUTE_CALL_ASSISTANT) {
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                        composable(ROUTE_RADAR) {
                            SixthSenseScreen(viewModel = viewModel)
                        }
                        composable(ROUTE_MEMORY) {
                            MemoryScreen(viewModel = viewModel)
                        }
                        composable(ROUTE_SECURITY) {
                            PermissionsScreen(viewModel = viewModel)
                        }
                        composable(ROUTE_NEWS) {
                            NewsScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(ROUTE_OFFLINE_VISION) {
                            OfflineVisionScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(ROUTE_SOCIAL_POSTER) {
                            SocialPosterScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(ROUTE_LIFE_COMPASS) {
                            LifeCompassScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(ROUTE_CALL_ASSISTANT) {
                            CallAssistantScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
