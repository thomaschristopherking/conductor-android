package build.conductor.android.client.ui.workspace

import android.app.Application
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import build.conductor.android.client.ComponentActivityRule
import build.conductor.android.client.data.AgentStatus
import build.conductor.android.client.data.api.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SessionTabRowTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRule()).around(composeRule)

    private val sessions = listOf(
        SessionRow(Session("s1", "conductor://1", "Fix the importer"), AgentStatus.IDLE),
        SessionRow(Session("s2", "conductor://2", null), AgentStatus.WORKING),
    )

    @Test
    fun `a tap on a tab selects that session`() {
        var selected: String? = null
        composeRule.setContent { SessionTabRow(sessions, selectedIndex = 0, onSelect = { selected = it }, onNewSession = {}) }

        composeRule.onNodeWithText("Fix the importer").assertIsSelected()
        composeRule.onNodeWithText("Untitled session").performClick()

        assertEquals("s2", selected)
    }

    @Test
    fun `the last tab starts a new session`() {
        var isNewSessionRequested = false
        composeRule.setContent { SessionTabRow(emptyList(), selectedIndex = null, onSelect = {}, onNewSession = { isNewSessionRequested = true }) }

        composeRule.onNodeWithContentDescription("New session").performClick()

        assertTrue(isNewSessionRequested)
    }
}
