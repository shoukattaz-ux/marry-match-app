package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.example.push.PushNotifications
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.data.AuthState
import com.example.data.MarryMatchRepository
import com.example.model.MatchItem
import com.example.model.UserProfile
import com.example.ui.screens.*

enum class AppTab {
    DISCOVER,
    LIKES,
    CHATS,
    PROFILE
}

enum class ActiveSubScreen {
    NONE,
    CHAT_THREAD,
    SETTINGS,
    SAFETY_CENTER,
    ADMIN_PANEL,
    SELFIE_VERIFICATION,
    PRIVACY_SETTINGS,
    PROFILE_CREATION,
    CHAPERONE,
    CHAT_MEDIA_SETTINGS
}

@Composable
fun MainApp(
    repository: MarryMatchRepository,
    modifier: Modifier = Modifier
) {
    val authState by repository.authState.collectAsState()
    val lang by repository.appLanguage.collectAsState()
    val isConfigured by repository.isConfigured.collectAsState()
    val matchCelebration by repository.matchCelebration.collectAsState()
    val newLikes by repository.newLikesCount.collectAsState()
    val unreadChats = repository.matches.collectAsState().value.count { it.unread }

    if (!isConfigured) {
        SupabaseNotConfiguredScreen(repository = repository, modifier = modifier)
        return
    }

    var activeTab by remember { mutableStateOf(AppTab.DISCOVER) }
    var activeSubScreen by remember { mutableStateOf(ActiveSubScreen.NONE) }
    var editorStartStep by remember { mutableIntStateOf(1) }
    var activeChatMatch by remember { mutableStateOf<MatchItem?>(null) }
    var reportUserTarget by remember { mutableStateOf<UserProfile?>(null) }

    when (authState) {
        AuthState.LOADING -> {
            // The saved login is still loading: never show Welcome before we know.
            StartupLoadingScreen(appName = Localization.t("app_name", lang))
        }
        AuthState.WELCOME -> {
            WelcomeScreen(repository = repository)
        }
        AuthState.SIGNUP -> {
            BackHandler { repository.setAuthState(AuthState.WELCOME) }
            SignUpScreen(repository = repository)
        }
        AuthState.LOGIN,
        AuthState.FORGOT_PASSWORD -> {
            BackHandler { repository.setAuthState(AuthState.WELCOME) }
            LoginScreen(repository = repository)
        }
        AuthState.LOCATION_GATE -> {
            BackHandler {
                if (repository.isPostLoginLocationGate.value) {
                    repository.signOut()
                } else {
                    repository.setAuthState(AuthState.WELCOME)
                }
            }
            LocationGateScreen(repository = repository)
        }
        AuthState.LOCATION_REJECTED -> {
            BackHandler {
                if (repository.isPostLoginLocationGate.value) {
                    repository.signOut()
                } else {
                    repository.setAuthState(AuthState.WELCOME)
                }
            }
            LocationRejectedScreen(repository = repository)
        }
        AuthState.ONBOARDING -> {
            OnboardingScreen(repository = repository)
        }
        AuthState.CHAPERONE -> {
            ChaperoneScreen(repository = repository, chaperoneOnly = true)
        }
        AuthState.LOGGED_IN -> {
            AskForNotificationPermission()
            // Main Authenticated Experience
            if (activeSubScreen != ActiveSubScreen.NONE) {
                when (activeSubScreen) {
                    ActiveSubScreen.CHAT_THREAD -> {
                        activeChatMatch?.let { match ->
                            BackHandler { activeSubScreen = ActiveSubScreen.NONE }
                            ChatScreen(
                                match = match,
                                repository = repository,
                                onBack = { activeSubScreen = ActiveSubScreen.NONE },
                                onOpenReport = { reportUserTarget = match.matchedUser }
                            )
                        } ?: run { activeSubScreen = ActiveSubScreen.NONE }
                    }
                    ActiveSubScreen.SETTINGS -> {
                        BackHandler { activeSubScreen = ActiveSubScreen.NONE }
                        SettingsScreen(
                            repository = repository,
                            onBack = { activeSubScreen = ActiveSubScreen.NONE },
                            onOpenSafety = { activeSubScreen = ActiveSubScreen.SAFETY_CENTER },
                            onOpenPrivacySettings = { activeSubScreen = ActiveSubScreen.PRIVACY_SETTINGS },
                            onOpenChaperone = { activeSubScreen = ActiveSubScreen.CHAPERONE },
                            onOpenChatMedia = { activeSubScreen = ActiveSubScreen.CHAT_MEDIA_SETTINGS }
                        )
                    }
                    ActiveSubScreen.SAFETY_CENTER -> {
                        BackHandler { activeSubScreen = ActiveSubScreen.SETTINGS }
                        SafetyCenterScreen(
                            repository = repository,
                            onBack = { activeSubScreen = ActiveSubScreen.SETTINGS }
                        )
                    }
                    ActiveSubScreen.PRIVACY_SETTINGS -> {
                        BackHandler { activeSubScreen = ActiveSubScreen.SETTINGS }
                        PrivacySettingsScreen(
                            repository = repository,
                            onBack = { activeSubScreen = ActiveSubScreen.SETTINGS }
                        )
                    }
                    ActiveSubScreen.ADMIN_PANEL -> {
                        BackHandler { activeSubScreen = ActiveSubScreen.NONE }
                        AdminPanelScreen(
                            repository = repository,
                            onBack = { activeSubScreen = ActiveSubScreen.NONE }
                        )
                    }
                    ActiveSubScreen.SELFIE_VERIFICATION -> {
                        BackHandler { activeSubScreen = ActiveSubScreen.NONE }
                        SelfieVerificationScreen(
                            repository = repository,
                            onClose = { activeSubScreen = ActiveSubScreen.NONE }
                        )
                    }
                    ActiveSubScreen.PROFILE_CREATION -> {
                        OnboardingScreen(
                            repository = repository,
                            editMode = true,
                            startStep = editorStartStep,
                            onClose = { activeSubScreen = ActiveSubScreen.NONE }
                        )
                    }
                    ActiveSubScreen.CHAPERONE -> {
                        BackHandler { activeSubScreen = ActiveSubScreen.SETTINGS }
                        ChaperoneScreen(
                            repository = repository,
                            onBack = { activeSubScreen = ActiveSubScreen.SETTINGS }
                        )
                    }
                    ActiveSubScreen.CHAT_MEDIA_SETTINGS -> {
                        BackHandler { activeSubScreen = ActiveSubScreen.SETTINGS }
                        ChatMediaSettingsScreen(
                            repository = repository,
                            onBack = { activeSubScreen = ActiveSubScreen.SETTINGS }
                        )
                    }
                    ActiveSubScreen.NONE -> {}
                }
            } else {
                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                        ) {
                            NavigationBarItem(
                                selected = activeTab == AppTab.DISCOVER,
                                onClick = { activeTab = AppTab.DISCOVER },
                                icon = {
                                    Icon(
                                        imageVector = if (activeTab == AppTab.DISCOVER) Icons.Filled.TravelExplore else Icons.Outlined.TravelExplore,
                                        contentDescription = "Discover"
                                    )
                                },
                                label = { Text(if (lang == AppLanguage.EN) "Discover" else "Entdecken") },
                                modifier = Modifier.testTag("nav_tab_discover")
                            )
                            NavigationBarItem(
                                selected = activeTab == AppTab.LIKES,
                                onClick = { activeTab = AppTab.LIKES },
                                icon = {
                                    // Only likes that arrived since the tab was last opened.
                                    BadgedBox(badge = { if (newLikes > 0) Badge { Text(newLikes.toString()) } }) {
                                        Icon(
                                            imageVector = if (activeTab == AppTab.LIKES) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                            contentDescription = "Likes"
                                        )
                                    }
                                },
                                label = { Text(Localization.t("likes_you", lang)) },
                                modifier = Modifier.testTag("nav_tab_likes")
                            )
                            NavigationBarItem(
                                selected = activeTab == AppTab.CHATS,
                                onClick = { activeTab = AppTab.CHATS },
                                icon = {
                                    // Chats with messages you have not read yet.
                                    BadgedBox(badge = { if (unreadChats > 0) Badge { Text(unreadChats.toString()) } }) {
                                        Icon(
                                            imageVector = if (activeTab == AppTab.CHATS) Icons.Filled.Chat else Icons.Outlined.ChatBubbleOutline,
                                            contentDescription = "Chats"
                                        )
                                    }
                                },
                                label = { Text(if (lang == AppLanguage.EN) "Chats" else "Nachrichten") },
                                modifier = Modifier.testTag("nav_tab_chats")
                            )
                            NavigationBarItem(
                                selected = activeTab == AppTab.PROFILE,
                                onClick = { activeTab = AppTab.PROFILE },
                                icon = {
                                    Icon(
                                        imageVector = if (activeTab == AppTab.PROFILE) Icons.Filled.Person else Icons.Outlined.PersonOutline,
                                        contentDescription = "Profile"
                                    )
                                },
                                label = { Text(if (lang == AppLanguage.EN) "Profile" else "Profil") },
                                modifier = Modifier.testTag("nav_tab_profile")
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (activeTab) {
                            AppTab.DISCOVER -> {
                                DiscoverScreen(
                                    repository = repository,
                                    onOpenReport = { reportUserTarget = it }
                                )
                            }
                            AppTab.LIKES -> {
                                LikesYouScreen(
                                    repository = repository,
                                    onOpenDiscover = { activeTab = AppTab.DISCOVER }
                                )
                            }
                            AppTab.CHATS -> {
                                ChatsScreen(
                                    repository = repository,
                                    onOpenChat = { match ->
                                        activeChatMatch = match
                                        activeSubScreen = ActiveSubScreen.CHAT_THREAD
                                    },
                                    onOpenDiscover = { activeTab = AppTab.DISCOVER }
                                )
                            }
                            AppTab.PROFILE -> {
                                ProfileScreen(
                                    repository = repository,
                                    onOpenSettings = { activeSubScreen = ActiveSubScreen.SETTINGS },
                                    onOpenAdmin = { activeSubScreen = ActiveSubScreen.ADMIN_PANEL },
                                    onOpenSelfieVerification = { activeSubScreen = ActiveSubScreen.SELFIE_VERIFICATION },
                                    onOpenProfileCreation = {
                                        editorStartStep = 1
                                        activeSubScreen = ActiveSubScreen.PROFILE_CREATION
                                    },
                                    onOpenProfileEditorAt = { step ->
                                        editorStartStep = step
                                        activeSubScreen = ActiveSubScreen.PROFILE_CREATION
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Match Celebration Dialog
    matchCelebration?.let { user ->
        MatchCelebrationDialog(
            matchedUser = user,
            onSendMessage = {
                repository.dismissCelebration()
                val match = repository.matches.value.firstOrNull { it.matchedUser.id == user.id }
                if (match != null) {
                    activeChatMatch = match
                    activeSubScreen = ActiveSubScreen.CHAT_THREAD
                }
            },
            onKeepBrowsing = {
                repository.dismissCelebration()
            },
            lang = lang
        )
    }

    // Global Report Dialog
    reportUserTarget?.let { target ->
        ReportProfileDialog(
            userToReport = target,
            repository = repository,
            onDismiss = { reportUserTarget = null }
        )
    }
}

/**
 * Android 13+ needs the user's permission before notifications can be shown. Asked once per
 * app start after login; saying no is fine, the switches in Settings explain it again.
 */
@Composable
private fun AskForNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (!asked && !PushNotifications.canNotify(context)) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** Logo and a progress indicator while the saved login is loaded at app start. */
@Composable
private fun StartupLoadingScreen(appName: String) {
    Box(
        modifier = Modifier.fillMaxSize().testTag("startup_loading"),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(appName, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator()
        }
    }
}
