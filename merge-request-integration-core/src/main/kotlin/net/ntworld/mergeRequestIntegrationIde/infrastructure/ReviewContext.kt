package net.ntworld.mergeRequestIntegrationIde.infrastructure

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.Change
import com.intellij.util.messages.MessageBusConnection
import git4idea.repo.GitRepository
import net.ntworld.mergeRequest.*

interface ReviewContext {
    val project: Project

    val providerData: ProviderData

    val mergeRequestInfo: MergeRequestInfo

    val messageBusConnection: MessageBusConnection

    val diffReference: DiffReference?

    val repository: GitRepository?

    val commits: List<Commit>

    val comments: List<Comment>

    val changes: List<Change>

    val reviewingCommits: List<Commit>

    val reviewingChanges: List<Change>

    fun findChangeByPath(path: String): Change?

    fun getCommentsByPath(path: String): List<Comment>

    fun openChange(change: Change, focus: Boolean, displayMergeRequestId: Boolean)

    fun hasAnyChangeOpened(): Boolean

    fun closeAllChanges()

    fun <T> getChangeData(change: Change, key: Key<T>): T?

    fun <T> putChangeData(change: Change, key: Key<T>, value: T?)

    companion object {
        val KEY = Key.create<ReviewContext>("mri.ReviewContext")
        val HAS_VIEWED = Key.create<Boolean>("hasViewed")
    }
}
