package net.ntworld.mergeRequestIntegrationIde.infrastructure

import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.vcs.BranchChangeListener
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.messages.MessageBus
import net.ntworld.foundation.Infrastructure
import net.ntworld.foundation.MemorizedInfrastructure
import net.ntworld.foundation.util.UUIDGenerator
import net.ntworld.mergeRequest.MergeRequest
import net.ntworld.mergeRequest.ProviderData
import net.ntworld.mergeRequest.ProviderInfo
import net.ntworld.mergeRequest.api.ApiCredentials
import net.ntworld.mergeRequest.request.PublishAllCommentsRequest
import net.ntworld.mergeRequest.request.PublishCommentsRequest
import net.ntworld.mergeRequestIntegration.DefaultProviderStorage
import net.ntworld.mergeRequestIntegration.ProviderStorage
import net.ntworld.mergeRequestIntegration.make
import net.ntworld.mergeRequestIntegration.provider.MemoryCache
import net.ntworld.mergeRequestIntegration.provider.ProviderException
import net.ntworld.mergeRequestIntegration.provider.github.Github
import net.ntworld.mergeRequestIntegration.provider.gitlab.Gitlab
import net.ntworld.mergeRequestIntegrationIde.ComponentFactory
import net.ntworld.mergeRequestIntegrationIde.DefaultComponentFactory
import net.ntworld.mergeRequestIntegrationIde.IdeInfrastructure
import net.ntworld.mergeRequestIntegrationIde.compatibility.IntellijIdeApi
import net.ntworld.mergeRequestIntegrationIde.debug
import net.ntworld.mergeRequestIntegrationIde.infrastructure.internal.ProviderSettingsImpl
import net.ntworld.mergeRequestIntegrationIde.infrastructure.internal.ReviewContextManagerImpl
import net.ntworld.mergeRequestIntegrationIde.infrastructure.internal.ServiceBase
import net.ntworld.mergeRequestIntegrationIde.infrastructure.notifier.MergeRequestDataNotifier
import net.ntworld.mergeRequestIntegrationIde.infrastructure.notifier.ProjectNotifier
import net.ntworld.mergeRequestIntegrationIde.infrastructure.notifier.ReworkEditorNotifier
import net.ntworld.mergeRequestIntegrationIde.infrastructure.notifier.SingleMRToolWindowNotifier
import net.ntworld.mergeRequestIntegrationIde.infrastructure.notifier.provider.MergeRequestDataProvider
import net.ntworld.mergeRequestIntegrationIde.infrastructure.service.FiltersStorageService
import net.ntworld.mergeRequestIntegrationIde.infrastructure.service.RepositoryFileService
import net.ntworld.mergeRequestIntegrationIde.infrastructure.service.internal.FiltersStorageServiceImpl
import net.ntworld.mergeRequestIntegrationIde.infrastructure.service.repositoryFile.CachedRepositoryFile
import net.ntworld.mergeRequestIntegrationIde.infrastructure.service.repositoryFile.LocalRepositoryFileService
import net.ntworld.mergeRequestIntegrationIde.infrastructure.setting.ApplicationSettings
import net.ntworld.mergeRequestIntegrationIde.rework.ReworkEditorManager
import net.ntworld.mergeRequestIntegrationIde.rework.ReworkManager
import net.ntworld.mergeRequestIntegrationIde.rework.internal.ReworkEditorManagerImpl
import net.ntworld.mergeRequestIntegrationIde.rework.internal.ReworkManagerImpl
import net.ntworld.mergeRequestIntegrationIde.task.RegisterProviderTask
import net.ntworld.mergeRequestIntegrationIde.ui.configuration.GithubConnectionsConfigurableBase
import net.ntworld.mergeRequestIntegrationIde.ui.configuration.GitlabConnectionsConfigurableBase
import org.jdom.Element
import com.intellij.openapi.project.Project as IdeaProject

