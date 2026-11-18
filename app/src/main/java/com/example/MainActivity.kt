package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.firestore.ExamRepository
import com.example.data.firestore.authStateFlow
import com.example.data.preferences.AppPreferences
import com.example.data.preferences.AppPreferencesRepository
import com.example.data.preferences.ThemeMode
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.MainAppScaffold
import com.example.ui.theme.EUjianTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.util.ExamNotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ExamNotificationHelper.ensureChannel(this)
        setContent {
            val context = LocalContext.current
            val prefsRepo = remember(context) { AppPreferencesRepository(context.applicationContext) }
            val prefs by prefsRepo.preferencesFlow.collectAsStateWithLifecycle(initialValue = AppPreferences())
            val scope = rememberCoroutineScope()

            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (prefs.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> systemDark
            }

            EUjianTheme(darkTheme = darkTheme) {
                AppNavigation(
                    appPreferences = prefs,
                    isDarkThemeActive = darkTheme,
                    onSetThemeMode = { mode ->
                        scope.launch { prefsRepo.setThemeMode(mode) }
                    },
                    onSetNotificationsEnabled = { enabled ->
                        scope.launch { prefsRepo.setNotificationsEnabled(enabled) }
                    }
                )
            }
        }
    }
}

@Composable
fun AppNavigation(
    appPreferences: AppPreferences,
    isDarkThemeActive: Boolean,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetNotificationsEnabled: (Boolean) -> Unit,
    auth: FirebaseAuth = Firebase.auth
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser
    if (user == null) {
        AuthScreen(
            isDarkThemeActive = isDarkThemeActive,
            onToggleDarkTheme = {
                onSetThemeMode(if (isDarkThemeActive) ThemeMode.LIGHT else ThemeMode.DARK)
            },
            onAuthSuccess = { }
        )
    } else {
        val uid = user.uid
        key(uid) {
            val viewModel: MainViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        MainViewModel(ExamRepository(db, auth), uid)
                    }
                }
            )
            MainAppScaffold(
                currentUserId = uid,
                viewModel = viewModel,
                appPreferences = appPreferences,
                isDarkThemeActive = isDarkThemeActive,
                onSetThemeMode = onSetThemeMode,
                onSetNotificationsEnabled = onSetNotificationsEnabled
            )
        }
    }
}
