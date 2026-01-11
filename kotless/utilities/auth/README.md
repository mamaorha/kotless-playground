# Auth Utility

The Auth utility provides authentication and authorization functionality for Kotless applications, supporting AWS Cognito JWT tokens, Google OAuth tokens, manual-signed tokens, and server-signed tokens.

## Features

- **JWT Token Verification**: Verify and decode JWT tokens from AWS Cognito, Google OAuth, or manual signing
- **Server-Signed Tokens**: Create and verify server-signed tokens for internal service communication
- **Auth Context**: Extract user information from tokens and build authentication context with payload
- **Secret Management**: Interface for retrieving secrets (e.g., from AWS Secrets Manager)

## Architecture

The auth utility follows a layered architecture:

1. **AuthWrapper** (Interface): Defines the contract for authentication operations
2. **JwtAuthWrapper** (Abstract Class): Base implementation for JWT-based authentication
3. **Provider Implementations**: `AwsAuthWrapper`, `GoogleAuthWrapper`, `ManualAuthWrapper`

## Components

### AuthWrapper

Interface that defines the authentication contract. All authentication wrappers implement this interface.

**Key Methods:**
- `withAuth(token: String, f: (AuthContext) -> T)`: Execute a function with authentication context
  - Automatically handles "Bearer " prefix if present
  - Returns `Either<AuthException, T>` for error handling
- `withServerAuth(token: String, f: (AuthContext) -> T)`: Execute a function requiring server authentication
  - Verifies that the token is a server-signed token (starts with "SRV.")
  - Returns `Either<AuthException, T>` for error handling
- `buildServerSignToken(server: String, payload: AuthContextPayload)`: Create a server-signed token
  - Tokens are prefixed with "SRV." for identification
  - Uses HMAC256 algorithm with secret from `SecretManager`

### JwtAuthWrapper

Abstract base class that implements `AuthWrapper` and provides JWT token verification logic. This class handles:
- Token verification (both regular JWT and server-signed tokens)
- Token prefix handling ("Bearer " and "SRV.")
- AuthContext building from decoded JWT tokens
- Automatic retry for tokens with clock skew issues

**Constructor Parameters:**
- `usernameAttributeKey: String`: The claim key used to identify the username in JWT tokens
- `serverSignIssuer: String`: The issuer value for server-signed tokens

**Abstract Methods to Implement:**
- `jwtVerifier(): JWTVerifier`: Return the JWT verifier for validating regular JWT tokens
- `serverSignAlgorithm(): Algorithm`: Return the HMAC256 algorithm for server-signed tokens

### AwsAuthWrapper

AWS Cognito implementation that extends `JwtAuthWrapper`.

**Features:**
- Uses RSA256 algorithm with keys from AWS Cognito JWKS endpoint
- Uses `cognito:username` as the username attribute
- Provides `getUserAttributes(username: String)` method for retrieving user attributes from Cognito (optional, not part of interface)

**Spring Configuration:**
- `AuthConfiguration` automatically provides `AuthWrapper` and `SecretManager` as Spring beans
- Default implementation uses `AwsAuthWrapper` if no custom bean is provided

**Configuration Required:**
- AWS Cognito User Pool ID (configured via `AwsConstants.awsUserPoolsId`)
- AWS Region (configured via `AwsConstants.awsRegion`)
- Server Sign Secret (stored in AWS Secrets Manager with key `SERVER_SIGN_SECRET`)

**Usage:**

### GoogleAuthWrapper

Google OAuth implementation that extends `JwtAuthWrapper`.

**Features:**
- Uses RSA256 algorithm with keys from Google's OAuth2 certificate endpoint
- Uses `sub` as the username attribute (extracted from token claims)

**Usage:**

**Spring Configuration:**
To use `GoogleAuthWrapper` instead of `AwsAuthWrapper`, define it as a Spring bean:

```kotlin
@Configuration
class GoogleAuthConfiguration {
    @Bean
    fun authWrapper(secretManager: SecretManager): AuthWrapper {
        return GoogleAuthWrapper.build(secretManager)
    }
}
```

### ManualAuthWrapper

Manual authentication implementation that extends `JwtAuthWrapper`. Useful for custom token signing or testing.

