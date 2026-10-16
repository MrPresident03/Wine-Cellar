package com.houseofvino.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SmokeUiTest {
    @Test
    fun appStarts() = runComposeUiTest {
        setContent { HouseOfVinoApp() }
        onNodeWithText("House of Vino").assertIsDisplayed()
    }
}
