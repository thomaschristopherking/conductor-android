package build.conductor.android.client.ui.home

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import build.conductor.android.client.ComponentActivityRule
import build.conductor.android.client.FakeConductorRepository.Companion.workspace
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WorkspaceGroupListTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRule()).around(composeRule)

    @Test
    fun `each titled group shows its header above its workspaces`() {
        val groups = listOf(
            WorkspaceGroup("s1", "Interop", listOf(workspace("a", "Fix the importer"))),
            WorkspaceGroup("other", "Other workspaces", listOf(workspace("b", "Update the docs"))),
        )
        composeRule.setContent { WorkspaceGroupList(groups, onOpenWorkspace = {}) }

        val interopTop = composeRule.onNodeWithText("Interop").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
        val rowTop = composeRule.onNodeWithText("Fix the importer").fetchSemanticsNode().boundsInRoot.top
        val otherTop = composeRule.onNodeWithText("Other workspaces").fetchSemanticsNode().boundsInRoot.top
        assert(interopTop < rowTop && rowTop < otherTop) { "Expected Interop, its row, then Other workspaces" }
    }

    @Test
    fun `a tap on a workspace opens it`() {
        var opened: String? = null
        composeRule.setContent {
            WorkspaceGroupList(listOf(WorkspaceGroup("other", null, listOf(workspace("a", "Fix the importer")))), onOpenWorkspace = { opened = it.id })
        }

        composeRule.onNodeWithText("Fix the importer").performClick()

        assertEquals("a", opened)
    }
}
