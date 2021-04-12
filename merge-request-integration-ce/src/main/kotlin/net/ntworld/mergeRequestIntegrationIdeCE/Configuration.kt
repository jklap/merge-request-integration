package net.ntworld.mergeRequestIntegrationIdeCE

import com.intellij.openapi.components.ServiceManager
import net.ntworld.mergeRequestIntegrationIde.ui.configuration.ConfigurationBase

class Configuration: ConfigurationBase(ServiceManager.getService(CommunityApplicationServiceProvider::class.java)) {
    override fun getId(): String {
        return "merge-request-integration-ce"
    }

    override fun getDisplayName(): String {
        return "GitLab MR Integration"
    }
}