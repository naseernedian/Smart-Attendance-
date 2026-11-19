package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.LoadingScreen
import com.example.ui.theme.SmartAttendanceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `read app name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Smart Attendance", appName)
    }

    @Test
    fun `loading screen displays all required school and developer details`() {
        composeRule.setContent {
            SmartAttendanceTheme {
                LoadingScreen(onLoadingComplete = {})
            }
        }
        composeRule.onNodeWithText("SMART ATTENDANCE APP").assertIsDisplayed()
        composeRule.onNodeWithText("GPS Koz Shinoli").assertIsDisplayed()
        composeRule.onNodeWithText("EMIS Code: 61673").assertIsDisplayed()
        composeRule.onNodeWithText("Developed by Naseer Ud Din Shinwari").assertIsDisplayed()
    }
}
