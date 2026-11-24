package com.fitnex.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.fitnex.app.MainUiState
import com.fitnex.app.MainViewModel

/** Same wiring as MainActivity: the session decides what is on screen. */
@Composable
fun TestApp() {
    val vm: MainViewModel = hiltViewModel()
    val state by vm.uiState.collectAsState()
    (state as? MainUiState.Ready)?.let { FitnexRoot(darkTheme = true, session = it.session) }
}

private const val TIMEOUT = 5_000L

/** Room and DataStore work on background threads; wait for real content instead of idling. */
fun AndroidComposeTestRule<*, *>.waitForText(text: String) =
    waitUntil(TIMEOUT) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

fun AndroidComposeTestRule<*, *>.waitForTag(tag: String) =
    waitUntil(TIMEOUT) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
