package kotless.utilities.gamelift

import io.kotless.PermissionLevel
import io.kotless.dsl.cloud.aws.GameLift
import kotless.utilities.common.AwsConstants.awsSecondaryRegion
import kotless.utilities.common.AwsCredentialsProvider
import kotless.utilities.common.Benchmark
import kotless.utilities.common.Either
import kotless.utilities.common.asEither
import software.amazon.awssdk.services.gamelift.GameLiftClient
import software.amazon.awssdk.services.gamelift.model.*

@GameLift(level = PermissionLevel.ReadWrite)
object GameLiftManager {
    private val gameLift by lazy {
        val credentialsProvider = AwsCredentialsProvider.credentialsProvider

        Benchmark.logTime("building AmazonDynamoDB client") {
            GameLiftClient.builder()
                .region(awsSecondaryRegion)
                .credentialsProvider(credentialsProvider)
                .build()
        }
    }

    fun startMatchmaking(
        matchMakingConfigurationName: String,
        playerId: String,
        rank: Double
    ): Either<Exception, StartMatchmakingResponse> {
        val player = Player.builder()
            .playerId(playerId)
            .playerAttributes(mapOf("rank" to AttributeValue.builder().n(rank).build()))
            .build()

        val startRequest = StartMatchmakingRequest.builder()
            .configurationName(matchMakingConfigurationName)
            .players(player)
            .build()

        return Result.runCatching { gameLift.startMatchmaking(startRequest) }.asEither()
    }

    fun startMatchmakingGetTicketId(
        matchMakingConfigurationName: String,
        playerId: String,
        rank: Double
    ): Either<Exception, String> {
        val startMatchmakingResponse = startMatchmaking(
            matchMakingConfigurationName = matchMakingConfigurationName,
            playerId = playerId,
            rank = rank
        )

        return startMatchmakingResponse.fold(
            onLeft = { e ->
                if (e is InvalidRequestException) {
                    val message = e.message ?: ""

                    if (message.contains("already participating in matchmaking ticket")) {
                        // The message looks like: "Player p-123 is already participating in matchmaking ticket ticket-abc-123"
                        val ticketId = message.split(" ").last()
                        Either.Right(ticketId)
                    } else {
                        Either.Left(e)
                    }
                } else {
                    Either.Left(e)
                }
            },
            onRight = { Either.Right(it.matchmakingTicket().ticketId()) }
        )
    }

    fun stopMatchmaking(ticketId: String): Either<Exception, StopMatchmakingResponse?> {
        val stopRequest = StopMatchmakingRequest.builder()
            .ticketId(ticketId)
            .build()

        return Result.runCatching { gameLift.stopMatchmaking(stopRequest) }.asEither()
    }

    fun acceptMatch(ticketId: String, playerIds: Array<String>): Either<Exception, AcceptMatchResponse> {
        val acceptRequest = AcceptMatchRequest.builder()
            .ticketId(ticketId)
            .playerIds(*playerIds)
            .acceptanceType(AcceptanceType.ACCEPT)
            .build()

        return Result.runCatching { gameLift.acceptMatch(acceptRequest) }.asEither()
    }

    fun rejectMatch(ticketId: String, playerIds: Array<String>): Either<Exception, AcceptMatchResponse> {
        val acceptRequest = AcceptMatchRequest.builder()
            .ticketId(ticketId)
            .playerIds(*playerIds)
            .acceptanceType(AcceptanceType.REJECT)
            .build()

        return Result.runCatching { gameLift.acceptMatch(acceptRequest) }.asEither()
    }

    fun describeMatchmaking(ticketIds: Array<String>): Either<Exception, DescribeMatchmakingResponse> {
        val describeMatchmakingRequest = DescribeMatchmakingRequest.builder()
            .ticketIds(*ticketIds)
            .build()

        return Result.runCatching { gameLift.describeMatchmaking(describeMatchmakingRequest) }.asEither()
    }
}