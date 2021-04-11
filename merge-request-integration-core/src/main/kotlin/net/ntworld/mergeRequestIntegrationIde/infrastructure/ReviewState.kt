package net.ntworld.mergeRequestIntegrationIde.infrastructure

import net.ntworld.mergeRequest.ProviderInfo
import net.ntworld.mergeRequest.api.ApiCredentials

interface ReviewState {
    // provider + merge Id
    val id: String

    // data
    val data: MutableMap<String, MutableMap<String, String>>

    // branch

    // viewed state

    // last line state

}