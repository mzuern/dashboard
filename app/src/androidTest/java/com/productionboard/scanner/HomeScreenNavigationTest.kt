package com.productionboard.scanner

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

/**
 * Smoke test for the entry navigation flow. Requires a device/emulator
 * (not runnable in this development environment - see README's "Known
 * limitations"). Camera-dependent flows (Scan Board) aren't exercised
 * here; see the manual physical-device testing checklist for those.
 */
class HomeScreenNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreenShowsPrimaryActions() {
        composeRule.onNodeWithText("Scan Board").assertExists()
        composeRule.onNodeWithText("Manual Entry").assertExists()
        composeRule.onNodeWithText("Settings").assertExists()
    }

    @Test
    fun manualEntryNavigatesAndBackReturnsHome() {
        composeRule.onNodeWithText("Manual Entry").performClick()
        composeRule.onNodeWithText("Add Row").assertExists()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithText("Scan Board").assertExists()
    }

    @Test
    fun settingsScreenOpensAndReturnsHome() {
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Save Settings").assertExists()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithText("Scan Board").assertExists()
    }
}
