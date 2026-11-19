package com.example

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.example.data.local.AdminEntity
import com.example.domain.AttendanceStatus
import com.example.domain.SchoolConstants
import com.example.ui.screens.AdminAuthScreen
import com.example.ui.screens.AttendanceScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LoadingScreen
import com.example.ui.screens.RecordsSyncScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.StudentRegistrationScreen
import com.example.ui.theme.SmartAttendanceTheme
import com.example.ui.theme.VerifiedGreen
import com.example.ui.theme.VerifiedGreenBg
import com.example.ui.viewmodel.AuthStage
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.MainWorkspaceViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MainDestination(val label: String) {
    HOME("Home"),
    ATTENDANCE("Attendance"),
    REGISTER_STUDENT("Register"),
    RECORDS_SYNC("Records"),
    REPORTS("Reports")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SmartAttendanceTheme {
                val authViewModel: AuthViewModel = viewModel()
                val authState by authViewModel.uiState.collectAsStateWithLifecycle()

                when (authState.stage) {
                    AuthStage.LOADING -> LoadingScreen(
                        onLoadingComplete = { authViewModel.finishSplashLoading() }
                    )

                    AuthStage.AUTHENTICATED -> {
                        val admin = authState.authenticatedAdmin
                        if (admin != null) {
                            AuthorizedMainShell(
                                admin = admin,
                                onLogout = { authViewModel.logout() }
                            )
                        } else {
                            AdminAuthScreen(authViewModel = authViewModel)
                        }
                    }

                    else -> AdminAuthScreen(authViewModel = authViewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorizedMainShell(
    admin: AdminEntity,
    onLogout: () -> Unit,
    workspaceViewModel: MainWorkspaceViewModel = viewModel()
) {
    var currentDestination by rememberSaveable { mutableStateOf(MainDestination.HOME) }
    var showUserProfileDialog by remember { mutableStateOf(false) }
    val isOnline by workspaceViewModel.isOnline.collectAsStateWithLifecycle()
    val students by workspaceViewModel.students.collectAsStateWithLifecycle()
    val allAttendance by workspaceViewModel.allAttendance.collectAsStateWithLifecycle()
    val pendingStudents by workspaceViewModel.pendingStudentCount.collectAsStateWithLifecycle()
    val pendingAttendance by workspaceViewModel.pendingAttendanceCount.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val todayString = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
    val todayRecordsByStudent = remember(allAttendance, todayString) {
        allAttendance.filter { it.attendanceDate == todayString }.associateBy { it.studentUid }
    }
    val todayPresent = remember(todayRecordsByStudent) {
        todayRecordsByStudent.values.count { it.status == AttendanceStatus.PRESENT.name }
    }
    val todayAbsent = remember(todayRecordsByStudent) {
        todayRecordsByStudent.values.count { it.status == AttendanceStatus.ABSENT.name }
    }
    val todayRemaining = remember(students, todayRecordsByStudent) {
        students.count { !todayRecordsByStudent.containsKey(it.studentUid) }
    }

    val userThumb = remember(admin.adminUid) {
        if (admin.faceThumbnailBytes.isEmpty()) {
            null
        } else {
            runCatching {
                BitmapFactory.decodeByteArray(
                    admin.faceThumbnailBytes,
                    0,
                    admin.faceThumbnailBytes.size
                )
            }.getOrNull()
        }
    }

    if (drawerState.isOpen) {
        BackHandler {
            scope.launch { drawerState.close() }
        }
    } else if (currentDestination != MainDestination.HOME) {
        BackHandler {
            currentDestination = MainDestination.HOME
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.testTag("side_drawer_sheet")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Light-green header with user profile & school identity
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (userThumb != null) {
                                    Image(
                                        bitmap = userThumb.asImageBitmap(),
                                        contentDescription = "${admin.fullName} profile portrait",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(56.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = admin.fullName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = admin.designation,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = admin.mobileNormalized,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            Text(
                                text = SchoolConstants.APP_TITLE,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${SchoolConstants.SCHOOL_NAME} • EMIS ${SchoolConstants.EMIS_CODE}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    val drawerItemColors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        unselectedContainerColor = MaterialTheme.colorScheme.surface,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurface
                    )

                    MainDestination.entries.forEach { dest ->
                        val icon = when (dest) {
                            MainDestination.HOME -> Icons.Default.Home
                            MainDestination.ATTENDANCE -> Icons.Default.FactCheck
                            MainDestination.REGISTER_STUDENT -> Icons.Default.PersonAdd
                            MainDestination.RECORDS_SYNC -> Icons.Default.History
                            MainDestination.REPORTS -> Icons.Default.Assessment
                        }
                        val labelText = when (dest) {
                            MainDestination.HOME -> "Home"
                            MainDestination.ATTENDANCE -> "Start Marking Attendance"
                            MainDestination.REGISTER_STUDENT -> "Register Students"
                            MainDestination.RECORDS_SYNC -> "Class Profiles & Records"
                            MainDestination.REPORTS -> "Attendance Reports"
                        }
                        NavigationDrawerItem(
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(labelText) },
                            selected = currentDestination == dest,
                            onClick = {
                                if (dest == MainDestination.ATTENDANCE) {
                                    workspaceViewModel.startNextAutoScan()
                                }
                                currentDestination = dest
                                scope.launch { drawerState.close() }
                            },
                            colors = drawerItemColors,
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                                .testTag("drawer_item_${dest.name.lowercase()}")
                        )
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                        label = { Text("My Profile") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            showUserProfileDialog = true
                        },
                        colors = drawerItemColors,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .testTag("drawer_item_profile")
                    )

                    val pendingTotal = pendingStudents + pendingAttendance
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Sync, contentDescription = null) },
                        label = { Text("Sync Offline Records") },
                        badge = if (pendingTotal > 0) {
                            { Text("$pendingTotal") }
                        } else null,
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            workspaceViewModel.syncNow()
                            currentDestination = MainDestination.RECORDS_SYNC
                        },
                        colors = drawerItemColors,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .testTag("drawer_item_sync")
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                        label = { Text("Log Out") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onLogout()
                        },
                        colors = drawerItemColors,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .testTag("drawer_item_logout")
                    )

                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("hamburger_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open side menu"
                            )
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = SchoolConstants.SCHOOL_NAME,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "EMIS ${SchoolConstants.EMIS_CODE} • ${admin.fullName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                contentDescription = if (isOnline) "Online" else "Offline",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (isOnline) "Online" else "Offline",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
            IconButton(
                onClick = { showUserProfileDialog = true },
                modifier = Modifier.testTag("user_profile_button")
            ) {
                if (userThumb != null) {
                    Image(
                        bitmap = userThumb.asImageBitmap(),
                        contentDescription = "View user profile",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = "View user profile"
                    )
                }
            }
            IconButton(
                onClick = onLogout,
                modifier = Modifier.testTag("logout_button")
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Lock and log out"
                )
            }
        }
    )
}
        ) { innerPadding ->
            val contentMod = Modifier
                .fillMaxSize()
                .padding(innerPadding)

            when (currentDestination) {
                MainDestination.HOME -> HomeScreen(
                    admin = admin,
                    totalStudents = students.size,
                    todayPresent = todayPresent,
                    todayAbsent = todayAbsent,
                    todayRemaining = todayRemaining,
                    pendingSyncCount = pendingStudents + pendingAttendance,
                    onRegisterStudents = {
                        currentDestination = MainDestination.REGISTER_STUDENT
                    },
                    onStartMarkingAttendance = {
                        workspaceViewModel.startNextAutoScan()
                        currentDestination = MainDestination.ATTENDANCE
                    },
                    modifier = contentMod
                )

                MainDestination.ATTENDANCE -> AttendanceScreen(
                    adminUid = admin.adminUid,
                    viewModel = workspaceViewModel,
                    modifier = contentMod
                )

                MainDestination.REGISTER_STUDENT -> StudentRegistrationScreen(
                    adminUid = admin.adminUid,
                    viewModel = workspaceViewModel,
                    modifier = contentMod
                )

                MainDestination.RECORDS_SYNC -> RecordsSyncScreen(
                    viewModel = workspaceViewModel,
                    modifier = contentMod
                )

                MainDestination.REPORTS -> ReportsScreen(
                    viewModel = workspaceViewModel,
                    modifier = contentMod
                )
            }
        }
    }

    if (showUserProfileDialog) {
        UserProfileDialog(
            user = admin,
            onDismiss = { showUserProfileDialog = false }
        )
    }
}