abstract class AbstractProjectServiceProvider(
    final override val project: IdeaProject
) : ProjectServiceProvider, ServiceBase() {
    private val myNotification: NotificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("Merge Request Integration")

    private var myIsInitialized = false

    final override val providerStorage: ProviderStorage = DefaultProviderStorage()

    override val infrastructure: Infrastructure = MemorizedInfrastructure(IdeInfrastructure(providerStorage))

    override val applicationSettings: ApplicationSettings
        get() = applicationServiceProvider.settingsManager

    override val intellijIdeApi: IntellijIdeApi
        get() = applicationServiceProvider.intellijIdeApi

    final override val messageBus: MessageBus by lazy { project.messageBus }

    override val repositoryFile: RepositoryFileService by lazy {
        CachedRepositoryFile(
            LocalRepositoryFileService(this),
            MemoryCache()
        )
    }

    override val filtersStorage: FiltersStorageService = FiltersStorageServiceImpl(this)

    override val reviewContextManager: ReviewContextManager = ReviewContextManagerImpl(this)

    override val projectNotifierTopic: ProjectNotifier = messageBus.syncPublisher(ProjectNotifier.TOPIC)

    override val singleMRToolWindowNotifierTopic = messageBus.syncPublisher(SingleMRToolWindowNotifier.TOPIC)

    override val reworkManager: ReworkManager = ReworkManagerImpl(this)

    final override val reworkEditorManager: ReworkEditorManager = ReworkEditorManagerImpl(this)

    override val componentFactory: ComponentFactory = DefaultComponentFactory(this)

    private val myBranchChangeListener = object: BranchChangeListener {
        override fun branchWillChange(branchName: String) {
        }

        override fun branchHasChanged(branchName: String) {
            debug("BranchChangeListener triggered, request create ReworkWatcher for $branchName")
            reworkManager.requestCreateReworkWatcher(providerStorage.registeredProviders, branchName)
        }
    }

    private val myFileEditorManagerListener = object: FileEditorManagerListener {
        override fun selectionChanged(event: FileEditorManagerEvent) {
            val editor = event.newEditor
            if (editor !is TextEditor) {
                return
            }
            val providers = providerStorage.registeredProviders
            for (provider in providers) {
                val reworkWatcher = reworkManager.findActiveReworkWatcher(provider)
                if (null !== reworkWatcher) {
                    ApplicationManager.getApplication().invokeLater {
                        reworkEditorManager.bootstrap(editor, reworkWatcher)
                    }
                }
            }
        }
    }

    init {
        messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, myFileEditorManagerListener)
        messageBus.connect().subscribe(ReworkEditorNotifier.TOPIC, reworkEditorManager)
    }

    protected fun initWithApplicationServiceProvider(applicationSP: ApplicationServiceProvider) {
        applicationSP.watcherManager.addWatcher(reviewContextManager)

        val connection = messageBus.connect(project)
        connection.subscribe(MergeRequestDataNotifier.TOPIC, MergeRequestDataProvider(this, messageBus))
        connection.subscribe(BranchChangeListener.VCS_BRANCH_CHANGED, myBranchChangeListener)
    }

//    override fun readStateItem(item: Element, id: String, settings: ProviderSettings) {
//        super.readProviderStateItem(item, id, settings)
//        filtersStorage.readFrom(item, id)
//    }

