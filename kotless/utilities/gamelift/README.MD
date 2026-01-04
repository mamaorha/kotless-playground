# GameLift Utility

The GameLift utility provides integration with AWS GameLift FlexMatch for matchmaking functionality in multiplayer games.

## Features

- **Matchmaking**: Start and manage matchmaking tickets
- **Match Acceptance**: Accept or reject match proposals
- **Match Status**: Query matchmaking ticket status
- **SNS Integration**: Receive matchmaking notifications via SNS

## Components

### GameLiftManager

Singleton object that provides methods for interacting with AWS GameLift FlexMatch service.

**Key Methods:**
- `startMatchmaking()`: Start a new matchmaking request
- `startMatchmakingGetTicketId()`: Start matchmaking and get ticket ID
- `stopMatchmaking()`: Cancel an active matchmaking ticket
- `acceptMatch()`: Accept a match proposal
- `rejectMatch()`: Reject a match proposal
- `describeMatchmaking()`: Get status of matchmaking tickets

## Infrastructure Setup

### 1. Create Rule Set

Go to the AWS GameLift Console -> FlexMatch -> Rule Sets. Create a new one, for example, `RankMatchmakingRuleSet`.

**Example Rule Set (JSON):**

```json
{
  "name": "RankMatchmakingWithExpansion",
  "ruleLanguageVersion": "1.0",
  "playerAttributes": [{
    "name": "rank",
    "type": "number",
    "default": 0
  }],
  "teams": [{
    "name": "players",
    "minPlayers": 2,
    "maxPlayers": 5
  }],
  "rules": [{
    "name": "RankDistance",
    "type": "distance",
    "measurements": [ "teams[players].players.attributes[rank]" ],
    "referenceValue": "avg(teams[players].players.attributes[rank])",
    "maxDistance": 50
  }],
  "expansions": [{
    "target": "rules[RankDistance].maxDistance",
    "steps": [
      { "waitTimeSeconds": 5, "value": 150 },
      { "waitTimeSeconds": 10, "value": 500 },
      { "waitTimeSeconds": 30, "value": 500000 }
    ]
  }]
}
```

### 2. Create SNS Topic

Create an SNS Topic named `MatchmakingNotifications` (or your preferred name) to receive matchmaking callbacks.

### 3. Create FlexMatch Configuration

Create a "Matchmaking Configuration" in the GameLift console:

- **Name**: `MyMatchmakingConfig` (or your preferred name)
- **Rule Set**: `RankMatchmakingRuleSet` (the one you created above)
- **Acceptance Required**: `Yes` (if players need to accept matches)
- **RequestTimeoutSeconds**: Timeout in seconds (e.g., 300 for 5 minutes)
- **Notification Target**: Select your `MatchmakingNotifications` SNS topic

## Usage Examples

### Starting Matchmaking

```kotlin
import kotless.utilities.gamelift.GameLiftManager
import kotless.utilities.common.Either

fun joinMatchmakingQueue(playerId: String, playerRank: Double): Either<Exception, String> {
    return GameLiftManager.startMatchmakingGetTicketId(
        matchMakingConfigurationName = "MyMatchmakingConfig",
        playerId = playerId,
        rank = playerRank
    )
}

// Usage
val ticketIdResult = joinMatchmakingQueue("player-123", 1500.0)
ticketIdResult.fold(
    onLeft = { error -> 
        println("Failed to start matchmaking: ${error.message}")
    },
    onRight = { ticketId -> 
        println("Matchmaking started with ticket: $ticketId")
        // Store ticketId for later use
    }
)
```

### Checking Matchmaking Status

