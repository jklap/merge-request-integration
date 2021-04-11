package net.ntworld.mergeRequestIntegrationIde.infrastructure.internal

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.diagnostic.Logger
import net.ntworld.mergeRequest.ProviderInfo
import net.ntworld.mergeRequest.api.ApiCredentials
import net.ntworld.mergeRequestIntegration.provider.gitlab.Gitlab
import net.ntworld.mergeRequestIntegrationIde.infrastructure.ProviderSettings
import net.ntworld.mergeRequestIntegrationIde.infrastructure.ReviewState
import org.jdom.Element

open class ServiceBase : PersistentStateComponent<Element> {
    private val myLogger = Logger.getInstance(this.javaClass)

    protected val providerSettingsData = mutableMapOf<String, ProviderSettings>()
    protected val reviewSettingsData = mutableMapOf<String, ReviewState>()

    private val supportedProviders: List<ProviderInfo> = listOf(
        Gitlab
        // Gitlab, Github
    )

    override fun getState(): Element? {
        val element = Element("Providers")
        providerSettingsData.values.map {
            val item = Element("Provider")
            item.setAttribute("id", it.id)
            writeProviderStateItem(item, it.id, it)
            element.addContent(item)
        }
        myLogger.debug("settings $reviewSettingsData")
        reviewSettingsData.values.map {
            val item = Element("Review")
            item.setAttribute("id", it.id)
            writeReviewStateItem(item, it.id, it)
            element.addContent(item)
        }
        return element
    }

    protected open fun writeProviderStateItem(item: Element, id: String, settings: ProviderSettings) {
        item.setAttribute("providerId", settings.info.id)
        item.setAttribute("url", settings.credentials.url)
        item.setAttribute("login", settings.credentials.login)
        item.setAttribute("projectId", settings.credentials.projectId)
        item.setAttribute("version", settings.credentials.version)
        item.setAttribute("info", settings.credentials.info)
        item.setAttribute("ignoreSSLCertificateErrors", if (settings.credentials.ignoreSSLCertificateErrors) "1" else "0")
        item.setAttribute("repository", settings.repository)
    }

    protected open fun writeReviewStateItem(item: Element, id: String, review: ReviewState) {
        review.data.forEach { c, d ->
            d.forEach { k, v ->
                val ele = Element("Change")
                ele.setAttribute("change", c)
                ele.setAttribute("key", k)
                ele.setAttribute("value", v)
                item.addContent(ele)
            }
        }
    }

    override fun loadState(state: Element) {
        for (item in state.children) {
            if (item.name == "Item" || item.name == "Provider") {
                loadProvider(item)
            } else if (item.name == "Review") {
                loadReviewState(item)
            }
        }
    }

    private fun loadProvider(item: Element) {
        val info = supportedProviders.firstOrNull { it.id == item.getAttribute("providerId").value }
        if (null === info) {
            return
        }
        val credentials = ApiCredentialsImpl(
            url = item.getAttribute("url").value,
            login = item.getAttribute("login").value,
            token = "",
            projectId = item.getAttribute("projectId").value,
            version = item.getAttribute("version").value,
            info = item.getAttribute("info").value,
            ignoreSSLCertificateErrors = shouldIgnoreSSLCertificateErrors(item)
        )
        val id = item.getAttribute("id").value
        val settings = ProviderSettingsImpl(
            id = id.trim(),
            info = info,
            credentials = decryptCredentials(info, credentials),
            repository = item.getAttribute("repository").value
        )
        readProviderStateItem(item, id, settings)
    }

    private fun loadReviewState(item: Element) {
        myLogger.debug("loading state $item")
        val id = item.getAttribute("id").value
        val data = mutableMapOf<String, MutableMap<String, String>>()
        item.children.forEach { c ->
            val change = c.getAttribute("change").value
            val key = c.getAttribute("key").value
            val value = c.getAttribute("value").value
            if ( ! data.containsKey(change) ) {
                data[change] = mutableMapOf()
            }
            data[change]?.put(key, value)
        }
        val state = ReviewStateImpl(
            id = id,
            data = data
        )
        readReviewStateItem(item, id, state)
    }

    protected open fun readProviderStateItem(item: Element, id: String, settings: ProviderSettings) {
        providerSettingsData[id] = settings
    }

    protected open fun readReviewStateItem(item: Element, id: String, state: ReviewStateImpl) {
        reviewSettingsData[id] = state
    }

    private fun shouldIgnoreSSLCertificateErrors(item: Element): Boolean {
        val attribute = item.getAttribute("ignoreSSLCertificateErrors")
        if (null === attribute) {
            return false
        }
        return attribute.value == "1" || attribute.value.toLowerCase() == "true"
    }

    protected fun encryptCredentials(info: ProviderInfo, credentials: ApiCredentials): ApiCredentials {
        encryptPassword(info, credentials, credentials.token)
        return ApiCredentialsImpl(
            url = credentials.url,
            // -----------------------------------------------------------------
            // Always bind login and token because if we don't the token will be
            // empty if the state not stored to the storage yet.
            // It's safe because the secret is stored on memory only.
            login = credentials.login,
            token = credentials.token,
            // -----------------------------------------------------------------
            projectId = credentials.projectId,
            version = credentials.version,
            info = credentials.info,
            ignoreSSLCertificateErrors = credentials.ignoreSSLCertificateErrors
        )
    }

    protected fun decryptCredentials(info: ProviderInfo, credentials: ApiCredentials): ApiCredentials {
        return ApiCredentialsImpl(
            url = credentials.url,
            // -----------------------------------------------------------------
            // Always bind login and token because if we don't the token will be
            // empty if the state not stored to the storage yet.
            // It's safe because the secret is stored on memory only.
            login = credentials.login,
            token = decryptPassword(info, credentials) ?: credentials.token,
            // -----------------------------------------------------------------
            projectId = credentials.projectId,
            version = credentials.version,
            info = credentials.info,
            ignoreSSLCertificateErrors = credentials.ignoreSSLCertificateErrors
        )
    }

    private fun encryptPassword(info: ProviderInfo, credentials: ApiCredentials, password: String) {
        PasswordSafe.instance.setPassword(makeCredentialAttribute(info, credentials), password)
    }

    private fun decryptPassword(info: ProviderInfo, credentials: ApiCredentials): String? {
        val password = PasswordSafe.instance.getPassword(makeCredentialAttribute(info, credentials))
        if (null === password || password.isEmpty()) {
            // Handle legacy CredentialAttribute
            return PasswordSafe.instance.getPassword(makeLegacyCredentialAttribute(info, credentials))
        }
        return password
    }

    /**
     * For Windows, Intellij is using KeePass which have a 36 chars limitation on the group name, therefore I have
     * to shorten the group name since v2019.3.3
     */
    private fun makeCredentialAttribute(info: ProviderInfo, credentials: ApiCredentials): CredentialAttributes {
        if (credentials.url == credentials.login) {
            return CredentialAttributes("MRI:${info.id}", credentials.url)
        }
        return CredentialAttributes("MRI:${info.id}", "${credentials.login}:${credentials.url}")
    }

    /**
     * I have to keep legacy credential attribute otherwise current users have to input the token again
     * which is not available anymore. I meant can't see the token again after refreshing Gitlab's page.
     */
    private fun makeLegacyCredentialAttribute(info: ProviderInfo, credentials: ApiCredentials): CredentialAttributes {
        return CredentialAttributes(
            "MRI - ${info.id} - ${credentials.url} - ${credentials.login}"
        )
    }
}