package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.firebase.attemptAutoSignIn
import com.example.data.firebase.authStateFlow
import com.example.data.firebase.configureAppCheck
import com.example.data.firebase.signOutUser
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.InterviewStageScreen
import com.example.ui.screens.ModelsScreen
import com.example.ui.screens.PersonasScreen
import com.example.ui.theme.ClarionTheme
import com.example.viewmodel.ClarionStudioViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

private enum class StudioDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    STAGE("stage", R.string.tab_stage, Icons.Filled.GraphicEq, Icons.Outlined.GraphicEq, "nav_tab_stage"),
    AVATARS("avatars", R.string.tab_avatars, Icons.Filled.Face, Icons.Outlined.Face, "nav_tab_avatars"),
    MODELS("models", R.string.tab_models, Icons.Filled.Hub, Icons.Outlined.Hub, "nav_tab_models"),
    HISTORY("history", R.string.tab_history, Icons.Filled.History, Icons.Outlined.History, "nav_tab_history")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before any UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            ClarionTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ClarionRoot()
                }
            }
        }
    }
}

@Composable
private fun ClarionRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            ClarionStudioShell(
                currentUserId = user.uid,
                onSignOut = {
                    signOutUser(
                        credentialManager = credentialManager,
                        scope = scope
                    )
                }
            )
        }
    }
}

@Composable
private fun ClarionStudioShell(
    currentUserId: String,
    onSignOut: () -> Unit
) {
    val viewModel: ClarionStudioViewModel = viewModel(
        key = currentUserId,
        factory = ClarionStudioViewModel.provideFactory(currentUserId)
    )
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: StudioDestination.STAGE.route

    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    fun navigateTo(route: String) {
        if (currentRoute == route) return
        navController.navigate(route) {
            popUpTo(StudioDestination.STAGE.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        StudioDestination.entries.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(dest.labelRes),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary,
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
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        StudioDestination.entries.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = StudioDestination.STAGE.route
                    ) {
                        composable(StudioDestination.STAGE.route) {
                            InterviewStageScreen(
                                viewModel = viewModel,
                                onNavigateToModels = { navigateTo(StudioDestination.MODELS.route) },
                                onNavigateToAvatars = { navigateTo(StudioDestination.AVATARS.route) }
                            )
                        }
                        composable(StudioDestination.AVATARS.route) {
                            PersonasScreen(
                                viewModel = viewModel,
                                onSelectAndEnterStage = { navigateTo(StudioDestination.STAGE.route) }
                            )
                        }
                        composable(StudioDestination.MODELS.route) {
                            ModelsScreen(viewModel = viewModel)
                        }
                        composable(StudioDestination.HISTORY.route) {
                            HistoryScreen(
                                viewModel = viewModel,
                                onSignOut = onSignOut
                            )
                        }
                    }
                }
            }
        }
    }
}