```kotlin
fun checkMatchmakingStatus(ticketId: String): Either<Exception, MatchmakingTicket> {
    return GameLiftManager.describeMatchmaking(arrayOf(ticketId))
        .map { response ->
            response.ticketList().firstOrNull()
        }
}

// Usage
val statusResult = checkMatchmakingStatus("ticket-abc-123")
statusResult.fold(
    onLeft = { error -> 
        println("Error checking status: ${error.message}")
    },
    onRight = { ticket ->
        ticket?.let {
            println("Status: ${it.status()}")
            println("Players: ${it.players().size}")
            println("Match ID: ${it.matchId()}")
        } ?: println("Ticket not found")
    }
)
```

### Accepting a Match

```kotlin
fun acceptMatchProposal(ticketId: String, playerIds: List<String>): Either<Exception, Unit> {
    return GameLiftManager.acceptMatch(
        ticketId = ticketId,
        playerIds = playerIds.toTypedArray()
    ).map { Unit }
}

// Usage
val acceptResult = acceptMatchProposal(
    ticketId = "ticket-abc-123",
    playerIds = listOf("player-1", "player-2", "player-3")
)

acceptResult.fold(
    onLeft = { error -> 
        println("Failed to accept match: ${error.message}")
    },
    onRight = { 
        println("Match accepted successfully")
    }
)
```

### Rejecting a Match

```kotlin
fun rejectMatchProposal(ticketId: String, playerIds: List<String>): Either<Exception, Unit> {
    return GameLiftManager.rejectMatch(
        ticketId = ticketId,
        playerIds = playerIds.toTypedArray()
    ).map { Unit }
}
```

### Stopping Matchmaking

```kotlin
fun cancelMatchmaking(ticketId: String): Either<Exception, Unit> {
    return GameLiftManager.stopMatchmaking(ticketId)
        .map { Unit }
}

// Usage
val cancelResult = cancelMatchmaking("ticket-abc-123")
cancelResult.fold(
    onLeft = { error -> 
        println("Failed to cancel: ${error.message}")
    },
    onRight = { 
        println("Matchmaking cancelled")
    }
)
```

### Starting Matchmaking and Handling Results

In production, matchmaking status updates are handled via SNS notifications. Here's the recommended flow:

```kotlin
import kotless.utilities.common.Either
import kotless.utilities.common.either

data class MatchmakingRequest(
    val ticketId: String,
    val playerId: String,
    val rank: Double
)

fun startMatchmakingRequest(
    playerId: String,
    playerRank: Double
): Either<Exception, MatchmakingRequest> {
    return either {
        val ticketId = GameLiftManager.startMatchmakingGetTicketId(
            matchMakingConfigurationName = "MyMatchmakingConfig",
            playerId = playerId,
            rank = playerRank
        ).bind()
        
        // Store the ticket ID (e.g., in database) for later reference
        // The actual matchmaking result will be received via SNS notification
        MatchmakingRequest(
            ticketId = ticketId,
            playerId = playerId,
            rank = playerRank
        )
    }
}
```

### SNS Consumer for Matchmaking Notifications

Set up an SNS consumer to receive matchmaking callbacks:

