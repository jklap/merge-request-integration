package net.ntworld.mergeRequestIntegrationIde.toolWindow.internal

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.patch.AbstractFilePatchInProgress.PatchChange
import com.intellij.openapi.vcs.changes.patch.FilePatchStatus
import com.intellij.openapi.vcs.changes.ui.ChangeNodeDecorator
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.openapi.vcs.changes.ui.ChangesTreeImpl
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.ui.tree.TreeUtil
import net.miginfocom.swing.MigLayout
import net.ntworld.mergeRequest.Comment
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
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.function.Predicate
import java.util.stream.Collectors
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

        panel.add(JPanel())
        panel.add(leftToolbar.component)
        panel.add(
            ToolbarUtil.createExpandAndCollapseToolbar(
                "${this::class.java.canonicalName}/toolbar-right",
                myTree
            )
        )
        panel
    }
    private val myTreeMouseListener = object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent?) {
            if (null === e) {
                return
            }

            if (e.clickCount == 2) {
                handleTreeItemSelected(myTree.selectionPath)
            }
        }
    }
    private val myKeyListener = object: KeyAdapter() {
        override fun keyPressed(e: KeyEvent?) {
            if (null === e) {
                return
            }
            if (e.keyCode == KeyEvent.VK_ENTER) {
                handleTreeItemSelected(myTree.selectionPath)
            }
        }
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
//        myTree.addMouseListener(myTreeMouseListener)
//        myTree.addKeyListener(myKeyListener)

        val reviewContext = projectServiceProvider.reviewContextManager.findDoingCodeReviewContext()
        if (null !== reviewContext) {
            myChangeNodeDecorator.reviewContext = reviewContext
            setChanges(reviewContext.providerData, reviewContext.reviewingChanges)
        } else {
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
        ApplicationManager.getApplication().invokeLater {
            myTree.setChangesToDisplay(changes)
        }
        myToolbar.isVisible = true
        myComponent.setContent(myTreeWrapper)
    }

    override fun hide() {
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
            val comments = reviewContext?.getCommentsByPath(ChangesUtil.getFilePath(change).path)
            if ( comments != null && ! comments.isEmpty() ) {
                val groups = CommentUtil.groupCommentsByThreadId(comments)
                myLogger.info("groups found: ${groups.keys}")
//                val parents = comments.filter { comment -> groups.containsKey(comment.parentId) }
//                myLogger.info("parents: $parents")
                val unresolved = groups.filter { entry -> ! entry.value[0].resolved }
//                myLogger.info("unresolved: $unresolved")
                component.append("   " + unresolved.size, SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, Color.RED))
                component.append("/" + groups.size, SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES)
            }
        }

        override fun preDecorate(change: Change, renderer: ChangesBrowserNodeRenderer, showFlatten: Boolean) {
            if ( reviewContext?.getChangeData(change, ReviewContext.HAS_VIEWED) == null ) {
                renderer.append(" [" + "new" + "] ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
            }
        }
    }

}