package kotless.playground.persistence.dynamo

import io.kotless.PermissionLevel
import io.kotless.dsl.cloud.aws.DynamoDBTable
import kotless.playground.data.Example
import kotless.playground.persistence.dto.ExampleDto
import kotless.utilities.cache.dynamoDB.DynamoDao
import kotless.utilities.common.Either
import kotless.utilities.common.GenericUtils
import kotless.utilities.rest.CustomObjectMapper
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.DeleteItemResponse

private const val tableName: String = "example"

/*
    you need to create a table in dynamoDB called "example" with the following:
    primaryKey: username (string)
    sortKey: id (string)
*/
@DynamoDBTable(table = tableName, level = PermissionLevel.ReadWrite)
object DynamoExampleDao : DynamoDao(tableName = tableName) {
    fun upsert(exampleDto: ExampleDto): Either<Exception, ExampleDto> {
        val updates = mapOf(
            "json" to AttributeValue.builder().s(CustomObjectMapper.objectMapper.writeValueAsString(exampleDto.data))
                .build()
        )

        return updateWithCas(
            key = buildKey(username = exampleDto.username, id = exampleDto.id),
            updates = updates,
            expectedCas = exampleDto.cas
        ).map {
            exampleDto.copy(
                cas = (exampleDto.cas ?: 1) + 1
            )
        }
    }

    //this helps us avoid concurrent updates, we get the current state, and we can choose if to update it
    fun upsert(
        username: String,
        id: String,
        mutator: (ExampleDto?) -> ExampleDto?
    ): Either<Exception, Pair<Boolean, ExampleDto?>> {
        val iterator = GenericUtils.iterator(0)

        while (true) {
            val index = iterator.next()
            val currentDto = get(username = username, id = id)
            val exampleDto = mutator(currentDto)

            // no need to update, mutating not required or already matching
            if (exampleDto == null || currentDto?.copy(cas = exampleDto.cas) == exampleDto) {
                return Either.Right(false to currentDto)
            }

            when (val result = upsert(exampleDto = exampleDto)) {
                is Either.Left -> if (index >= 5) return result
                is Either.Right -> return result.map { true to it }
            }
        }
    }

    fun delete(username: String, id: String): Either<Exception, DeleteItemResponse> {
        return delete(key = buildKey(username = username, id = id))
    }

    fun get(username: String, id: String): ExampleDto? {
        val queryResult = query(
            keyConditionExpression = "#u = :v_username and #i > :v_id",
            expressionAttributeNames = mapOf("#u" to "username", "#i" to "id"),
            expressionAttributeValues = mapOf(
                ":v_username" to AttributeValue.builder().s(username).build(),
                ":v_id" to AttributeValue.builder().s(id).build()
            )
        )

        return queryResult.items().firstOrNull()?.let { readExampleDto(it) }
    }

    fun query(username: String, limit: Int?): List<ExampleDto> {
        val queryResult = super.query(
            keyConditionExpression = "#u = :v_username",
            expressionAttributeNames = mapOf("#u" to "username"),
            expressionAttributeValues = mapOf(":v_username" to AttributeValue.builder().s(username).build()),
            scanIndexForward = true,
            limit = limit
        )

        return queryResult.items().map { readExampleDto(it) }
    }

    private fun buildKey(username: String, id: String): Map<String, AttributeValue> {
        return mapOf(
            "username" to AttributeValue.builder().s(username).build(),
            "id" to AttributeValue.builder().s(id).build()
        )
    }

    private fun readExampleDto(item: Map<String, AttributeValue>): ExampleDto {
        // Extract attributes from DynamoDB item
        val username = requireNotNull(item["username"]?.s()) { "username is null" }
        val id = requireNotNull(item["id"]?.s()) { "id is null" }
        val cas = requireNotNull(item["cas"]?.n()?.toLongOrNull()) { "cas is null" }
        val data = requireNotNull(item["json"]?.s()) { "json is null" }.let {
            CustomObjectMapper.objectMapper.readValue(
                it,
                Example::class.java
            )
        }

        return ExampleDto(
            username = username,
            id = id,
            cas = cas,
            data = data
        )
    }
}