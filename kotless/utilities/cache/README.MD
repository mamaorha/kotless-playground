# Cache Utility - DynamoDB

The Cache utility provides DynamoDB integration for caching and data persistence, including a throttling mechanism built on top of DynamoDB.

## Components

### DynamoDao

Base class for DynamoDB operations. Provides methods for:
- **Put**: Insert or replace items
- **Update**: Update existing items
- **Update with CAS**: Optimistic locking using Compare-And-Swap
- **Query**: Query items by key conditions
- **Scan**: Scan entire table
- **Delete**: Delete items

### DynamoThrottler

Rate limiting implementation using DynamoDB as the storage backend.

**Spring Configuration:**
- `CacheConfiguration` automatically provides `ThrottlerBuilder` as a Spring bean
- The default `ThrottlerBuilder` creates `DynamoThrottler` instances
- You can inject `ThrottlerBuilder` directly in your services - no need to create it manually
- You can provide a custom `ThrottlerBuilder` bean to use a different throttling implementation

## DynamoDB Table Setup

### For DynamoThrottler

In order for the DynamoThrottler to work you will need to create a DynamoDB table with the following:
* name: throttler
* partitionKey: clientKey (S)
* sortKey: throttlerTime (N)
* TTL attribute: ttl (N) - Optional but recommended for automatic cleanup

### Custom ThrottlerBuilder

If you need a throttling implementation that doesn't use DynamoDB (e.g., in-memory throttling, Redis-based throttling, etc.), you can create a custom `ThrottlerBuilder` and define it as a Spring bean:

```kotlin
import kotless.utilities.common.throttler.Throttler
import kotless.utilities.common.throttler.ThrottlerBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// Example: In-memory throttler implementation
class InMemoryThrottler(
    private val ttlInSeconds: Long,
    private val maxRequests: Int
) : Throttler {
    private val requestCounts = mutableMapOf<String, MutableList<Long>>()
    
    override fun <T> throttle(key: String, resource: String, f: () -> T): Either<ThrottlerException, T> {
        val now = System.currentTimeMillis()
        val windowStart = now - (ttlInSeconds * 1000)
        
        // Clean up old entries
        requestCounts[key] = requestCounts[key]?.filter { it > windowStart }?.toMutableList() ?: mutableListOf()
        
        // Check if limit exceeded
        if (requestCounts[key]?.size ?: 0 >= maxRequests) {
            return Either.Left(ThrottlerException(resource = resource, message = "Too many calls"))
        }
        
        // Record this request
        requestCounts.getOrPut(key) { mutableListOf() }.add(now)
        
        return Either.Right(f())
    }
}

// Define custom ThrottlerBuilder as Spring bean
@Configuration
class CustomThrottlerConfiguration {
    @Bean
    fun throttlerBuilder(): ThrottlerBuilder {
        return object : ThrottlerBuilder {
            override fun build(ttlInSeconds: Long, maxRequests: Int): Throttler {
                return InMemoryThrottler(ttlInSeconds = ttlInSeconds, maxRequests = maxRequests)
            }
        }
    }
}
```

Then your custom `ThrottlerBuilder` will be automatically used when you inject it:

```kotlin
@RestController
class ApiController(
    private val throttlerBuilder: ThrottlerBuilder  // Your custom implementation
) {
    private val apiThrottler by lazy {
        throttlerBuilder.build(ttlInSeconds = 60, maxRequests = 100)
    }
    // Use as normal
}
```

## Usage Examples

### Basic DynamoDao Usage

```kotlin
import kotless.utilities.cache.dynamoDB.DynamoDao
import io.kotless.dsl.cloud.aws.DynamoDBTable
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import kotless.utilities.common.Either

@DynamoDBTable(table = "users", level = PermissionLevel.ReadWrite)
class UserDao : DynamoDao(tableName = "users") {
    
    fun createUser(userId: String, email: String, name: String) {
        val item = mapOf(
            "userId" to AttributeValue.builder().s(userId).build(),
            "email" to AttributeValue.builder().s(email).build(),
            "name" to AttributeValue.builder().s(name).build(),
            "createdAt" to AttributeValue.builder().n(System.currentTimeMillis().toString()).build()
        )
        
        put(values = item)
    }
    
    fun updateUserEmail(userId: String, newEmail: String) {
        val key = mapOf(
            "userId" to AttributeValue.builder().s(userId).build()
        )
        
        val updates = mapOf(
            "email" to AttributeValue.builder().s(newEmail).build()
        )
        
        update(key = key, updates = updates)
    }
    
    fun getUser(userId: String): List<Map<String, AttributeValue>> {
        val response = query(
            keyConditionExpression = "userId = :userId",
            expressionAttributeNames = emptyMap(),
            expressionAttributeValues = mapOf(
                ":userId" to AttributeValue.builder().s(userId).build()
            )
        )
        
        return response.items()
    }
    
    fun deleteUser(userId: String): Either<Exception, DeleteItemResponse> {
        val key = mapOf(
            "userId" to AttributeValue.builder().s(userId).build()
        )
        
        return delete(key = key)
    }
}
```

