package net.ntworld.mergeRequestIntegrationIde.util

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import net.ntworld.mergeRequest.ProviderData

object HtmlHelper {
    private val myCommonMarkParser = Parser.builder().build()
    private val myHtmlRenderer = HtmlRenderer.builder().build()

    fun convertFromMarkdown(md: String): String {
        return myHtmlRenderer.render(myCommonMarkParser.parse(md))
    }

    fun resolveRelativePath(providerData: ProviderData, html: String): String {
        return html.replace("<img src=\"/", "<img src=\"${providerData.project.url}/")
    }
}