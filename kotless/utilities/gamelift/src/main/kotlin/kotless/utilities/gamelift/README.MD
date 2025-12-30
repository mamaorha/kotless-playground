# The Rule Set (JSON)
Go to the AWS GameLift Console -> FlexMatch -> Rule Sets. 
   
Create a new one, for example, RankMatchmakingRuleSet.

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

# The Infrastructure Setup
1. SNS Topic: Create an SNS Topic named MatchmakingNotifications.

2. FlexMatch Configuration:
Create a "Matchmaking Configuration" in the GameLift console.

* Name: MyMatchmakingConfig
* Rule Set: RankMatchmakingRuleSet
* Acceptance Required: Yes
* RequestTimeoutSeconds: for example, if you want to auto assign bots after given time.
* Notification Target: Select your MatchmakingNotifications SNS topic.

3. use GameLiftManager to perform actions
4. define Sns consumer to get callbacks from gamelift
```kotlin
object GameLiftSnsConsumer {
    private val logger = LoggerFactory.getLogger(GameLiftSnsConsumer::class.java)

    @SNSEvent(topicName = "TpmMatchmakingNotifications", region = "CHANGE_ME")
    fun snsUpdate(record: SNSRecord) {
        logger.info("record: {}", record)
        //BUSINESS LOGIC GOES HERE
    }
}
```