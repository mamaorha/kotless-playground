# DAO Utility

The DAO (Data Access Object) utility provides database connection management and SQL utilities for MySQL/Aurora databases.

## Features

- **Connection Management**: Centralized database connection handling
- **Secret Integration**: Secure credential management via SecretManager
- **SQL Utilities**: Helper functions for working with ResultSets
- **Environment Variable Support**: Fallback to environment variables for credentials
- **Serverless Optimized**: Lazy initialization ensures connections are only created when needed

## Components

### DaoConnectionProvider

Manages database connections with automatic credential retrieval from AWS Secrets Manager or environment variables.

**Spring Configuration:**
- `DaoProviderConfiguration` automatically provides `DaoConnectionProvider` as a Spring bean
- You can inject `DaoConnectionProvider` directly in your DAO classes - no need to create it manually
- You can provide a custom `DaoConnectionProvider` bean to use a different connection strategy

### Custom DaoConnectionProvider Implementation

If you need a custom `DaoConnectionProvider` implementation (e.g., for different database types, custom connection logic, or different credential sources), you can create one and define it as a Spring bean:

```kotlin
import kotless.utilities.dao.DaoConnectionProvider
import kotless.utilities.auth.SecretManager
import java.sql.Connection
import java.sql.DriverManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// Example: Custom connection provider with different secret keys
class CustomDaoConnectionProvider(
    private val secretManager: SecretManager
) {
    // Use different secret keys or environment variables
    private val url by lazy {
        System.getenv()["DB_HOST"] ?: secretManager.getSecret("CUSTOM_DB_HOST")
    }
    
    private val user by lazy {
        System.getenv()["DB_USER"] ?: secretManager.getSecret("CUSTOM_DB_USER")
    }
    
    private val password by lazy {
        System.getenv()["DB_PASS"] ?: secretManager.getSecret("CUSTOM_DB_PASS")
    }
    
    fun <T> useConnection(f: (Connection) -> T): T {
        return buildConnection().use(f)
    }
    
    private fun buildConnection(): Connection {
        // Custom connection building logic
        // e.g., different JDBC URL format, additional connection configuration, etc.
        return DriverManager.getConnection(url, user, password).apply {
            this.autoCommit = true
            // Additional connection configuration if needed
        }
    }
}

// Or create a completely custom implementation for different database types
class PostgresConnectionProvider(
    private val secretManager: SecretManager
) {
    private val url by lazy {
        System.getenv()["POSTGRES_HOST"] ?: secretManager.getSecret("POSTGRES_HOST")
    }
    
    private val user by lazy {
        System.getenv()["POSTGRES_USER"] ?: secretManager.getSecret("POSTGRES_USER")
    }
    
    private val password by lazy {
        System.getenv()["POSTGRES_PASS"] ?: secretManager.getSecret("POSTGRES_PASS")
    }
    
    fun <T> useConnection(f: (Connection) -> T): T {
        return DriverManager.getConnection(
            "jdbc:postgresql://$url/database",
            user,
            password
        ).apply { this.autoCommit = true }.use(f)
    }
}

// Define as Spring bean in configuration
// Note: The return type should match what your DAO classes expect
@Configuration
class CustomDaoConfiguration {
    @Bean
    fun daoConnectionProvider(secretManager: SecretManager): CustomDaoConnectionProvider {
        return CustomDaoConnectionProvider(secretManager)
    }
}
```

Then your custom `DaoConnectionProvider` will be automatically used when injected:

```kotlin
class UserDao(
    private val connectionProvider: CustomDaoConnectionProvider  // Your custom implementation
) {
    fun getUserById(userId: String): User? {
        return connectionProvider.useConnection { connection ->
            // Use connection as normal
        }
    }
}
```

**Note**: If you want to maintain compatibility with existing DAO classes that expect `DaoConnectionProvider`, you can create a wrapper or ensure your custom implementation has the same interface.

### DaoUtils

Utility functions for working with SQL ResultSets, particularly for handling nullable values.

## Configuration

### Required Secrets

The `DaoConnectionProvider` expects the following secrets in AWS Secrets Manager (or environment variables):

- **AURORA_HOST** (or `MYSQL_HOST` env var): Database host URL
- **AURORA_USER** (or `MYSQL_USER` env var): Database username
- **AURORA_PASS** (or `MYSQL_PASS` env var): Database password

### AWS Secrets Manager Setup

