package kotless.playground.apis

import kotless.playground.api.GameLiftApi
import kotless.playground.model.*
import kotless.utilities.auth.AuthWrapper
import kotless.utilities.common.either
import kotless.utilities.common.flatten
import kotless.utilities.gamelift.GameLiftManager
import kotless.utilities.rest.validations.FieldValidations.asNotFound
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController
import software.amazon.awssdk.services.gamelift.model.MatchmakingConfigurationStatus
import javax.validation.Valid
import javax.validation.constraints.NotNull

/*
    Make sure you read kotless/utilities/gamelift/src/main/kotlin/kotless/utilities/gamelift/README.MD
*/
@RestController
class GameLiftExampleRest(
    private val authWrapper: AuthWrapper
) : GameLiftApi {
    @RequestMapping(
        value = ["/gamelift/matchmaking/ticketId/accept"],
        produces = ["application/json"],
        consumes = ["application/json"],
        method = [RequestMethod.POST]
    )
    override fun acceptMatch(
        authorization: String,
        ticketId: @NotNull @Valid String,
        body: @Valid TicketIdAcceptBody
    ): ResponseEntity<Void> {
        return authWrapper.withAuth(token = authorization) { authContext ->
            either {
                GameLiftManager.acceptMatch(ticketId = ticketId, playerIds = body.playerIds.toTypedArray()).bind()
                ResponseEntity.noContent().build<Void>()
            }
        }.flatten().getOrThrow()
    }

    @RequestMapping(
        value = ["/gamelift/matchmaking/ticketId"],
        produces = ["application/json"],
        method = [RequestMethod.GET]
    )
    override fun getMatchmakingStatus(
        authorization: String,
        ticketId: @NotNull @Valid String
    ): ResponseEntity<MatchmakingStatusResponse> {
        return authWrapper.withAuth(token = authorization) { authContext ->
            either {
                val matchmakingResponse = GameLiftManager.describeMatchmaking(ticketIds = arrayOf(ticketId)).bind()
                val ticket = matchmakingResponse.ticketList().firstOrNull().asNotFound("ticket").bind()

                val response = MatchmakingStatusResponse()
                    .ticketId(ticket.ticketId())
                    .players(
                        ticket.players().map { player ->
                            MatchmakingStatusResponsePlayers()
                                .playerId(player.playerId())
                                .rank(player.playerAttributes()["rank"]?.n() ?: 0.0)
                        })
                    .status(
                        when (ticket.status()) {
                            MatchmakingConfigurationStatus.CANCELLED -> MatchmakingStatusResponse.StatusEnum.CANCELLED
                            MatchmakingConfigurationStatus.COMPLETED -> MatchmakingStatusResponse.StatusEnum.COMPLETED
                            MatchmakingConfigurationStatus.FAILED -> MatchmakingStatusResponse.StatusEnum.FAILED
                            MatchmakingConfigurationStatus.PLACING -> MatchmakingStatusResponse.StatusEnum.PLACING
                            MatchmakingConfigurationStatus.QUEUED -> MatchmakingStatusResponse.StatusEnum.QUEUED
                            MatchmakingConfigurationStatus.REQUIRES_ACCEPTANCE -> MatchmakingStatusResponse.StatusEnum.REQUIRES_ACCEPTANCE
                            MatchmakingConfigurationStatus.SEARCHING -> MatchmakingStatusResponse.StatusEnum.SEARCHING
                            MatchmakingConfigurationStatus.TIMED_OUT -> MatchmakingStatusResponse.StatusEnum.TIMED_OUT
                            MatchmakingConfigurationStatus.UNKNOWN_TO_SDK_VERSION -> MatchmakingStatusResponse.StatusEnum.UNKNOWN
                        }
                    )
                    .gameSessionInfo(
                        MatchmakingStatusResponseGameSessionInfo()
                            .gameSessionId(ticket.gameSessionConnectionInfo().gameSessionArn())
                            .ipAddress(ticket.gameSessionConnectionInfo().ipAddress())
                            .port(ticket.gameSessionConnectionInfo().port())
                    )
                ResponseEntity.ok(response)
            }
        }.flatten().getOrThrow()
    }

    @RequestMapping(
        value = ["/gamelift/matchmaking/ticketId/reject"],
        produces = ["application/json"],
        consumes = ["application/json"],
        method = [RequestMethod.POST]
    )
    override fun rejectMatch(
        authorization: String,
        ticketId: @NotNull @Valid String,
        body: @Valid TicketIdRejectBody
    ): ResponseEntity<Void?>? {
        return authWrapper.withAuth(token = authorization) { authContext ->
            either {
                GameLiftManager.rejectMatch(ticketId = ticketId, playerIds = body.playerIds.toTypedArray()).bind()
                ResponseEntity.noContent().build<Void>()
            }
        }.flatten().getOrThrow()
    }

    @RequestMapping(
        value = ["/gamelift/matchmaking"],
        produces = ["application/json"],
        consumes = ["application/json"],
        method = [RequestMethod.POST]
    )
    override fun startMatchmaking(
        authorization: String,
        body: @Valid MatchmakingRequest
    ): ResponseEntity<MatchmakingResponse> {
        return authWrapper.withAuth(token = authorization) { authContext ->
            either {
                val ticketId = GameLiftManager.startMatchmakingGetTicketId(
                    matchMakingConfigurationName = body.matchmakingConfigurationName,
                    playerId = body.playerId,
                    rank = body.rank
                ).bind()

                ResponseEntity.ok(MatchmakingResponse().ticketId(ticketId))
            }
        }.flatten().getOrThrow()
    }

    @RequestMapping(
        value = ["/gamelift/matchmaking/ticketId"],
        produces = ["application/json"],
        method = [RequestMethod.DELETE]
    )
    override fun stopMatchmaking(
        authorization: String,
        ticketId: @NotNull @Valid String
    ): ResponseEntity<Void> {
        return authWrapper.withAuth(token = authorization) { authContext ->
            either {
                GameLiftManager.stopMatchmaking(ticketId = ticketId).bind()
                ResponseEntity.noContent().build<Void>()
            }
        }.flatten().getOrThrow()
    }

}