@Composable
private fun UserProfileDialog(
    user: AdminEntity,
    onDismiss: () -> Unit
) {
    val portrait = remember(user.adminUid) {
        if (user.faceThumbnailBytes.isEmpty()) {
            null
        } else {
            runCatching {
                BitmapFactory.decodeByteArray(user.faceThumbnailBytes, 0, user.faceThumbnailBytes.size)
            }.getOrNull()
        }
    }
    val registeredOn = remember(user.createdAtEpochMs) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(user.createdAtEpochMs))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registered User Profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (portrait != null) {
                        Image(
                            bitmap = portrait.asImageBitmap(),
                            contentDescription = "${user.fullName} profile photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column {
                        Text(user.fullName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            user.designation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(color = VerifiedGreenBg, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                text = "Sync: ${user.syncStatus}",
                                color = VerifiedGreen,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Mobile Number: ${user.mobileNormalized}", style = MaterialTheme.typography.bodyMedium)
                        Text("CNIC: ${user.cnicNormalized}", style = MaterialTheme.typography.bodyMedium)
                        Text("Email: ${user.email}", style = MaterialTheme.typography.bodyMedium)
                        Text("Registered On: $registeredOn", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "CNIC Front: ${if (user.cnicFrontDocPath.isNotBlank()) "Stored (Encrypted)" else "Synced on server"}",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "CNIC Back: ${if (user.cnicBackDocPath.isNotBlank()) "Stored (Encrypted)" else "Synced on server"}",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "Employment Doc: ${if (user.employmentDocPath.isNotBlank()) "Stored (Encrypted)" else "Synced on server"}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