1. Create secrets in AWS Secrets Manager:
   ```bash
   # Using AWS CLI
   aws secretsmanager create-secret \
     --name AURORA_HOST \
     --secret-string "your-database-host.rds.amazonaws.com"
   
   aws secretsmanager create-secret \
     --name AURORA_USER \
     --secret-string "your-username"
   
   aws secretsmanager create-secret \
     --name AURORA_PASS \
     --secret-string "your-password"
   ```

2. Or use environment variables (useful for local development):
   ```bash
   export MYSQL_HOST=localhost:3306
   export MYSQL_USER=root
   export MYSQL_PASS=password
   ```

## Usage Examples

### Basic Connection Usage

```kotlin
import kotless.utilities.dao.DaoConnectionProvider
import java.sql.Connection

// Inject DaoConnectionProvider as a Spring bean (provided by DaoProviderConfiguration)
class UserDao(
    private val connectionProvider: DaoConnectionProvider
) {
    // Execute a query
    fun getUserById(userId: String): User? {
        return connectionProvider.useConnection { connection ->
        val sql = "SELECT id, name, email FROM users WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, userId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    User(
                        id = rs.getString("id"),
                        name = rs.getString("name"),
                        email = rs.getString("email")
                    )
                } else {
                    null
                }
            }
        }
    }
}
```

### Insert Operations

```kotlin
import kotless.utilities.dao.DaoConnectionProvider

class UserDao(
    private val connectionProvider: DaoConnectionProvider  // Injected Spring bean
) {
    fun createUser(name: String, email: String): Long {
        return connectionProvider.useConnection { connection ->
        val sql = """
            INSERT INTO users (name, email, created_at) 
            VALUES (?, ?, NOW())
        """.trimIndent()
        
        connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { stmt ->
            stmt.setString(1, name)
            stmt.setString(2, email)
            stmt.executeUpdate()
            
            stmt.generatedKeys.use { keys ->
                if (keys.next()) {
                    keys.getLong(1) // Return generated ID
                } else {
                    throw RuntimeException("Failed to get generated key")
                }
            }
        }
    }
}
```

### Update Operations

```kotlin
import kotless.utilities.dao.DaoConnectionProvider

class UserDao(
    private val connectionProvider: DaoConnectionProvider  // Injected Spring bean
) {
    fun updateUserEmail(userId: String, newEmail: String): Boolean {
        return connectionProvider.useConnection { connection ->
        val sql = "UPDATE users SET email = ? WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, newEmail)
            stmt.setString(2, userId)
            stmt.executeUpdate() > 0
        }
    }
}
```

### Batch Operations

```kotlin
import kotless.utilities.dao.DaoConnectionProvider

class UserDao(
    private val connectionProvider: DaoConnectionProvider  // Injected Spring bean
) {
    fun createUsers(users: List<Pair<String, String>>): Int {
        return connectionProvider.useConnection { connection ->
        val sql = "INSERT INTO users (name, email, created_at) VALUES (?, ?, NOW())"
        connection.prepareStatement(sql).use { stmt ->
            users.forEach { (name, email) ->
                stmt.setString(1, name)
                stmt.setString(2, email)
                stmt.addBatch()
            }
            stmt.executeBatch().sum()
        }
    }
    }
}
```

### Using DaoUtils for Nullable Values

```kotlin
import kotless.utilities.dao.DaoConnectionProvider
import kotless.utilities.dao.sql.DaoUtils

class UserDao(
    private val connectionProvider: DaoConnectionProvider  // Injected Spring bean
) {
    fun getUserWithOptionalFields(userId: String): User? {
        return connectionProvider.useConnection { connection ->
        val sql = """
            SELECT id, name, email, phone, age, last_login 
            FROM users 
            WHERE id = ?
        """.trimIndent()
        
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, userId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    User(
                        id = rs.getString("id"),
                        name = rs.getString("name"),
                        email = rs.getString("email"),
                        phone = DaoUtils.getNullable(rs) { it.getString("phone") },
                        age = DaoUtils.getNullable(rs) { it.getInt("age") },
                        lastLogin = DaoUtils.getNullable(rs) { 
                            it.getTimestamp("last_login")?.toInstant() 
                        }
                    )
                } else {
                    null
                }
            }
        }
    }
}
```

### Complex Queries with Joins