### Using Update with CAS (Optimistic Locking)

```kotlin
@DynamoDBTable(table = "products", level = PermissionLevel.ReadWrite)
class ProductDao : DynamoDao(tableName = "products") {
    
    fun updateProductStock(
        productId: String,
        newStock: Int,
        expectedCas: Long?
    ): Either<Exception, UpdateItemResponse> {
        val key = mapOf(
            "productId" to AttributeValue.builder().s(productId).build()
        )
        
        val updates = mapOf(
            "stock" to AttributeValue.builder().n(newStock.toString()).build()
        )
        
        // expectedCas = null means insert new item
        // expectedCas = number means update only if CAS matches
        return updateWithCas(
            key = key,
            updates = updates,
            expectedCas = expectedCas
        )
    }
    
    // Usage example
    fun decrementStock(productId: String, currentCas: Long): Either<Exception, Unit> {
        return updateProductStock(
            productId = productId,
            newStock = getCurrentStock(productId) - 1,
            expectedCas = currentCas
        ).map { Unit }
    }
}
```

### Query with Filters and Limits

```kotlin
@DynamoDBTable(table = "orders", level = PermissionLevel.ReadWrite)
class OrderDao : DynamoDao(tableName = "orders") {
    
    fun getRecentOrders(userId: String, limit: Int = 10): List<Map<String, AttributeValue>> {
        val response = query(
            keyConditionExpression = "userId = :userId",
            expressionAttributeNames = emptyMap(),
            expressionAttributeValues = mapOf(
                ":userId" to AttributeValue.builder().s(userId).build()
            ),
            scanIndexForward = false, // Sort descending (newest first)
            limit = limit
        )
        
        return response.items()
    }
    
    fun getOrdersByDateRange(
        userId: String,
        startDate: Long,
        endDate: Long
    ): List<Map<String, AttributeValue>> {
        val response = query(
            keyConditionExpression = "userId = :userId AND orderDate BETWEEN :start AND :end",
            expressionAttributeNames = emptyMap(),
            expressionAttributeValues = mapOf(
                ":userId" to AttributeValue.builder().s(userId).build(),
                ":start" to AttributeValue.builder().n(startDate.toString()).build(),
                ":end" to AttributeValue.builder().n(endDate.toString()).build()
            )
        )
        
        return response.items()
    }
}
```

### Using DynamoThrottler

The `DynamoThrottler` is typically used through the `ThrottlerBuilder` interface, which is provided as a Spring bean. This allows you to create throttlers with specific rate limits for different endpoints.

#### Basic Usage in REST Controller

```kotlin
import kotless.utilities.auth.AuthWrapper
import kotless.utilities.common.throttler.ThrottlerBuilder
import kotless.utilities.common.throttler.Throttler
import kotless.utilities.common.flatten
import org.springframework.web.bind.annotation.*
import jakarta.validation.Valid

@RestController
class ExampleRest(
    private val authWrapper: AuthWrapper,
    private val throttlerBuilder: ThrottlerBuilder,
) : ExampleApi {
    
    // Create throttler lazily with specific rate limits
    private val exampleThrottler by lazy {
        throttlerBuilder.build(ttlInSeconds = 5, maxRequests = 2)
    }

    @RequestMapping(
        value = ["/example"],
        produces = ["application/json"],
        consumes = ["application/json"],
        method = [RequestMethod.POST]
    )
    override fun doSomething(
        authorization: String,
        body: @Valid DoSomethingRequest
    ): ResponseEntity<DoSomethingResponse> {
        return authWrapper.withAuth(token = authorization) { authContext ->
            exampleThrottler.throttle(
                key = authContext.username + "_example",
                resource = "doSomething"
            ) {
                // Your business logic here
                DoSomethingResponse(result = "Success")
            }
        }.flatten().getOrThrow()
    }
}
```

#### Multiple Throttlers with Different Limits

