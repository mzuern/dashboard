package com.productionboard.scanner

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

/**
 * Smoke test for the app's navigation flow. Requires a device/emulator
 * (not runnable in this development environment - see README's "Known
 * limitations"). Camera/Photo-Picker-dependent flows aren't exercised
 * here since they launch external system UI; see the manual
 * physical-device testing checklist for those.
 */
class PhotoCaptureNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainScreenShowsPrimaryActions() {
        composeRule.onNodeWithText("Take Photo").assertExists()
        composeRule.onNodeWithText("Choose Photos").assertExists()
        composeRule.onNodeWithText("Process Photos (0)").assertExists()
    }

    @Test
    fun settingsScreenOpensAndReturnsToPhotoCapture() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("Save Settings").assertExists()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithText("Take Photo").assertExists()
    }

    @Test
    fun calibrationScreenOpensWithoutAPhoto() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("Open Calibration").performClick()
        composeRule.onNodeWithText("Calibration").assertExists()
        composeRule.onNodeWithText("Cancel").performClick()
    }

    @Test
    fun uncalibratedBoardShowsWarningThatLinksToCalibration() {
        composeRule.onNodeWithText("Board not calibrated yet").assertExists()
        composeRule.onNodeWithText("Calibrate Now").performClick()
        composeRule.onNodeWithText("Calibration").assertExists()
    }
}