**Features:**
- Uses HMAC256 algorithm with secret from `SecretManager` (key: `MANUAL_SIGN_SECRET`)
- Uses `username` as the username attribute
- Provides `manualSign(username: String)` method for creating manually-signed tokens

**Configuration Required:**
- Manual Sign Secret (stored in AWS Secrets Manager with key `MANUAL_SIGN_SECRET`)
- Server Sign Secret (stored in AWS Secrets Manager with key `SERVER_SIGN_SECRET`)

### SecretManager

Interface for managing secrets. Implementations can retrieve secrets from various sources.

### AwsSecretManager

AWS Secrets Manager implementation that retrieves secrets from AWS Secrets Manager.

## Usage Examples

### Basic Authentication

```kotlin
import kotless.utilities.auth.AuthWrapper
import kotless.utilities.auth.data.AuthContext
import kotless.utilities.common.Either

// Inject AuthWrapper as a Spring bean (provided by AuthConfiguration)
class AuthService(
    private val authWrapper: AuthWrapper
) {
    // Use authentication in a handler
    fun handleRequest(token: String): Either<AuthException, String> {
        return authWrapper.withAuth(token) { authContext ->
            // Access user information from payload
            val username = authContext.payload.username
            
            // Your business logic here
            "Hello, $username!"
        }
    }
}
```

### Server Authentication

```kotlin
import kotless.utilities.auth.AuthWrapper
import kotless.utilities.auth.data.AuthContextPayload
import kotless.utilities.common.Either

class AuthService(
    private val authWrapper: AuthWrapper  // Injected Spring bean
) {
    // Create a server-signed token
    fun createServerToken(username: String, server: String): String {
        val payload = AuthContextPayload(
            username = username
            // Add more fields as needed
        )
        return authWrapper.buildServerSignToken(
            server = server,
            payload = payload
        )
    }
    
    // Verify server token in another service
    fun verifyServerToken(token: String): Either<AuthException, String?> {
        return authWrapper.withServerAuth(token) { authContext ->
            // This will only succeed if the token is server-signed
            authContext.server // Returns server name
        }
    }
}
```

### Custom AuthWrapper Implementation

If you need a custom `AuthWrapper` implementation, you can either:

1. **Extend JwtAuthWrapper** (recommended for JWT-based auth):
```kotlin
import kotless.utilities.auth.JwtAuthWrapper
import kotless.utilities.auth.SecretManager
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.JWTVerifier
import kotless.utilities.auth.utils.RsaKeyProviderBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class CustomJwtAuthWrapper(
    secretManager: SecretManager
) : JwtAuthWrapper(
    usernameAttributeKey = "username", // or "cognito:username", etc.
    serverSignIssuer = "your-issuer"
) {
    private val serverSignAlgorithm by lazy {
        val serverSignSecret = secretManager.getSecret("SERVER_SIGN_SECRET")
        Algorithm.HMAC256(serverSignSecret)
    }
    
    override fun jwtVerifier(): JWTVerifier {
        // Return your JWT verifier
        // Example with HMAC256:
        val algorithm = Algorithm.HMAC256("your-secret")
        return JWT.require(algorithm).build()
        
        // Or with RSA256 (like AWS/Google):
        // val keyProvider = RsaKeyProviderBuilder.build("https://your-jwks-url")
        // val algorithm = Algorithm.RSA256(keyProvider)
        // return JWT.require(algorithm).build()
    }
    
    override fun serverSignAlgorithm(): Algorithm {
        return serverSignAlgorithm
    }
}

// Define as Spring bean in configuration
@Configuration
class CustomAuthConfiguration {
    @Bean
    fun authWrapper(secretManager: SecretManager): AuthWrapper {
        return CustomJwtAuthWrapper(secretManager)
    }
}
```

