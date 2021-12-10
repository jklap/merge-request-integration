package net.ntworld.mergeRequestIntegration.update

import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.ResponseResultOf
import com.github.kittinunf.result.Result
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.util.*

object UpdateManager {
    private const val CURRENT_VERSION = "2020.1.2"
    private const val METADATA_URL = "https://nhat-phan.github.io/updates/merge-request-integration/metadata.json"
    private const val CHECK_INTERVAL = 3600000 // Every 1 hour

    private val myJson: Json = Json
    private var myLastCheckDate : Date? = null

    fun shouldGetAvailableUpdates(): Boolean {
        val lastCheck = myLastCheckDate
        if (null === lastCheck) {
            return true
        }
        val difference = Date().time - lastCheck.time
        return difference > CHECK_INTERVAL
    }

    private fun makeGetRequest(url: String): ResponseResultOf<String> {
        return FuelManager().get(url).responseString()
    }

    fun getAvailableUpdates(): List<String> {
        try {
            val (_, _, result) = makeGetRequest(METADATA_URL)
            return when (result) {
                is Result.Success -> {
                    myLastCheckDate = Date()
                    buildAvailableUpdates(result.value)
                }
                is Result.Failure -> {
                    myLastCheckDate = Date()
                    listOf()
                }
            }
        } catch (exception: Exception) {
            myLastCheckDate = Date()
            return listOf()
        }
    }

    private fun buildAvailableUpdates(input: String): List<String> {
        val metadata = myJson.decodeFromString(ListSerializer(UpdateMetadata.serializer()), input).sortedBy { it.id }
        val currentVersion = metadata.firstOrNull { it.version == CURRENT_VERSION }
        if (null === currentVersion) {
            return listOf()
        }
        val updates = metadata.filter { it.id > currentVersion.id && it.active }
        return updates.map {
            try {
                val (_, _, result) = makeGetRequest(it.changesUrl)
                when (result) {
                    is Result.Success -> {
                        result.value
                            .replace("<html lang=\"en\">", "<h2>${it.version}</h2>")
                            .replace("</html>", "")
                    }
                    is Result.Failure -> {
                        ""
                    }
                }
            } catch (exception: Exception) {
                ""
            }
        }
    }
}