```kotlin
import io.kotless.dsl.cloud.aws.SNSEvent
import io.kotless.dsl.cloud.aws.SNSRecord
import org.slf4j.LoggerFactory
import com.fasterxml.jackson.databind.ObjectMapper

object GameLiftSnsConsumer {
    private val logger = LoggerFactory.getLogger(GameLiftSnsConsumer::class.java)
    private val objectMapper = ObjectMapper()

    @SNSEvent(topicName = "MatchmakingNotifications", region = "us-east-1")
    fun snsUpdate(record: SNSRecord) {
        try {
            val message = record.sns.message
            logger.info("Received matchmaking notification: {}", message)
            
            // Parse the message (it's a JSON string)
            val notification = objectMapper.readTree(message)
            val type = notification.get("detail").get("type").asText()
            
            when (type) {
                "MatchmakingSucceeded" -> {
                    val ticketId = notification.get("detail").get("ticketId").asText()
                    val matchId = notification.get("detail").get("matchId").asText()
                    handleMatchFound(ticketId, matchId)
                }
                "MatchmakingTimedOut" -> {
                    val ticketId = notification.get("detail").get("ticketId").asText()
                    handleMatchmakingTimeout(ticketId)
                }
                "MatchmakingCancelled" -> {
                    val ticketId = notification.get("detail").get("ticketId").asText()
                    handleMatchmakingCancelled(ticketId)
                }
                "MatchmakingFailed" -> {
                    val ticketId = notification.get("detail").get("ticketId").asText()
                    handleMatchmakingFailed(ticketId)
                }
                else -> {
                    logger.warn("Unknown notification type: {}", type)
                }
            }
        } catch (e: Exception) {
            logger.error("Error processing matchmaking notification", e)
        }
    }
    
    private fun handleMatchFound(ticketId: String, matchId: String) {
        logger.info("Match found! Ticket: {}, Match: {}", ticketId, matchId)
        // Notify players, create game session, etc.
    }
    
    private fun handleMatchmakingTimeout(ticketId: String) {
        logger.info("Matchmaking timed out for ticket: {}", ticketId)
        // Handle timeout - maybe retry or notify player
    }
    
    private fun handleMatchmakingCancelled(ticketId: String) {
        logger.info("Matchmaking cancelled for ticket: {}", ticketId)
    }
    
    private fun handleMatchmakingFailed(ticketId: String) {
        logger.error("Matchmaking failed for ticket: {}", ticketId)
    }
}
```

### Handling Duplicate Matchmaking Requests

The `startMatchmakingGetTicketId` method automatically handles cases where a player is already in matchmaking:

```kotlin
fun joinOrGetExistingTicket(playerId: String, rank: Double): Either<Exception, String> {
    // This will return the existing ticket ID if player is already in queue
    return GameLiftManager.startMatchmakingGetTicketId(
        matchMakingConfigurationName = "MyMatchmakingConfig",
        playerId = playerId,
        rank = rank
    )
}
```

### Multi-Player Matchmaking

```kotlin
fun startTeamMatchmaking(
    teamMembers: List<Pair<String, Double>>, // (playerId, rank)
    matchmakingConfig: String
): Either<Exception, String> {
    // Note: GameLiftManager.startMatchmaking supports multiple players
    // You would need to extend it or use the underlying AWS SDK directly
    // for team-based matchmaking
    
    // For now, start matchmaking for the first player
    val (firstPlayerId, firstPlayerRank) = teamMembers.first()
    return GameLiftManager.startMatchmakingGetTicketId(
        matchMakingConfigurationName = matchmakingConfig,
        playerId = firstPlayerId,
        rank = firstPlayerRank
    )
}
```

## Best Practices

1. **Use SNS Notifications**: Don't poll for matchmaking status; use SNS notifications instead
2. **Handle Timeouts**: Always handle matchmaking timeouts gracefully
3. **Store Ticket IDs**: Store matchmaking ticket IDs so you can check status or cancel if needed
4. **Error Handling**: Use `Either` pattern for proper error handling
5. **Player Attributes**: Define meaningful player attributes in your rule set
6. **Rule Set Design**: Design rule sets that balance match quality with wait time
7. **Expansions**: Use expansions to gradually relax matchmaking criteria over time
8. **Acceptance Flow**: If acceptance is required, implement a timeout for player responses

## Error Handling

```kotlin
fun safeStartMatchmaking(
    playerId: String,
    rank: Double
): Either<String, String> {
    return GameLiftManager.startMatchmakingGetTicketId(
        matchMakingConfigurationName = "MyMatchmakingConfig",
        playerId = playerId,
        rank = rank
    ).mapLeft { exception ->
        when {
            exception.message?.contains("InvalidRequestException") == true ->
                "Invalid matchmaking request"
            exception.message?.contains("NotFoundException") == true ->
                "Matchmaking configuration not found"
            else ->
                "Matchmaking error: ${exception.message}"
        }
    }
}
```