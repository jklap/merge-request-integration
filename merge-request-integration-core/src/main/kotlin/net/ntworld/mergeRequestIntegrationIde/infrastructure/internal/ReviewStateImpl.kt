package net.ntworld.mergeRequestIntegrationIde.infrastructure.internal

import net.ntworld.mergeRequestIntegrationIde.infrastructure.ReviewState

data class ReviewStateImpl(
    override val id: String,

    override val data: MutableMap<String, MutableMap<String, String>>

) : ReviewState
