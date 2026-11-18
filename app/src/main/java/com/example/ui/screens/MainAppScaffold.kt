package com.example.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.firestore.QrScanOutcome
import com.example.data.firestore.signOutUser
import com.example.data.model.UserRole
import com.example.data.preferences.AppPreferences
import com.example.data.preferences.ThemeMode
import com.example.ui.viewmodel.MainViewModel
import com.example.util.ExamNotificationHelper
import kotlinx.serialization.Serializable

@Serializable object RouteDashboard
@Serializable object RouteStudents
@Serializable object RouteExams
@Serializable object RouteScanner
@Serializable object RoutePortal

private data class NavTabItem(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun MainAppScaffold(
    currentUserId: String,
    viewModel: MainViewModel,
    appPreferences: AppPreferences,
    isDarkThemeActive: Boolean,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetNotificationsEnabled: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val role by viewModel.effectiveRole.collectAsStateWithLifecycle()
    val classesState by viewModel.classesState.collectAsStateWithLifecycle()
    val subjectsState by viewModel.subjectsState.collectAsStateWithLifecycle()
    val studentsState by viewModel.studentsState.collectAsStateWithLifecycle()
    val examsState by viewModel.examsState.collectAsStateWithLifecycle()
    val attendanceState by viewModel.attendanceState.collectAsStateWithLifecycle()
    val submissionsState by viewModel.submissionsState.collectAsStateWithLifecycle()
    val proctorsState by viewModel.proctorsState.collectAsStateWithLifecycle()
    val selectedExam by viewModel.selectedExamForDetail.collectAsStateWithLifecycle()
    val selectedExamQuestionsState by viewModel.selectedExamQuestionsState.collectAsStateWithLifecycle()
    val qrScanOutcome by viewModel.qrScanOutcome.collectAsStateWithLifecycle()
    val activeExamSession by viewModel.activeExamSession.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    LaunchedEffect(qrScanOutcome) {
        if (qrScanOutcome is QrScanOutcome.Success) {
            val rec = (qrScanOutcome as QrScanOutcome.Success).record
            ExamNotificationHelper.showNotification(
                context = context,
                enabled = appPreferences.notificationsEnabled,
                title = "Absensi QR Tercatat",
                message = "${rec.studentName} (${rec.className}) hadir pada ${rec.examTitle}."
            )
        }
    }

    val activeSession = activeExamSession
    if (activeSession != null) {
        ExamRunnerScreen(
            session = activeSession,
            onSelectAnswer = viewModel::updateDraftAnswer,
            onSubmitExam = {
                viewModel.submitActiveExam()
                ExamNotificationHelper.showNotification(
                    context = context,
                    enabled = appPreferences.notificationsEnabled,
                    title = "Ujian Dikumpulkan",
                    message = "Jawaban untuk ${activeSession.exam.title} berhasil dikumpulkan."
                )
            },
            onExit = viewModel::closeExamSession
        )
        return
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val dashboardLabel = stringResource(R.string.nav_dashboard)
    val studentsLabel = stringResource(R.string.nav_students)
    val examsLabel = stringResource(R.string.nav_exams)
    val scannerLabel = stringResource(R.string.nav_scanner)
    val portalLabel = stringResource(R.string.nav_portal)

    val tabs = remember(role, dashboardLabel, studentsLabel, examsLabel, scannerLabel, portalLabel) {
        when (role) {
            UserRole.ADMIN -> listOf(
                NavTabItem(RouteDashboard, dashboardLabel, Icons.Rounded.Home, Icons.Outlined.Home, "nav_dashboard"),
                NavTabItem(RouteStudents, studentsLabel, Icons.Rounded.Groups, Icons.Outlined.Groups, "nav_students"),
                NavTabItem(RouteExams, examsLabel, Icons.Rounded.Assignment, Icons.Outlined.Assignment, "nav_exams"),
                NavTabItem(RouteScanner, scannerLabel, Icons.Rounded.QrCodeScanner, Icons.Outlined.QrCodeScanner, "nav_scanner"),
                NavTabItem(RoutePortal, portalLabel, Icons.Rounded.Badge, Icons.Outlined.Badge, "nav_portal")
            )
            UserRole.PROCTOR -> listOf(
                NavTabItem(RouteDashboard, dashboardLabel, Icons.Rounded.Home, Icons.Outlined.Home, "nav_dashboard"),
                NavTabItem(RouteStudents, studentsLabel, Icons.Rounded.Groups, Icons.Outlined.Groups, "nav_students"),
                NavTabItem(RouteExams, examsLabel, Icons.Rounded.Assignment, Icons.Outlined.Assignment, "nav_exams"),
                NavTabItem(RouteScanner, scannerLabel, Icons.Rounded.QrCodeScanner, Icons.Outlined.QrCodeScanner, "nav_scanner")
            )
            UserRole.STUDENT -> listOf(
                NavTabItem(RouteDashboard, dashboardLabel, Icons.Rounded.Home, Icons.Outlined.Home, "nav_dashboard"),
                NavTabItem(RoutePortal, portalLabel, Icons.Rounded.Badge, Icons.Outlined.Badge, "nav_portal")
            )
        }
    }

    fun navigateToTab(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        tabs.forEach { tab ->
                            val selected = currentDestination?.hasRoute(tab.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
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
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        tabs.forEach { tab ->
                            val selected = currentDestination?.hasRoute(tab.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize()
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = RouteDashboard,
                        modifier = Modifier
                            .widthIn(max = 840.dp)
                            .fillMaxSize()
                    ) {
                        composable<RouteDashboard> {
                            DashboardScreen(
                                currentUserId = currentUserId,
                                profileState = profileState,
                                role = role,
                                appPreferences = appPreferences,
                                isDarkThemeActive = isDarkThemeActive,
                                classesState = classesState,
                                studentsState = studentsState,
                                examsState = examsState,
                                attendanceState = attendanceState,
                                submissionsState = submissionsState,
                                proctorsState = proctorsState,
                                onSetThemeMode = onSetThemeMode,
                                onSetNotificationsEnabled = onSetNotificationsEnabled,
                                onSeedSampleData = viewModel::seedSampleData,
                                onGrantProctor = viewModel::grantProctor,
                                onRevokeProctor = viewModel::revokeProctor,
                                onNavigateToScanner = { navigateToTab(RouteScanner) },
                                onNavigateToExams = { navigateToTab(RouteExams) },
                                onNavigateToStudents = { navigateToTab(RouteStudents) },
                                onNavigateToPortal = { navigateToTab(RoutePortal) },
                                onSignOut = { signOutUser(credentialManager, scope) },
                                onShowMessage = viewModel::showMessage
                            )
                        }
                        composable<RouteStudents> {
                            StudentsScreen(
                                role = role,
                                classesState = classesState,
                                studentsState = studentsState,
                                onAddClass = viewModel::addClass,
                                onAddStudent = viewModel::addStudent,
                                onDeleteStudent = viewModel::deleteStudent
                            )
                        }
                        composable<RouteExams> {
                            ExamsScreen(
                                role = role,
                                subjectsState = subjectsState,
                                classesState = classesState,
                                examsState = examsState,
                                selectedExam = selectedExam,
                                questionsState = selectedExamQuestionsState,
                                attendanceState = attendanceState,
                                submissionsState = submissionsState,
                                onSelectExam = viewModel::selectExamForDetail,
                                onAddSubject = viewModel::addSubject,
                                onAddExam = viewModel::addExam,
                                onDeleteExam = viewModel::deleteExam,
                                onAddQuestion = viewModel::addQuestion,
                                onDeleteQuestion = viewModel::deleteQuestion,
                                onGradeEssay = { sub, scores ->
                                    viewModel.gradeEssay(sub, scores)
                                    val total = sub.autoScore + scores.values.sum()
                                    ExamNotificationHelper.showNotification(
                                        context = context,
                                        enabled = appPreferences.notificationsEnabled,
                                        title = "Nilai Esai Diperbarui",
                                        message = "Nilai ${sub.studentName} pada ${sub.examTitle}: $total/${sub.maxScore}."
                                    )
                                }
                            )
                        }
                        composable<RouteScanner> {
                            QrScannerScreen(
                                role = role,
                                examsState = examsState,
                                attendanceState = attendanceState,
                                scanOutcome = qrScanOutcome,
                                onScanToken = viewModel::processQrScan,
                                onClearOutcome = viewModel::clearQrScanOutcome
                            )
                        }
                        composable<RoutePortal> {
                            StudentPortalScreen(
                                role = role,
                                profileState = profileState,
                                studentsState = studentsState,
                                examsState = examsState,
                                submissionsState = submissionsState,
                                onBeginExam = viewModel::launchExam
                            )
                        }
                    }
                }
            }
        }
    }
}
