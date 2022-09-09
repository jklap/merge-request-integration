package net.ntworld.mergeRequestIntegration.provider.gitlab.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReplyCommentPayload(
    val id: Int,
    val type: String,
    val body: String,
    val attachment: String? = null,
    val author: UserInfoModel,

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null,

    val system: Boolean,

    @SerialName("noteable_id")
    val noteableId: Int,
    @SerialName("noteable_type")
    val noteableType: String,
    @SerialName("noteable_iid")
    val noteableIid: Int,
    @SerialName("commit_id")
    val commitIt: Int? = null,
    val position: Position? = null,

    val resolvable: Boolean,
    val confidential: Boolean,
    val internal: Boolean,

) {
    @Serializable
    data class Position(
            @SerialName("base_sha")
            val baseSha: String,
            @SerialName("start_sha")
            val startSha: String,
            @SerialName("head_sha")
            val headSha: String,
            @SerialName("position_type")
            val positionType: String,
            @SerialName("new_path")
            val newPath: String? = null,
            @SerialName("new_line")
            val newLine: Int? = null,
            @SerialName("old_path")
            val oldPath: String? = null,
            @SerialName("old_line")
            val oldLine: Int? = null,
            val width: Int? = null,
            val height: Int? = null,
            val x: Int? = null,
            val y: Int? = null
    )
}
