package kotless.playground.core

import io.kotless.dsl.cloud.aws.SNSEvent
import io.kotless.dsl.cloud.aws.SNSEventData.SNSRecord
import kotless.utilities.rest.CustomObjectMapper
import org.slf4j.LoggerFactory

object SnsConsumers {
    private val logger = LoggerFactory.getLogger(SnsConsumers::class.java)

    @SNSEvent(topicName = "TpmMatchmakingNotifications", region = "CHANGE_ME")
    fun gameLiftSnsUpdate(record: SNSRecord) {
        logger.info("record: {}", record)

        val message = record.sns.message
        val root = CustomObjectMapper.objectMapper.readTree(message)
        val id by lazy { root.get("id").asText() }
        val detail = root.get("detail")
        val detailType = detail.get("type").asText()

        val ticketIdToPlayerIds by lazy {
            val tickets = detail.get("tickets")
            tickets.associate { ticket ->
                val ticketId = ticket.get("ticketId").asText()
                val players = ticket.get("players")

                val playerIds = players.map { it.get("playerId").asText() }
                ticketId to playerIds
            }
        }

        logger.info("detailType: {}, ticketIdToPlayerIds: {}", detailType, ticketIdToPlayerIds)

        when (detailType) {
            "MatchmakingCancelled" -> TODO() //CHANGE ME
            "PotentialMatchCreated" -> TODO() //CHANGE ME
            "MatchmakingSucceeded" -> TODO() //CHANGE ME
            "MatchmakingTimedOut" -> TODO() //CHANGE ME
            "AcceptMatchCompleted" -> TODO() //CHANGE ME
            "MatchmakingSearching" -> TODO() //CHANGE ME
            else -> {
                logger.warn("Unknown detail type: $detailType")
            }
        }
    }

    @SNSEvent(topicName = "CHANGE_ME")
    fun snsConsumer(record: SNSRecord) {
        TODO() //CHANGE_ME
    }
}