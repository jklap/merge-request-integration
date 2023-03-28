package net.ntworld.mergeRequestIntegrationIde.toolWindow.internal

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.ui.ChangeNodeDecorator
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.openapi.vcs.changes.ui.ChangesTreeImpl
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.tree.TreeUtil
import net.miginfocom.swing.MigLayout
import net.ntworld.mergeRequest.ProviderData
import net.ntworld.mergeRequestIntegrationIde.infrastructure.ProjectServiceProvider
import net.ntworld.mergeRequestIntegrationIde.infrastructure.ReviewContext
import net.ntworld.mergeRequestIntegrationIde.toolWindow.FilesToolWindowTab
import net.ntworld.mergeRequestIntegrationIde.ui.mergeRequest.tab.commit.CommitChanges
import net.ntworld.mergeRequestIntegrationIde.ui.util.CustomSimpleToolWindowPanel
import net.ntworld.mergeRequestIntegrationIde.ui.util.ToolbarUtil
import net.ntworld.mergeRequestIntegrationIde.util.CommentUtil
import java.awt.Color
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath


class FilesToolWindowTabImpl(
    private val projectServiceProvider: ProjectServiceProvider,
    override val providerData: ProviderData,
    override val isCodeReviewChanges: Boolean
) : FilesToolWindowTab {
    private val myLogger = Logger.getInstance(this.javaClass)

    private var myProviderData: ProviderData? = null
    private val myComponentEmpty = JPanel()
    private val myLabelEmpty = JLabel("", SwingConstants.CENTER)
    private val myComponent = CustomSimpleToolWindowPanel(vertical = true, borderless = true)
    private val myChangeNodeDecorator = MyChangeNodeDecorator()
    private val myTree = MyTree(projectServiceProvider.project, myChangeNodeDecorator)
    private val myTreeWrapper = ScrollPaneFactory.createScrollPane(myTree, true)
    private val myToolbar by lazy {
        val panel = JPanel(MigLayout("ins 0, fill", "[left]0[left, fill]push[right]", "center"))

        val leftActionGroup = DefaultActionGroup()
        val leftToolbar = ActionManager.getInstance().createActionToolbar(
            "${CommitChanges::class.java.canonicalName}/toolbar-left",
            leftActionGroup,
            true
        )
        leftToolbar.targetComponent = panel

        panel.add(JPanel())
        panel.add(leftToolbar.component)

        val toolBar =  ToolbarUtil.createExpandAndCollapseToolbar(
                "${this::class.java.canonicalName}/toolbar-right",
                myTree
        )
        toolBar.targetComponent = panel

        panel.add(toolBar.component)
        panel
    }

    init {
        myLabelEmpty.text = "<html>Files' changes will be displayed when you do Code Review<br/>or<br/>open a branch which has an opened Merge Request</html>"
        myComponentEmpty.background = JBColor.background()
        myComponentEmpty.layout = GridBagLayout()
        myComponentEmpty.add(myLabelEmpty)
        myComponent.toolbar = myToolbar
        myTree.setDoubleClickAndEnterKeyHandler {
            handleTreeItemSelected(myTree.selectionPath)
        }

        val reviewContext = projectServiceProvider.reviewContextManager.findDoingCodeReviewContext()
        if (null !== reviewContext) {
            myChangeNodeDecorator.reviewContext = reviewContext
            setChanges(reviewContext.providerData, reviewContext.reviewingChanges)
        } else {
            myLogger.info("closing (2)")
            hide()
        }
    }

    override val component: JComponent = myComponent

    private fun handleTreeItemSelected(path: TreePath?) {
        if (null === path) {
            return
        }

        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
        val change = node.userObject as? Change ?: return
        val reviewContext = projectServiceProvider.reviewContextManager.findDoingCodeReviewContext()
        if (null !== reviewContext) {
            reviewContext.openChange(change, focus = true, displayMergeRequestId = false)
            TreeUtil.repaintPath(myTree, path)
            return
        }

        val providerData = myProviderData
        if (null !== providerData) {
            val reworkWatcher = projectServiceProvider.reworkManager.findReworkWatcherByChange(
                providerData,
                change
            )
            if (null !== reworkWatcher) {
                reworkWatcher.openChange(change)
            }
        }
    }

    override fun setChanges(providerData: ProviderData, changes: List<Change>) {
        myProviderData = providerData
        myLogger.info("from", Exception("here"))
        myLogger.info("original changes from:")
        changes.forEach { change -> myLogger.info("$change") }
        // filter the list of changes

        val filteredChanges = ArrayList<Change>()
        changes.forEach { change ->
            if ( filteredChanges.contains(change) ) {
                // ignore it
            } else {
                // TODO: check to see if it exists but with a state change (like moved)
                filteredChanges.add(change)
            }
        }
        myLogger.info("setting changes from:")
        filteredChanges.forEach { change -> myLogger.info("$change.") }

        ApplicationManager.getApplication().invokeLater {
            myTree.setChangesToDisplay(filteredChanges)
        }
        myToolbar.isVisible = true
        myComponent.setContent(myTreeWrapper)
    }

    override fun hide() {
        myLogger.info("closing via hide()")
        myTree.setChangesToDisplay(listOf())
        myToolbar.isVisible = false
        myComponent.setContent(myComponentEmpty)
    }

    private class MyTree(ideaProject: Project, decorator: MyChangeNodeDecorator) : ChangesTreeImpl<Change>(
        ideaProject, false, false, Change::class.java
    ) {
        private val myChangeNodeDecorator = decorator
        override fun buildTreeModel(changes: MutableList<out Change>): DefaultTreeModel {
            return TreeModelBuilder.buildFromChanges(myProject, grouping, changes, myChangeNodeDecorator)
        }
    }

    private class MyChangeNodeDecorator() : ChangeNodeDecorator {
        private val myLogger = Logger.getInstance(this.javaClass)

        var reviewContext: ReviewContext? = null

        override fun decorate(change: Change, component: SimpleColoredComponent, isShowFlatten: Boolean) {
            if ( reviewContext == null  ) {
                // don't display during rework
                return
            }
            val comments = reviewContext?.getCommentsByPath(ChangesUtil.getFilePath(change).path)
            if ( comments != null && ! comments.isEmpty() ) {
                val groups = CommentUtil.groupCommentsByThreadId(comments)
                myLogger.debug("groups found: ${groups.keys}")
                val unresolved = groups.filter { entry -> ! entry.value[0].resolved }
                component.append("   " + unresolved.size, SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, Color.RED))
                component.append("/" + groups.size, SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES)
            }
        }

        override fun preDecorate(change: Change, renderer: ChangesBrowserNodeRenderer, showFlatten: Boolean) {
            if ( reviewContext == null ) {
                // don't display during rework
                return
            }
            val viewedRevision = reviewContext?.getChangeData(change, ReviewContext.HAS_VIEWED)
            if ( viewedRevision == null ) {
                // TODO: doesn't get removed when a deleted file has been reviewed
                renderer.append(" [" + "new" + "] ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
            } else {
                // check revision
                if ( viewedRevision != change.afterRevision?.revisionNumber?.asString()) {
                    renderer.append(" [" + "updated" + "] ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                }
            }
        }
    }

}