```kotlin
@RestController
class ApiController(
    private val authWrapper: AuthWrapper,
    private val throttlerBuilder: ThrottlerBuilder,
) {
    // Strict throttling for sensitive operations
    private val strictThrottler by lazy {
        throttlerBuilder.build(ttlInSeconds = 60, maxRequests = 5)
    }
    
    // Relaxed throttling for general operations
    private val relaxedThrottler by lazy {
        throttlerBuilder.build(ttlInSeconds = 60, maxRequests = 100)
    }
    
    @PostMapping("/api/sensitive-operation")
    fun sensitiveOperation(
        @RequestHeader("Authorization") token: String,
        @RequestBody request: SensitiveRequest
    ): ResponseEntity<Response> {
        return authWrapper.withAuth(token = token) { authContext ->
            strictThrottler.throttle(
                key = "${authContext.username}_sensitive",
                resource = "sensitiveOperation"
            ) {
                performSensitiveOperation(request)
            }
        }.flatten().getOrThrow()
    }
    
    @PostMapping("/api/general-operation")
    fun generalOperation(
        @RequestHeader("Authorization") token: String,
        @RequestBody request: GeneralRequest
    ): ResponseEntity<Response> {
        return authWrapper.withAuth(token = token) { authContext ->
            relaxedThrottler.throttle(
                key = "${authContext.username}_general",
                resource = "generalOperation"
            ) {
                performGeneralOperation(request)
            }
        }.flatten().getOrThrow()
    }
}
```

#### Error Handling

The throttler returns `Either<ThrottlerException, T>`, which is automatically handled by the REST exception handler. When throttling fails, it will return a `429 Too Many Requests` response.

```kotlin
@RestController
class ApiController(
    private val authWrapper: AuthWrapper,
    private val throttlerBuilder: ThrottlerBuilder,
) {
    private val apiThrottler by lazy {
        throttlerBuilder.build(ttlInSeconds = 60, maxRequests = 10)
    }
    
    @PostMapping("/api/endpoint")
    fun endpoint(
        @RequestHeader("Authorization") token: String,
        @RequestBody request: Request
    ): ResponseEntity<Response> {
        // The throttler exception will be automatically converted to 429 status
        return authWrapper.withAuth(token = token) { authContext ->
            apiThrottler.throttle(
                key = "${authContext.username}_endpoint",
                resource = "endpoint"
            ) {
                processRequest(request)
            }
        }.flatten().getOrThrow()
    }
}
```

### Error Handling

```kotlin
fun safeUpdate(userId: String, email: String): Either<String, Unit> {
    val dao = UserDao()
    
    return dao.updateUserEmail(userId, email)
        .map { Unit }
        .mapLeft { exception ->
            when {
                exception.message?.contains("ConditionalCheckFailedException") == true ->
                    "User was modified by another process"
                else -> "Failed to update user: ${exception.message}"
            }
        }
}
```

### Complex Example - User Session Management

```kotlin
@DynamoDBTable(table = "sessions", level = PermissionLevel.ReadWrite)
class SessionDao : DynamoDao(tableName = "sessions") {
    
    fun createSession(userId: String, sessionToken: String, ttl: Long) {
        val item = mapOf(
            "userId" to AttributeValue.builder().s(userId).build(),
            "sessionToken" to AttributeValue.builder().s(sessionToken).build(),
            "createdAt" to AttributeValue.builder().n(System.currentTimeMillis().toString()).build(),
            "ttl" to AttributeValue.builder().n(ttl.toString()).build()
        )
        
        put(values = item)
    }
    
    fun getActiveSessions(userId: String): List<Map<String, AttributeValue>> {
        val now = System.currentTimeMillis()
        
        return query(
            keyConditionExpression = "userId = :userId",
            expressionAttributeNames = emptyMap(),
            expressionAttributeValues = mapOf(
                ":userId" to AttributeValue.builder().s(userId).build()
            )
        ).items().filter { item ->
            val ttl = item["ttl"]?.n()?.toLongOrNull() ?: 0L
            ttl > now / 1000 // TTL is in seconds
        }
    }
    
    fun invalidateSession(userId: String, sessionToken: String): Either<Exception, DeleteItemResponse> {
        val key = mapOf(
            "userId" to AttributeValue.builder().s(userId).build(),
            "sessionToken" to AttributeValue.builder().s(sessionToken).build()
        )
        
        return delete(key = key)
    }
}
```

## Best Practices

1. **Use Appropriate Keys**: Design your partition and sort keys based on your access patterns
2. **Enable TTL**: Use TTL attribute for automatic cleanup of expired items
3. **Use CAS for Concurrency**: Use `updateWithCas` when multiple processes might update the same item
4. **Query vs Scan**: Prefer `query` over `scan` for better performance
5. **Set Limits**: Always set limits on queries to prevent large result sets
6. **Error Handling**: Always handle `Either.Left` cases properly
7. **Throttling**: Use `DynamoThrottler` to prevent abuse and manage rate limits

## Performance Considerations

- **Partition Keys**: Distribute data evenly across partitions
- **Query Efficiency**: Use query with proper key conditions instead of scans
- **Item Size**: Keep items small (< 400KB) for optimal performance
- **Read/Write Capacity**: Configure appropriate capacity units for your workload
- **Indexes**: Create Global Secondary Indexes (GSI) for alternative access patterns