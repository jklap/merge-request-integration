package net.ntworld.mergeRequestIntegrationIde.ui.mergeRequest.tab.commit

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.EventDispatcher
import net.ntworld.mergeRequest.Commit
import net.ntworld.mergeRequest.MergeRequestInfo
import net.ntworld.mergeRequest.ProviderData
import net.ntworld.mergeRequestIntegrationIde.ui.panel.CommitItemPanel
import net.ntworld.mergeRequestIntegrationIde.ui.util.CustomSimpleToolWindowPanel
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.ChangeListener

class CommitCollection : CommitCollectionUI {
    override val dispatcher = EventDispatcher.create(CommitCollectionUI.Listener::class.java)

    private val myComponent = CustomSimpleToolWindowPanel(vertical = true, borderless = true)
    private val myList = JPanel()
    private val myItems = mutableListOf<CommitItemPanel>()
    private var myProviderData: ProviderData? = null
    private var myMergeRequestInfo: MergeRequestInfo? = null
    private class MySelectAllButton(private val self: CommitCollection) : AnAction(
        "Select all", "Select all commits to see changes or do code review", null
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            val commits = self.myItems.map {
                it.isSelectable(selectable = true, selected = true)
                it.commit
            }
            self.dispatchCommitSelectedEvent(commits)
        }

        override fun displayTextInToolbar() = true
        override fun useSmallerFontForTextInToolbar() = true
    }
    private val mySelectAllButton = MySelectAllButton(this)

    private class MyUnselectAllButton(private val self: CommitCollection): AnAction(
        "Unselect all", "Unselect all commits then pick a single one to see changes or do code review", null
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            self.myItems.forEach {
                it.isSelectable(selectable = true, selected = false)
            }
            self.dispatchCommitSelectedEvent(listOf())
        }

        override fun displayTextInToolbar() = true
        override fun useSmallerFontForTextInToolbar() = true
    }
    private val myUnselectAllButton = MyUnselectAllButton(this)

    private val myCommitSelectChangeListener = ChangeListener {
        val list = myItems.map {
            Pair(it.commit, it.isSelected())
        }
        myItems.forEachIndexed { index, item ->
            item.isSelectable(CommitSelectUtil.canSelect(list, index))
        }

        val selectedCommits = mutableListOf<Commit>()
        for (item in myItems) {
            if (item.isSelected()) {
                selectedCommits.add(item.commit)
            }
        }
        dispatchCommitSelectedEvent(selectedCommits)
    }

    init {
        myList.layout = BoxLayout(myList, BoxLayout.Y_AXIS)
        myList.background = JBColor.background()

        myComponent.setContent(ScrollPaneFactory.createScrollPane(myList, true))
        var toolBar = createToolbar()
        toolBar.targetComponent = myList
        myComponent.toolbar = toolBar.component
    }

    override fun clear() {
        myList.removeAll()
        myItems.clear()
    }

    override fun disable() {
        myItems.forEach { it.disable() }
    }

    override fun enable() {
        myItems.forEach { it.enable() }
    }

    override fun setCommits(providerData: ProviderData, mergeRequestInfo: MergeRequestInfo, commits: Collection<Commit>) {
        clear()
        myProviderData = providerData
        myMergeRequestInfo = mergeRequestInfo
        commits.forEach {
            val item = CommitItemPanel(it, myCommitSelectChangeListener)
            myItems.add(item)
            myList.add(item.createComponent())
        }
    }

    override fun createComponent(): JComponent = myComponent

    private fun dispatchCommitSelectedEvent(commits: List<Commit>) {
        val providerData = myProviderData
        val mergeRequestInfo = myMergeRequestInfo
        if (null !== providerData && null !== mergeRequestInfo) {
            dispatcher.multicaster.commitsSelected(providerData, mergeRequestInfo, commits)
        }
    }

    private fun createToolbar() : ActionToolbar {
        val actionGroup = DefaultActionGroup()
        actionGroup.add(myUnselectAllButton)
        actionGroup.addSeparator()
        actionGroup.add(mySelectAllButton)

        return ActionManager.getInstance().createActionToolbar(
            "${CommitCollection::class.java.canonicalName}/toolbar",
            actionGroup,
            true
        )
    }
}