//    override fun writeStateItem(item: Element, id: String, settings: ProviderSettings) {
//        super.writeProviderStateItem(item, id, settings)
//        filtersStorage.writeTo(item, id)
//    }

    override fun addProviderConfiguration(
        id: String,
        info: ProviderInfo,
        credentials: ApiCredentials,
        repository: String
    ) {
        providerSettingsData[id] = ProviderSettingsImpl(
            id = id,
            info = info,
            credentials = encryptCredentials(info, credentials),
            repository = repository
        )
    }

    override fun removeProviderConfiguration(id: String) {
        providerSettingsData.remove(id)
    }

    override fun getProviderConfigurations(): List<ProviderSettings> {
        return providerSettingsData.values.map {
            ProviderSettingsImpl(
                id = it.id,
                info = it.info,
                credentials = decryptCredentials(it.info, it.credentials),
                repository = it.repository
            )
        }
    }

    override fun initialize() {
        myIsInitialized = false
        providerStorage.clear()
        reworkManager.clear()
        projectNotifierTopic.starting()

        getProviderConfigurations().forEach { registerProviderSettings(it) }
        projectNotifierTopic.initialized()
        myIsInitialized = true
    }

    override fun isInitialized(): Boolean {
        return myIsInitialized
    }

    override fun openSingleMRToolWindow(invoker: (() -> Unit)?) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(
            applicationServiceProvider.singleMRToolWindowName
        )
        if (null !== toolWindow) {
            toolWindow.show(invoker)
        }
    }

    override fun hideSingleMRToolWindow(invoker: (() -> Unit)?) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(
            applicationServiceProvider.singleMRToolWindowName
        )
        if (null !== toolWindow) {
            toolWindow.hide(invoker)
        }
    }

    override fun isDoingCodeReview(): Boolean = null !== reviewContextManager.findDoingCodeReviewContext()

    override fun onApplicationSettingsChanged(old: ApplicationSettings, new: ApplicationSettings) {
        if (!old.enableReworkProcess && new.enableReworkProcess) {
            for (provider in providerStorage.registeredProviders) {
                reworkManager.createBranchWatcher(provider)
            }
        }
    }

    override fun startCodeReview(providerData: ProviderData, mergeRequest: MergeRequest) {
        reviewContextManager.setContextToDoingCodeReview(providerData.id, mergeRequest.id)
        val reviewContext = reviewContextManager.findDoingCodeReviewContext()
        if (null !== reviewContext) {
            projectNotifierTopic.startCodeReview(reviewContext)
            openSingleMRToolWindow {
                singleMRToolWindowNotifierTopic.showChangesWhenDoingCodeReview(
                    reviewContext.providerData, reviewContext.changes
                )
            }
        }
    }

    override fun stopCodeReview() {
        val reviewContext = reviewContextManager.findDoingCodeReviewContext()
        if (null !== reviewContext) {
            infrastructure.serviceBus() process PublishAllCommentsRequest.make(
                providerId = reviewContext.providerData.id,
                mergeRequestId = reviewContext.mergeRequestInfo.id
            ) ifError {
                notify(
                    "There was an error from server. \n\n ${it.message}",
                    NotificationType.ERROR
                )
                throw ProviderException(it)
            }

            projectNotifierTopic.stopCodeReview(reviewContext)
            reviewContext.closeAllChanges()
            hideSingleMRToolWindow {
                singleMRToolWindowNotifierTopic.hideChangesAfterDoingCodeReview()
                singleMRToolWindowNotifierTopic.clearReworkTabs()
            }
        }
        println("After doing code review")
        providerStorage.registeredProviders.forEach {
            reworkManager.createBranchWatcher(it)
        }
        reviewContextManager.clearContextDoingCodeReview()
    }

    override fun notify(message: String) {
        notify(message, NotificationType.INFORMATION)
    }

    override fun notify(message: String, type: NotificationType) {
        val notification = myNotification.createNotification(message, type)
        notification.notify(project)
    }

    private fun registerProviderSettings(settings: ProviderSettings) {
        var name = ""
        if (settings.info.id == Gitlab.id) {
            name = GitlabConnectionsConfigurableBase.findNameFromId(settings.id)
        }
        if (settings.info.id == Github.id) {
            name = GithubConnectionsConfigurableBase.findNameFromId(settings.id)
        }

        val task = RegisterProviderTask(
            this,
            id = UUIDGenerator.generate(),
            name = name,
            settings = settings,
            listener = object : RegisterProviderTask.Listener {
                override fun providerRegistered(providerData: ProviderData) {
                    projectNotifierTopic.providerRegistered(providerData)
                    reworkManager.createBranchWatcher(providerData)
                }
            }
        )
        task.start()
    }
}