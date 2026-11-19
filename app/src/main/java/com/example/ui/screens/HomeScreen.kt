package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.example.data.local.AdminEntity
import com.example.domain.SchoolConstants
import com.example.ui.theme.AbsentRed
import com.example.ui.theme.AbsentRedBg
import com.example.ui.theme.LeaveAmber
import com.example.ui.theme.LeaveAmberBg
import com.example.ui.theme.PureBlackText
import com.example.ui.theme.VerifiedGreen
import com.example.ui.theme.VerifiedGreenBg
import com.example.ui.theme.WelcomeNoteMintBg
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    admin: AdminEntity,
    totalStudents: Int,
    todayPresent: Int,
    todayAbsent: Int,
    todayRemaining: Int,
    pendingSyncCount: Int,
    onRegisterStudents: () -> Unit,
    onStartMarkingAttendance: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // School & User Welcome Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(28.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = SchoolConstants.APP_TITLE,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${SchoolConstants.SCHOOL_NAME} • EMIS ${SchoolConstants.EMIS_CODE}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Text(
                    text = "Welcome, ${admin.fullName} (${admin.designation})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (pendingSyncCount > 0) {
                    Text(
                        text = "$pendingSyncCount offline record(s) queued for automatic sync",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Animated Welcome Note Banner
        AnimatedWelcomeNoteBanner()

        // Today's Quick Attendance Overview
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HomeStatTile(
                label = "Students",
                value = totalStudents.toString(),
                fgColor = MaterialTheme.colorScheme.primary,
                bgColor = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.weight(1f)
            )
            HomeStatTile(
                label = "Present",
                value = todayPresent.toString(),
                fgColor = VerifiedGreen,
                bgColor = VerifiedGreenBg,
                modifier = Modifier.weight(1f)
            )
            HomeStatTile(
                label = "Absent",
                value = todayAbsent.toString(),
                fgColor = AbsentRed,
                bgColor = AbsentRedBg,
                modifier = Modifier.weight(1f)
            )
            HomeStatTile(
                label = "Remaining",
                value = todayRemaining.toString(),
                fgColor = LeaveAmber,
                bgColor = LeaveAmberBg,
                modifier = Modifier.weight(1f)
            )
        }

        // Primary Action 1: Start Marking Attendance
        Button(
            onClick = onStartMarkingAttendance,
            colors = ButtonDefaults.buttonColors(containerColor = VerifiedGreen),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .testTag("home_start_attendance_button")
        ) {
            Icon(Icons.Default.FactCheck, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Start Marking Attendance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }

        // Primary Action 2: Register Students
        OutlinedButton(
            onClick = onRegisterStudents,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .testTag("home_register_students_button")
        ) {
            Icon(Icons.Default.PersonAdd, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Register Students",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = SchoolConstants.DEVELOPER_CREDIT,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun AnimatedWelcomeNoteBanner() {
    val welcomeMessage = "Welcome to GPS Koz Shinoli \u2013 Smart Attendance App. Have a productive day!"
    val infiniteTransition = rememberInfiniteTransition(label = "welcome_note_marquee")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 12000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "welcome_note_progress"
    )

    Surface(
        color = WelcomeNoteMintBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = welcomeMessage }
            .testTag("home_animated_welcome_note")
    ) {
        Layout(
            content = {
                Text(
                    text = welcomeMessage,
                    color = PureBlackText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
                .padding(vertical = 10.dp)
        ) { measurables, constraints ->
            val textPlaceable = measurables.first().measure(
                constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)
            )
            val containerWidth = if (constraints.hasBoundedWidth) {
                constraints.maxWidth
            } else {
                textPlaceable.width
            }
            val totalDistance = (containerWidth + textPlaceable.width).toFloat()
            layout(width = containerWidth, height = textPlaceable.height) {
                val offsetX = (containerWidth - (progress * totalDistance)).roundToInt()
                textPlaceable.place(x = offsetX, y = 0)
            }
        }
    }
}

@Composable
private fun HomeStatTile(
    label: String,
    value: String,
    fgColor: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = fgColor,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = fgColor
            )
        }
    }
}
