package com.flightradius.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.flightradius.app.data.api.LocalNetworkGuard
import com.flightradius.app.data.prefs.AppSettings
import com.flightradius.app.data.prefs.SettingsRepository
import com.flightradius.app.location.LocationRepository
import com.flightradius.app.service.MonitoringController
import com.flightradius.app.service.MonitoringStateRepository
import com.flightradius.app.ui.alerts.ProximityAlertSheet
import com.flightradius.app.ui.aircraft.AircraftScreen
import com.flightradius.app.ui.debug.DebugScreen
import com.flightradius.app.ui.fleets.FleetsScreen
import com.flightradius.app.ui.onboarding.WelcomeScreen
import com.flightradius.app.ui.radar.RadarScreen
import com.flightradius.app.ui.rememberMonitoringStarter
import com.flightradius.app.ui.settings.SettingsScreen
import com.flightradius.app.ui.theme.FlightRadiusTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

// Custom ImageVectors (icons-core has no radar/plane/layers glyphs).
private val RadarIcon: ImageVector = ImageVector.Builder(
    "Radar", 24.dp, 24.dp, 24f, 24f
).addPath(
    pathData = addPathNodes(
        // outer ring (r=8 ring thickness ~0.8) + mid ring + center dot
        "M12 4a8 8 0 1 1 0 16 8 8 0 1 1 0-16zm0 0.8A7.2 7.2 0 1 0 12 19.2 7.2 7.2 0 0 0 12 4.8z" +
            "M12 8a4 4 0 1 1 0 8 4 4 0 1 1 0-8zm0 0.8a3.2 3.2 0 1 0 0 6.4 3.2 3.2 0 0 0 0-6.4z" +
            "M12 10.5a1.5 1.5 0 1 1 0 3 1.5 1.5 0 1 1 0-3z"
    ),
    fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
    pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
).addPath(
    pathData = addPathNodes("M12 12 L12 4 A8 8 0 0 1 18.4 7.7 Z"),
    fill = androidx.compose.ui.graphics.SolidColor(Color.Black.copy(alpha = 0.6f))
).build()

private val PlaneIcon: ImageVector = ImageVector.Builder(
    "Plane", 24.dp, 24.dp, 24f, 24f
).addPath(
    pathData = addPathNodes(
        "M21.5 15.5v-2l-8.5-5V3.5a1.5 1.5 0 0 0-3 0v5l-8.5 5v2l8.5-2.5v5.5L7.5 20v1.5l4.5-1.25 4.5 1.25V20l-2.5-1.5v-5.5l8.5 2.5z"
    ),
    fill = androidx.compose.ui.graphics.SolidColor(Color.Black)
).build()

private val FleetsIcon: ImageVector = ImageVector.Builder(
    "Fleets", 24.dp, 24.dp, 24f, 24f
).addPath(
    pathData = addPathNodes(
        "M11.99 18.54l-7.37-5.73L3 14.07l9 7 9-7-1.63-1.27-7.38 5.74z" +
            "M12 16l7.36-5.73L21 9l-9-7-9 7 1.63 1.27L12 16z"
    ),
    fill = androidx.compose.ui.graphics.SolidColor(Color.Black)
).build()

