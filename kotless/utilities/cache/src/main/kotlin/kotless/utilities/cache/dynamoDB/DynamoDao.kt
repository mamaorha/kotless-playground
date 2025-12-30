package kotless.utilities.cache.dynamoDB

import io.kotless.dsl.cloud.aws.DynamoDBTable
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import kotless.utilities.common.AwsConstants.awsRegion
import kotless.utilities.common.AwsCredentialsProvider
import kotless.utilities.common.Benchmark
import kotless.utilities.common.Either
import kotless.utilities.common.asEither


open class DynamoDao(private val tableName: String) {
    companion object {
        private val logger = LoggerFactory.getLogger(DynamoDao::class.java)

        private val client: DynamoDbClient by lazy {
            val credentialsProvider = AwsCredentialsProvider.credentialsProvider

            Benchmark.logTime("building AmazonDynamoDB client") {
                DynamoDbClient.builder()
                    .region(awsRegion)
                    .credentialsProvider(credentialsProvider)
                    .build()
            }
        }
    }

    init {
        val annotation = javaClass.getAnnotation(DynamoDBTable::class.java)
        assert(annotation != null)
        assert(tableName == annotation.table)
    }

    protected fun put(values: Map<String, AttributeValue>): PutItemResponse {
        val req = PutItemRequest.builder()
            .tableName(tableName)
            .item(values)
            .build()

        return client.putItem(req)
    }

    protected fun update(
        key: Map<String, AttributeValue>,              // PK (must contain full primary key)
        updates: Map<String, AttributeValue>,          // other fields to update
    ): UpdateItemResponse {
        val updateExpr = StringBuilder("SET ")

        // build expression for updating all given attributes
        updates.keys.forEachIndexed { idx, attr ->
            if (idx > 0) updateExpr.append(", ")
            updateExpr.append("$attr = :$attr")
        }

        // expression attribute values
        val exprValues = mutableMapOf<String, AttributeValue>()
        updates.forEach { (attr, value) ->
            exprValues[":$attr"] = value
        }

        val builder = UpdateItemRequest.builder()
            .tableName(tableName)
            .key(key)
            .updateExpression(updateExpr.toString())

        return client.updateItem(builder.build())
    }

    // In order to use this you must have "cas" field of type number in the dynamo table
    protected fun updateWithCas(
        key: Map<String, AttributeValue>,              // PK (must contain full primary key)
        updates: Map<String, AttributeValue>,          // other fields to update
        expectedCas: Long?                             // null = insert, number = check + bump
    ): Either<Exception, UpdateItemResponse> {
        return Result.runCatching {
            val updateExpr = StringBuilder("SET ")

            // build expression for updating all given attributes
            updates.keys.forEachIndexed { idx, attr ->
                if (idx > 0) updateExpr.append(", ")
                updateExpr.append("$attr = :$attr")
            }

            // always increment CAS (insert → 1, otherwise bump by 1)
            if (updates.isNotEmpty()) updateExpr.append(", ")
            updateExpr.append("cas = if_not_exists(cas, :zero) + :one")

            // expression attribute values
            val exprValues = mutableMapOf<String, AttributeValue>()
            updates.forEach { (attr, value) ->
                exprValues[":$attr"] = value
            }
            exprValues[":zero"] = AttributeValue.builder().n("0").build()
            exprValues[":one"] = AttributeValue.builder().n("1").build()

            val builder = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .updateExpression(updateExpr.toString())

            // CAS condition
            if (expectedCas != null) {
                builder.conditionExpression("attribute_not_exists(cas) OR cas = :expectedCas")
                    .expressionAttributeValues(
                        exprValues + (":expectedCas" to AttributeValue.builder().n(expectedCas.toString()).build())
                    )
            } else {
                // only succeed if no CAS exists yet (new item)
                builder.conditionExpression("attribute_not_exists(cas)")
                    .expressionAttributeValues(exprValues)
            }

            client.updateItem(builder.build())
        }.asEither().mapLeft {
            logger.error("update with cas failed", it)
            it
        }
    }

    protected fun query(
        keyConditionExpression: String,
        expressionAttributeNames: Map<String, String>,
        expressionAttributeValues: Map<String, AttributeValue>
    ): QueryResponse {
        val req = QueryRequest.builder()
            .tableName(tableName)
            .keyConditionExpression(keyConditionExpression)
            .expressionAttributeNames(expressionAttributeNames)
            .expressionAttributeValues(expressionAttributeValues)
            .build()

        return client.query(req)
    }

    protected fun query(
        keyConditionExpression: String,
        expressionAttributeNames: Map<String, String>,
        expressionAttributeValues: Map<String, AttributeValue>,
        scanIndexForward: Boolean,
        limit: Int?
    ): QueryResponse {
        val builder = QueryRequest.builder()
            .tableName(tableName)
            .keyConditionExpression(keyConditionExpression)
            .expressionAttributeNames(expressionAttributeNames)
            .expressionAttributeValues(expressionAttributeValues)
            .scanIndexForward(scanIndexForward)

        limit?.let { builder.limit(it) }

        return client.query(builder.build())
    }

    protected fun scan(): ScanResponse {
        val req = ScanRequest.builder()
            .tableName(tableName)
            .build()

        return client.scan(req)
    }

    protected fun delete(key: Map<String, AttributeValue>): Either<Exception, DeleteItemResponse> {
        return Result.runCatching {
            val req = DeleteItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .build()

            client.deleteItem(req)
        }.asEither()
    }
}