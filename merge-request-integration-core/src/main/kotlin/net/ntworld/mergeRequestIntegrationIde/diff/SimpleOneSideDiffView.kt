package net.ntworld.mergeRequestIntegrationIde.diff

import com.intellij.diff.tools.simple.SimpleOnesideDiffViewer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.vcs.changes.Change
import net.ntworld.mergeRequest.Comment
import net.ntworld.mergeRequest.MergeRequest
import net.ntworld.mergeRequest.ProviderData
import net.ntworld.mergeRequestIntegrationIde.diff.gutter.*
import net.ntworld.mergeRequestIntegrationIde.service.ApplicationService

class SimpleOneSideDiffView(
    private val applicationService: ApplicationService,
    override val viewer: SimpleOnesideDiffViewer,
    private val change: Change,
    private val contentType: DiffView.ContentType
) : AbstractDiffView<SimpleOnesideDiffViewer>(applicationService, viewer) {

    override fun createGutterIcons() {
        for (logicalLine in 0 until viewer.editor.document.lineCount) {
            registerGutterIconRenderer(GutterIconRendererFactory.makeGutterIconRenderer(
                viewer.editor.markupModel.addLineHighlighter(logicalLine, HighlighterLayer.LAST, null),
                applicationService.settings.showAddCommentIconsInDiffViewGutter,
                logicalLine,
                visibleLineLeft = if (contentType == DiffView.ContentType.BEFORE) logicalLine + 1 else null,
                visibleLineRight = if (contentType == DiffView.ContentType.AFTER) logicalLine + 1 else null,
                contentType = contentType,
                action = dispatcher.multicaster::onGutterActionPerformed
            ))
        }
    }

    override fun changeGutterIconsByComments(
        visibleLine: Int,
        contentType: DiffView.ContentType,
        comments: List<Comment>
    ) {
        val gutterIconRenderer = findGutterIconRenderer(visibleLine - 1, contentType)
        gutterIconRenderer.setState(
            if (comments.size == 1) GutterState.THREAD_HAS_SINGLE_COMMENT else GutterState.THREAD_HAS_MULTI_COMMENTS
        )
    }

    override fun updateComments(visibleLine: Int, contentType: DiffView.ContentType, comments: List<Comment>) {
        updateComments(findGutterIconRenderer(visibleLine - 1, contentType), comments)
    }

    override fun displayEditorOnLine(
        providerData: ProviderData,
        mergeRequest: MergeRequest,
        logicalLine: Int,
        contentType: DiffView.ContentType,
        comments: List<Comment>
    ) {
        displayCommentsAndEditorOnLine(
            providerData, mergeRequest,
            viewer.editor,
            calcPosition(logicalLine),
            logicalLine, contentType,
            comments
        )
    }

    override fun toggleCommentsOnLine(
        providerData: ProviderData,
        mergeRequest: MergeRequest,
        logicalLine: Int,
        contentType: DiffView.ContentType,
        comments: List<Comment>
    ) {
        toggleCommentsOnLine(
            providerData, mergeRequest,
            viewer.editor,
            calcPosition(logicalLine),
            logicalLine, contentType,
            comments
        )
    }

    private fun calcPosition(logicalLine: Int): GutterPosition {
        return if (contentType == DiffView.ContentType.BEFORE) {
            GutterPosition(
                editorType = DiffView.EditorType.SINGLE_SIDE,
                changeType = findChangeType(viewer.editor, logicalLine),
                oldLine = logicalLine + 1,
                oldPath = change.beforeRevision!!.file.toString(),
                newLine = null,
                newPath = null,
                baseHash = change.beforeRevision!!.revisionNumber.asString()
            )
        } else {
            GutterPosition(
                editorType = DiffView.EditorType.SINGLE_SIDE,
                changeType = findChangeType(viewer.editor, logicalLine),
                newLine = logicalLine + 1,
                newPath = change.afterRevision!!.file.toString(),
                oldLine = null,
                oldPath = null,
                headHash = change.afterRevision!!.revisionNumber.asString()
            )
        }
    }
}