package com.houseofvino.ui.feature.placeholder

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.houseofvino.ui.components.MessageState
import com.houseofvino.ui.components.ScreenHeader

/** Shown for tabs whose feature hasn't been built yet in this development build. */
@Composable
fun TabNotReadyScreen(title: String, body: String) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title)
        MessageState(title = "Not built yet", body = body)
    }
}
