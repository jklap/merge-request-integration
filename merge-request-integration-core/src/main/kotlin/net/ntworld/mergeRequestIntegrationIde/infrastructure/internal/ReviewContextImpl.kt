package net.ntworld.mergeRequestIntegrationIde.infrastructure.internal

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.PreviewDiffVirtualFile
import com.intellij.util.messages.MessageBusConnection
import git4idea.repo.GitRepository
import net.ntworld.mergeRequest.*
import net.ntworld.mergeRequestIntegrationIde.infrastructure.ProjectServiceProvider
import net.ntworld.mergeRequestIntegrationIde.infrastructure.ReviewContext
import net.ntworld.mergeRequestIntegrationIde.util.RepositoryUtil

class ReviewContextImpl(
    val projectServiceProvider: ProjectServiceProvider,
    override val providerData: ProviderData,
    override val mergeRequestInfo: MergeRequestInfo,
    override val messageBusConnection: MessageBusConnection
) : ReviewContext {
    private val myLogger = Logger.getInstance(this.javaClass)

    // private val myPreviewDiffVirtualFileMap = mutableMapOf<Change, PreviewDiffVirtualFile>()
    private val myPreviewDiffVirtualFileMap = mutableMapOf<Int, PreviewDiffVirtualFile>()
    private val myCommentsMap = mutableMapOf<String, MutableList<Comment>>()
    private val myChangesMap = mutableMapOf<String, MutableList<Change>>()
    private val myChangesDataMap = mutableMapOf<Change, UserDataHolderBase>()

    override val project: Project = projectServiceProvider.project

    override var diffReference: DiffReference? = null

    override val repository: GitRepository? = RepositoryUtil.findRepository(projectServiceProvider, providerData)

    override var commits: List<Commit> = listOf()

    override var comments: List<Comment> = listOf()
        set(value) {
            field = value
            buildCommentsMap(value)
        }

    override var changes: List<Change> = listOf()
        set(value) {
            field = value
            buildChangesMap(value)
        }

    override var reviewingCommits: List<Commit> = listOf()

    override var reviewingChanges: List<Change> = listOf()

    override fun findChangeByPath(path: String): Change? {
        val absolutePath = RepositoryUtil.findAbsoluteCrossPlatformsPath(repository, path)
        val changes = myChangesMap[absolutePath]
        if (null === changes) {
            return null
        }

        // TODO: do something when has more than 1 change
        if (changes.size > 1) {
            myLogger.info("There is more than 1 change for $path")
        }
        return changes.first()
    }

    override fun getCommentsByPath(path: String): List<Comment> {
        val crossPlatformsPath = RepositoryUtil.transformToCrossPlatformsPath(path)
        val comments = myCommentsMap[crossPlatformsPath]
        if (null !== comments) {
            return comments
        }
        myLogger.debug("There are no comments for $crossPlatformsPath")
        return listOf()
    }

    override fun openChange(change: Change, focus: Boolean, displayMergeRequestId: Boolean) {
        val diffFile = myPreviewDiffVirtualFileMap[change.hashCode()]
        if (null === diffFile) {
            val provider = DiffPreviewProviderImpl(project, change, this, displayMergeRequestId)
            val created = PreviewDiffVirtualFile(provider)
            myPreviewDiffVirtualFileMap[change.hashCode()] = created
            FileEditorManagerEx.getInstanceEx(project).openFile(created, focus)
        } else {
            FileEditorManagerEx.getInstanceEx(project).openFile(diffFile, focus)
        }
        putChangeData(change, ReviewContext.HAS_VIEWED, true)
    }

    override fun hasAnyChangeOpened(): Boolean {
        return myPreviewDiffVirtualFileMap.isNotEmpty()
    }

    override fun closeAllChanges() {
        val fileEditorManagerEx = FileEditorManagerEx.getInstanceEx(project)
        myPreviewDiffVirtualFileMap.forEach { (_, diffFile) ->
            fileEditorManagerEx.closeFile(diffFile)
        }
        myPreviewDiffVirtualFileMap.clear()
    }

    override fun <T> getChangeData(change: Change, key: Key<T>): T? {
        val userDataHolder = myChangesDataMap[change]
        return if (null !== userDataHolder) {
            userDataHolder.getUserData(key)
        } else null
    }

    override fun <T> putChangeData(change: Change, key: Key<T>, value: T?) {
        val userDataHolder = myChangesDataMap[change]
        if (null === userDataHolder) {
            val dataHolder = UserDataHolderBase()
            dataHolder.putUserData(key, value)
            myChangesDataMap[change] = dataHolder
        } else {
            userDataHolder.putUserData(key, value)
        }
    }

    private fun buildCommentsMap(value: Collection<Comment>) {
        if (null === repository) {
            return
        }
        myCommentsMap.clear()
        for (comment in value) {
            myLogger.info("processing comment ${comment.id} w/parent ${comment.parentId} w/reply ${comment.replyId} and resolved ${comment.resolved}")
            val position = comment.position
            if (null === position) {
                continue
            }
            if (null !== position.newPath) {
                doHashComment(repository, position.newPath!!, comment)
            }
            if (null !== position.oldPath) {
                doHashComment(repository, position.oldPath!!, comment)
            }
        }
        myLogger.debug("myCommentsMap was built successfully")
        myCommentsMap.forEach { (path, comments) ->
            val commentIds = comments.map { it.id }
            myLogger.info("$path contains comment ids ${commentIds.joinToString(",")}")
        }
    }

    private fun doHashComment(repository: GitRepository, path: String, comment: Comment) {
        val fullPath = RepositoryUtil.findAbsoluteCrossPlatformsPath(repository, path)
        val list = myCommentsMap[fullPath]
        if (null === list) {
            myCommentsMap[fullPath] = mutableListOf(comment)
        } else {
            if (!list.contains(comment)) {
                list.add(comment)
            }
        }
    }

    private fun buildChangesMap(value: Collection<Change>) {
        myChangesMap.clear()
        for (change in value) {
            val filePaths = ChangesUtil.getPathsCaseSensitive(change)
            for (filePath in filePaths) {
                val path = filePath.path
                val list = myChangesMap.get(path)
                if (null === list) {
                    myChangesMap[path] = mutableListOf(change)
                } else {
                    if (!list.contains(change)) {
                        list.add(change)
                    }
                }
            }
        }
    }

}