```kotlin
import kotless.utilities.dao.DaoConnectionProvider

data class UserWithOrders(
    val userId: String,
    val userName: String,
    val orderId: String,
    val orderTotal: Double,
    val orderDate: Instant
)

class OrderDao(
    private val connectionProvider: DaoConnectionProvider  // Injected Spring bean
) {
    fun getUserOrders(userId: String): List<UserWithOrders> {
        return connectionProvider.useConnection { connection ->
        val sql = """
            SELECT u.id as user_id, u.name as user_name,
                   o.id as order_id, o.total as order_total, o.created_at as order_date
            FROM users u
            INNER JOIN orders o ON u.id = o.user_id
            WHERE u.id = ?
            ORDER BY o.created_at DESC
        """.trimIndent()
        
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, userId)
            stmt.executeQuery().use { rs ->
                val results = mutableListOf<UserWithOrders>()
                while (rs.next()) {
                    results.add(
                        UserWithOrders(
                            userId = rs.getString("user_id"),
                            userName = rs.getString("user_name"),
                            orderId = rs.getString("order_id"),
                            orderTotal = rs.getDouble("order_total"),
                            orderDate = rs.getTimestamp("order_date").toInstant()
                        )
                    )
                }
                results
            }
        }
    }
    }
}
```

### Transaction Management

```kotlin
import kotless.utilities.dao.DaoConnectionProvider

class AccountDao(
    private val connectionProvider: DaoConnectionProvider  // Injected Spring bean
) {
    fun transferFunds(fromUserId: String, toUserId: String, amount: Double): Boolean {
        return connectionProvider.useConnection { connection ->
        try {
            connection.autoCommit = false
            
            // Deduct from sender
            val deductSql = "UPDATE accounts SET balance = balance - ? WHERE user_id = ?"
            connection.prepareStatement(deductSql).use { stmt ->
                stmt.setDouble(1, amount)
                stmt.setString(2, fromUserId)
                val rowsAffected = stmt.executeUpdate()
                if (rowsAffected == 0) {
                    connection.rollback()
                    return false
                }
            }
            
            // Add to receiver
            val addSql = "UPDATE accounts SET balance = balance + ? WHERE user_id = ?"
            connection.prepareStatement(addSql).use { stmt ->
                stmt.setDouble(1, amount)
                stmt.setString(2, toUserId)
                val rowsAffected = stmt.executeUpdate()
                if (rowsAffected == 0) {
                    connection.rollback()
                    return false
                }
            }
            
            connection.commit()
            true
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }
    }
}
```

### Using with Either for Error Handling

```kotlin
import kotless.utilities.common.Either
import kotless.utilities.common.asEither

class UserDao(
    private val connectionProvider: DaoConnectionProvider  // Injected Spring bean
) {
    fun getUserById(userId: String): User? {
        return connectionProvider.useConnection { connection ->
            // ... query implementation
            null
        }
    }
    
    fun getUserSafely(userId: String): Either<Exception, User?> {
        return Result.runCatching {
            getUserById(userId)
        }.asEither()
    }
}

// Usage
val result = getUserSafely("123").fold(
    onLeft = { ex -> 
        println("Error: ${ex.message}")
        null
    },
    onRight = { user -> 
        user?.let { println("Found user: ${it.name}") }
        user
    }
)
```

## Best Practices

1. **Always Use useConnection**: The `useConnection` method ensures connections are properly closed
2. **Use Prepared Statements**: Always use prepared statements to prevent SQL injection
3. **Handle Nullable Fields**: Use `DaoUtils.getNullable` for optional database fields
4. **Close Resources**: Use Kotlin's `use` function to ensure ResultSets and Statements are closed
5. **Error Handling**: Wrap database operations in try-catch or use `Either` for error handling
6. **Transactions**: Use transactions for operations that must succeed or fail together
7. **Secret Management**: Never hardcode credentials; always use SecretManager or environment variables

**Note**: In serverless environments, connection pooling is not applicable as each invocation may use a different container. Connections are created and closed per request.

## Error Handling

Common database errors and how to handle them:

```kotlin
fun safeQuery(userId: String): Either<String, User?> {
    return Result.runCatching {
        getUserById(userId)
    }.fold(
        onSuccess = { Either.Right(it) },
        onFailure = { ex ->
            when {
                ex.message?.contains("Connection") == true ->
                    Either.Left("Database connection failed")
                ex.message?.contains("SQL") == true ->
                    Either.Left("SQL error: ${ex.message}")
                else ->
                    Either.Left("Unknown error: ${ex.message}")
            }
        }
    )
}
```

