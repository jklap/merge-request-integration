package net.ntworld.mergeRequestIntegrationIde.util

import com.intellij.openapi.diagnostic.Logger
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import net.ntworld.mergeRequest.ProviderData

object HtmlHelper {
    private val myLogger = Logger.getInstance(this.javaClass)

    private val myParser = Parser.builder().build()
    private val myHtmlRenderer = HtmlRenderer.builder().build()

    fun convertFromMarkdown(md: String): String {
        val body = myHtmlRenderer.render(myParser.parse(md))
        return body
    }

    fun resolveRelativePath(providerData: ProviderData, html: String): String {
        return html.replace("<img src=\"/", "<img src=\"${providerData.project.url}/")
    }
}