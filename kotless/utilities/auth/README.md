# Auth Utility

The Auth utility provides authentication and authorization functionality for Kotless applications, supporting AWS Cognito JWT tokens and server-signed tokens.

## Features

- **JWT Token Verification**: Verify and decode JWT tokens from AWS Cognito
- **Server-Signed Tokens**: Create and verify server-signed tokens for internal service communication
- **Auth Context**: Extract user information from tokens and build authentication context
- **Secret Management**: Interface for retrieving secrets (e.g., from AWS Secrets Manager)

## Components

### AuthWrapper

Abstract base class for authentication wrappers. Provides methods to verify tokens and extract user attributes.

**Key Methods:**
- `withAuth(token: String, f: (AuthContext) -> T)`: Execute a function with authentication context
- `withServerAuth(token: String, f: (AuthContext) -> T)`: Execute a function requiring server authentication
- `buildServerSignToken(username: String, server: String)`: Create a server-signed token

### AwsAuthWrapper

AWS-specific implementation of `AuthWrapper` that integrates with AWS Cognito.

**Spring Configuration:**
- `AuthConfiguration` automatically provides `AuthWrapper` and `SecretManager` as Spring beans
- You can inject `AuthWrapper` directly in your services - no need to build it manually

**Configuration Required:**
- AWS Cognito User Pool ID (configured via `AwsConstants.awsUserPoolsId`)
- AWS Region (configured via `AwsConstants.awsRegion`)
- Server Sign Secret (stored in AWS Secrets Manager with key `SERVER_SIGN_SECRET`)

### SecretManager

Interface for managing secrets. Implementations can retrieve secrets from various sources.

### AwsSecretManager

AWS Secrets Manager implementation that retrieves secrets from AWS Secrets Manager.

## Usage Examples

### Basic Authentication

```kotlin
import kotless.utilities.auth.AuthWrapper
import kotless.utilities.common.Either

// Inject AuthWrapper as a Spring bean (provided by AuthConfiguration)
class AuthService(
    private val authWrapper: AuthWrapper
) {
    // Use authentication in a handler
    fun handleRequest(token: String): Either<AuthException, String> {
        return authWrapper.withAuth(token) { authContext ->
            // Access user information
            val username = authContext.username
            val email = authContext.email
            
            // Your business logic here
            "Hello, $username!"
        }
    }
}
```

### Server Authentication

```kotlin
import kotless.utilities.auth.AuthWrapper
import kotless.utilities.common.Either

class AuthService(
    private val authWrapper: AuthWrapper  // Injected Spring bean
) {
    // Create a server-signed token
    fun createServerToken(username: String, server: String): String {
        return authWrapper.buildServerSignToken(
            username = username,
            server = server
        )
    }
    
    // Verify server token in another service
    fun verifyServerToken(token: String): Either<AuthException, String?> {
        return authWrapper.withServerAuth(token) { authContext ->
            // This will only succeed if the token has server attribute
            authContext.server // Returns server name
        }
    }
}
```

### Custom AuthWrapper Implementation

If you need a custom `AuthWrapper` implementation, you should define it as a Spring bean via configuration:

```kotlin
import kotless.utilities.auth.AuthWrapper
import kotless.utilities.auth.data.AuthContext
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.JWTVerifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean

class CustomAuthWrapper : AuthWrapper() {
    override fun getJwtVerifier(): JWTVerifier {
        // Return your JWT verifier
        val algorithm = Algorithm.HMAC256("your-secret")
        return JWT.require(algorithm).build()
    }
    
    override fun getServerSignAlgorithm(): Algorithm {
        return Algorithm.HMAC256("server-secret")
    }
    
    override fun getServerSignJwtVerifier(): JWTVerifier {
        val algorithm = Algorithm.HMAC256("server-secret")
        return JWT.require(algorithm).build()
    }
    
    override fun getUserAttributes(username: String): Map<String, String> {
        // Retrieve user attributes from your user store
        return mapOf(
            "email" to "user@example.com",
            "cognito:username" to username
        )
    }
}

// Define as Spring bean in configuration
@Configuration
class CustomAuthConfiguration {
    @Bean
    fun authWrapper(): AuthWrapper {
        return CustomAuthWrapper()
    }
}
```