2. **Implement AuthWrapper directly** (for non-JWT authentication):
```kotlin
import kotless.utilities.auth.AuthWrapper
import kotless.utilities.auth.data.AuthContext
import kotless.utilities.auth.data.AuthContextPayload
import kotless.utilities.auth.exceptions.AuthException
import kotless.utilities.common.Either

class CustomAuthWrapper : AuthWrapper {
    override fun <T> withAuth(token: String, f: (AuthContext) -> T): Either<AuthException, T> {
        // Your custom authentication logic
        // Parse token, verify, build AuthContext, etc.
    }
    
    override fun <T> withServerAuth(token: String, f: (AuthContext) -> T): Either<AuthException, T> {
        // Your custom server authentication logic
    }
    
    override fun buildServerSignToken(server: String, payload: AuthContextPayload): String {
        // Your custom server token creation logic
    }
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

### Manual Sign Secret (for ManualAuthWrapper)

1. Create a secret in AWS Secrets Manager:
   - Name: `MANUAL_SIGN_SECRET`
   - Value: A secure random string (e.g., 256-bit key)

## AuthContext and AuthContextPayload

### AuthContext

The `AuthContext` data class contains:
- `authorization`: The original token string
- `server`: Optional server identifier (for server-signed tokens, null for regular tokens)
- `payload`: `AuthContextPayload` containing user information

**Field Order:** `authorization`, `server`, `payload`

### AuthContextPayload

The `AuthContextPayload` data class contains user information extracted from tokens:
- `username`: The user's username (extracted from token claims)

**Note:** You can extend `AuthContextPayload` to include additional fields (e.g., `email`, `roles`, etc.) as needed for your application. See the `buildAuthContext` method in `JwtAuthWrapper` for where to add custom fields.

**Example Extension:**
```kotlin
data class AuthContextPayload(
    val username: String,
    val email: String? = null,
    val roles: List<String> = emptyList()
    // Add more fields as needed
)
```

## Token Handling

### Token Formats

1. **Regular JWT Tokens**: 
   - Can optionally include "Bearer " prefix (automatically stripped)
   - Verified using the provider-specific JWT verifier (AWS Cognito, Google OAuth, or Manual)
   - Username extracted from token claims using `usernameAttributeKey`

2. **Server-Signed Tokens**:
   - Must start with "SRV." prefix
   - Created using `buildServerSignToken(server, payload)`
   - Verified using HMAC256 with secret from `SecretManager`
   - Username and server extracted from token claims

3. **Manual-Signed Tokens** (ManualAuthWrapper only):
   - Created using `manualSign(username)`
   - Verified using HMAC256 with `MANUAL_SIGN_SECRET`
   - No prefix required

### Token Verification Flow

1. **Server Token Detection**: If token starts with "SRV.":
   - Removes "SRV." prefix
   - Verifies using server sign verifier (HMAC256)
   - Extracts username and server from token claims
   - Builds `AuthContext` with server attribute

2. **Regular JWT Token**:
   - Removes "Bearer " prefix if present
   - Verifies using provider-specific JWT verifier
   - Extracts username from token claims using `usernameAttributeKey`
   - Builds `AuthContext` without server attribute

### Token Creation Flow

1. **Server-Signed Token**:
   - Creates JWT with issuer from `serverSignIssuer`
   - Adds username claim from `payload.username`
   - Adds server claim
   - Signs with HMAC256 using `SERVER_SIGN_SECRET`
   - Prefixes with "SRV."

2. **Manual-Signed Token** (ManualAuthWrapper only):
   - Creates JWT with issuer
   - Adds username claim
   - Signs with HMAC256 using `MANUAL_SIGN_SECRET`
   - No prefix

## Best Practices

1. **Token Validation**: Always use `withAuth` or `withServerAuth` to ensure tokens are validated
2. **Error Handling**: Use `Either` pattern for proper error handling
3. **Secret Management**: Never hardcode secrets; always use `SecretManager`
4. **Server Tokens**: Use server-signed tokens for service-to-service communication
5. **Token Expiration**: Handle token expiration gracefully (401 status code)
6. **Token Retry**: The implementation includes automatic retry for tokens that aren't ready yet (handles clock skew)
7. **Lazy Initialization**: JWT verifiers and clients are lazily initialized for better performance
8. **Payload Extension**: Extend `AuthContextPayload` to include all necessary user information for your application
9. **Issuer Configuration**: Update `serverSignIssuer` in your implementations (currently "CHANGE_ME")