private val SettingsIcon: ImageVector = Icons.Filled.Settings

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var controller: MonitoringController
    @Inject lateinit var stateRepository: MonitoringStateRepository
    @Inject lateinit var locationRepository: LocationRepository
    @Inject lateinit var localNetworkGuard: LocalNetworkGuard
    @Inject lateinit var settingsRepository: SettingsRepository

    /** Set by the composed root — invoked for resume_monitoring intents. */
    private var startMonitoringAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("resume_monitoring", false) == true) {
            startMonitoringAction?.invoke()
        }
        // alert_aircraft_id is rendered by the sheet bound to
        // MonitoringStateRepository.activeAlert — no extra work needed
        // beyond bringing the activity forward.
    }

    @Composable
    private fun App() {
        val loaded by settingsRepository.settings
            .collectAsStateWithLifecycle(initialValue = null)
        val settings = loaded ?: AppSettings()
        val nav = rememberNavController()
        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route
        val scope = rememberCoroutineScope()
        var pendingRoute by remember { mutableStateOf<String?>(null) }

        val starter = rememberMonitoringStarter(
            controller = controller,
            locationRepository = locationRepository,
            localNetworkGuard = localNetworkGuard,
            settings = settings,
            onGoToLocationSettings = { nav.navigateToTab(NavItem.Settings.route) }
        )
        startMonitoringAction = { starter.begin() }

        FlightRadiusTheme(themeMode = settings.themeMode) {
            val activeAlert by stateRepository.activeAlert
                .collectAsStateWithLifecycle()
            val monitorState by stateRepository.state
                .collectAsStateWithLifecycle()

            if (loaded == null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                )
                return@FlightRadiusTheme
            }
            if (!settings.onboardingDone) {
                WelcomeScreen(
                    onAddFirstAircraft = {
                        pendingRoute = "aircraft?add=true"
                        scope.launch { settingsRepository.setOnboardingDone() }
                    },
                    onNotNow = {
                        pendingRoute = NavItem.Radar.route
                        scope.launch { settingsRepository.setOnboardingDone() }
                    }
                )
                return@FlightRadiusTheme
            }

            BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            val onSelect: (NavItem) -> Unit = { nav.navigateToTab(it.route) }
            Scaffold(
                bottomBar = {
                    if (!landscape) BottomNav(route, onSelect)
                }
            ) { padding ->
                Row(Modifier.fillMaxSize().padding(padding)) {
                if (landscape) SideRail(route, onSelect)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    NavHost(nav, startDestination = NavItem.Radar.route) {
                        composable(NavItem.Radar.route) {
                            RadarScreen(
                                onStartMonitoring = { starter.begin() },
                                onOpenSettings = { nav.navigateToTab(NavItem.Settings.route) },
                                onOpenAircraft = { add ->
                                    nav.navigateToTab("aircraft?add=$add", restore = false)
                                },
                                onEditAircraft = { id ->
                                    nav.navigateToTab("aircraft?edit=$id", restore = false)
                                }
                            )
                        }
                        composable(
                            "aircraft?add={add}&edit={edit}",
                            arguments = listOf(
                                navArgument("add") {
                                    type = NavType.BoolType; defaultValue = false
                                },
                                navArgument("edit") {
                                    type = NavType.LongType; defaultValue = -1L
                                }
                            )
                        ) { entry ->
                            AircraftScreen(
                                settings = settings,
                                openAddOnLaunch =
                                    entry.arguments?.getBoolean("add") == true,
                                editId = entry.arguments?.getLong("edit")
                                    ?.takeIf { it > 0 }
                            )
                        }
                        composable(NavItem.Fleets.route) {
                            FleetsScreen(settings = settings)
                        }
                        composable(NavItem.Settings.route) {
                            SettingsScreen(
                                onOpenDebug = { nav.navigate("debug") })
                        }
                        composable("debug") { DebugScreen(onBack = { nav.popBackStack() }) }
                    }

                    LaunchedEffect(pendingRoute) {
                        pendingRoute?.let {
                            nav.navigateToTab(it, restore = false)
                            pendingRoute = null
                        }
                    }

                    // Root-level proximity alert overlay.
                    if (settings.inAppAlertBanner) {
                        activeAlert?.let { event ->
                            // Live-update distance from newer snapshots.
                            val obs = monitorState.lastSnapshot?.ranked
                                ?.find { it.aircraftId == event.observation.aircraftId }
                                ?: event.observation
                            val extra = monitorState.lastSnapshot?.ranked
                                ?.count {
                                    it.distanceKm <= it.effectiveRadiusKm &&
                                        it.aircraftId != obs.aircraftId
                                } ?: 0
                            ProximityAlertSheet(
                                obs = obs,
                                extraCount = extra,
                                unit = settings.distanceUnit,
                                vibrationEnabled = settings.alertVibration,
                                onDismiss = { controller.dismissAlert() },
                                onSnooze = { controller.snooze(obs.aircraftId, it) }
                            )
                        }
                    }
                }
                }
            }
            }
        }
    }

    @Composable
    private fun CappedFontScale(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(
                density.density,
                fontScale = minOf(density.fontScale, 1.3f)
            ),
            content = content
        )
    }

    @Composable
    private fun BottomNav(route: String?, onSelect: (NavItem) -> Unit) {
        Column {
            HorizontalDivider(
                thickness = Dp.Hairline,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            CappedFontScale {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    NavItem.entries.forEach { item ->
                        NavigationBarItem(
                            selected = isSelected(route, item),
                            onClick = { onSelect(item) },
                            icon = {
                                Icon(item.icon, contentDescription =
                                    stringResource(item.labelRes))
                            },
                            label = { Text(stringResource(item.labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = Color.Transparent,
                                unselectedIconColor =
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor =
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun SideRail(route: String?, onSelect: (NavItem) -> Unit) {
        Row(Modifier.fillMaxHeight()) {
            CappedFontScale {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                    Spacer(Modifier.weight(1f))
                    NavItem.entries.forEach { item ->
                        NavigationRailItem(
                            selected = isSelected(route, item),
                            onClick = { onSelect(item) },
                            icon = {
                                Icon(item.icon, contentDescription =
                                    stringResource(item.labelRes))
                            },
                            label = { Text(stringResource(item.labelRes)) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = Color.Transparent,
                                unselectedIconColor =
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor =
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
            VerticalDivider(
                thickness = Dp.Hairline,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }

    private fun isSelected(route: String?, item: NavItem) =
        route?.startsWith(item.route) == true ||
            (route == "debug" && item == NavItem.Settings)

    /**
     * Switches to a top-level tab instead of pushing onto the current tab's
     * stack; otherwise cross-links (e.g. Radar -> Settings) would leave the
     * Radar tab permanently restoring to Settings. [restore] = false when the
     * route carries arguments that must take effect.
     */
    private fun NavHostController.navigateToTab(route: String, restore: Boolean = true) {
        navigate(route) {
            popUpTo(graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = restore
        }
    }

    private enum class NavItem(
        val route: String,
        val icon: ImageVector,
        val labelRes: Int
    ) {
        Radar("radar", RadarIcon, R.string.nav_radar),
        Aircraft("aircraft", PlaneIcon, R.string.nav_aircraft),
        Fleets("fleets", FleetsIcon, R.string.nav_fleets),
        Settings("settings", SettingsIcon, R.string.nav_settings)
    }
}
