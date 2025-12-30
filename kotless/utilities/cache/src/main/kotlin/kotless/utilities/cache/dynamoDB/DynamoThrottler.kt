package kotless.utilities.cache.dynamoDB

import kotless.utilities.common.Either
import kotless.utilities.common.throttler.Throttler
import kotless.utilities.common.throttler.ThrottlerException
import io.kotless.PermissionLevel
import io.kotless.dsl.cloud.aws.DynamoDBTable
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.util.concurrent.TimeUnit

//CHANGE_ME -> make sure you create this table in dynamoDB (see the README.MD file next to it)
private const val tableName: String = "throttler"

@DynamoDBTable(table = tableName, level = PermissionLevel.ReadWrite)
class DynamoThrottler(val ttlInSeconds: Long, val maxRequests: Int) : DynamoDao(tableName = tableName), Throttler {
    override fun <T> throttle(key: String, resource: String, f: () -> T): Either<ThrottlerException, T> {
        return if (getNumberOfCalls(key) >= maxRequests) Either.Left(
            ThrottlerException(
                resource = resource,
                message = "Too many calls"
            )
        )
        else {
            incNumberOfCalls(clientKey = key)
            return Either.Right(f())
        }
    }

    private fun incNumberOfCalls(clientKey: String) {
        val currentTimeStamp = System.currentTimeMillis()
        val ttl = currentTimeStamp / 1000 + ttlInSeconds

        val values = mapOf(
            "clientKey" to AttributeValue.builder().s(clientKey).build(),
            "throttlerTime" to AttributeValue.builder().n(currentTimeStamp.toString()).build(),
            "ttl" to AttributeValue.builder().n(ttl.toString()).build()
        )

        put(values = values)
    }

    private fun getNumberOfCalls(clientKey: String): Int {
        val ttlInMillis = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(ttlInSeconds)

        val queryResult = query(
            keyConditionExpression = "#c = :v_clientKey and #t > :v_throttler_tm",
            expressionAttributeNames = mapOf("#c" to "clientKey", "#t" to "throttlerTime"),
            expressionAttributeValues = mapOf(
                ":v_clientKey" to AttributeValue.builder().s(clientKey).build(),
                ":v_throttler_tm" to AttributeValue.builder().n(ttlInMillis.toString()).build()
            )
        )

        return queryResult.count()
    }
}