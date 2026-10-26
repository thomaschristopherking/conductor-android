package build.conductor.android.client.ui.home

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import build.conductor.android.client.ComponentActivityRule
import build.conductor.android.client.FakeConductorRepository.Companion.workspace
import build.conductor.android.client.data.github.StackPosition
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
        composeRule.setContent { WorkspaceGroupList(groups, emptyMap(), WorkspaceRowActions(onOpen = {}, onArchive = {})) }

        val interopTop = composeRule.onNodeWithText("Interop").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
        val rowTop = composeRule.onNodeWithText("Fix the importer").fetchSemanticsNode().boundsInRoot.top
        val otherTop = composeRule.onNodeWithText("Other workspaces").fetchSemanticsNode().boundsInRoot.top
        assert(interopTop < rowTop && rowTop < otherTop) { "Expected Interop, its row, then Other workspaces" }
    }

    @Test
    fun `a tap on a workspace opens it`() {
        var opened: String? = null
        composeRule.setContent {
            WorkspaceGroupList(listOf(WorkspaceGroup("other", null, listOf(workspace("a", "Fix the importer")))), emptyMap(), WorkspaceRowActions(onOpen = { opened = it.id }, onArchive = {}))
        }

        composeRule.onNodeWithText("Fix the importer").performClick()

        assertEquals("a", opened)
    }

    @Test
    fun `a workspace in a stack shows its position, and a workspace outside a stack shows none`() {
        val groups = listOf(WorkspaceGroup("other", null, listOf(workspace("a", "Fix the importer"), workspace("b", "Update the docs"))))
        composeRule.setContent { WorkspaceGroupList(groups, mapOf("a" to StackPosition(3, 12)), WorkspaceRowActions(onOpen = {}, onArchive = {})) }

        composeRule.onNodeWithContentDescription("Pull request 3 of 12 in a stack").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("in a stack", substring = true).assertCountEquals(1)
    }
}