Then you can inject it in your services:

```kotlin
class AuthService(
    private val authWrapper: AuthWrapper  // Your custom implementation will be injected
) {
    // Use authWrapper as normal
}
```

### Using SecretManager

```kotlin
import kotless.utilities.auth.SecretManager

// Inject SecretManager as a Spring bean (provided by AuthConfiguration)
class SecretService(
    private val secretManager: SecretManager
) {
    // Retrieve a secret (lazy - only when needed)
    fun getDatabasePassword(): String {
        return secretManager.getSecret("DATABASE_PASSWORD")
    }
    
    // Create a new secret
    fun createApiKey(secret: String) {
        secretManager.createSecret(
            name = "API_KEY",
            description = "API key for external service",
            secret = secret
        )
    }
}
```

### Custom SecretManager Implementation

If you need a custom `SecretManager` implementation (e.g., for local development, different cloud providers, or custom secret stores), you should define it as a Spring bean via configuration:

```kotlin
import kotless.utilities.auth.SecretManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class CustomSecretManager : SecretManager {
    override fun getSecret(secretId: String): String {
        // Your custom implementation
        // e.g., read from local file, environment variables, different cloud provider, etc.
        return System.getenv(secretId) ?: throw RuntimeException("Secret not found: $secretId")
    }
    
    override fun createSecret(name: String, description: String, secret: String) {
        // Your custom implementation for creating secrets
        // e.g., write to local file, different cloud provider, etc.
    }
}

// Define as Spring bean in configuration
@Configuration
class CustomSecretManagerConfiguration {
    @Bean
    fun secretManager(): SecretManager {
        return CustomSecretManager()
    }
}
```

Then it will be automatically used by `AuthWrapper` and other services that depend on `SecretManager`:

```kotlin
class AuthService(
    private val authWrapper: AuthWrapper,  // Will use your custom SecretManager
    private val secretManager: SecretManager  // Your custom implementation
) {
    // Use as normal
}
```

### Error Handling

```kotlin
import kotless.utilities.auth.exceptions.AuthException
import kotless.utilities.common.Either

fun handleAuth(token: String): Either<AuthException, String> {
    return authWrapper.withAuth(token) { authContext ->
        "Success"
    }.fold(
        onLeft = { error ->
            when (error.statusCode) {
                401 -> "Token expired"
                403 -> "Invalid token"
                else -> "Authentication failed"
            }
        },
        onRight = { result -> result }
    )
}
```

## Configuration

### AWS Cognito Setup

1. Create a Cognito User Pool in AWS
2. Configure the User Pool ID in your application:
   ```kotlin
   // In AwsConstants.kt
   val awsUserPoolsId = "your-user-pool-id"
   ```

### Server Sign Secret

1. Create a secret in AWS Secrets Manager:
   - Name: `SERVER_SIGN_SECRET`
   - Value: A secure random string (e.g., 256-bit key)

2. Or use environment variables if using a custom implementation

## AuthContext

The `AuthContext` data class contains:
- `authorization`: The original token string
- `username`: The user's username
- `email`: The user's email address
- `server`: Optional server identifier (for server-signed tokens)

You can extend `AuthContext` to include additional fields as needed for your application.

## Best Practices

1. **Token Validation**: Always use `withAuth` or `withServerAuth` to ensure tokens are validated
2. **Error Handling**: Use `Either` pattern for proper error handling
3. **Secret Management**: Never hardcode secrets; always use `SecretManager`
4. **Server Tokens**: Use server-signed tokens for service-to-service communication
5. **Token Expiration**: Handle token expiration gracefully (401 status code)

