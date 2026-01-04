# Common Utility

The Common utility provides shared functionality used across other utilities, including functional programming constructs, image processing, benchmarking, and throttling capabilities.

## Features

- **Either Monad**: Functional error handling with `Either<Left, Right>`
- **Raise Pattern**: Exception-like syntax for `Either` types
- **Generic Utilities**: Image processing, MD5 hashing, iterator generation
- **Benchmarking**: Performance measurement utilities
- **Throttling**: Rate limiting interface and implementations
- **AWS Integration**: Common AWS constants and credential providers

## Components

### Either

A functional programming construct for error handling. Represents a value that can be either a success (`Right`) or a failure (`Left`).

**Key Methods:**
- `map(f: (B) -> C)`: Transform the right value
- `flatMap(f: (B) -> Either<A, C>)`: Chain operations that return `Either`
- `fold(onLeft, onRight)`: Handle both cases
- `getOrNull()`: Get value or null
- `getOrThrow()`: Get value or throw exception

### Raise Pattern

Provides exception-like syntax for working with `Either` types, making code more readable.

### GenericUtils

Utility functions for common operations:
- Image parsing and resizing
- Base64 encoding for images
- MD5 hash generation
- Iterator generation

### Benchmark

Utility for measuring execution time of operations.

### Throttler

Interface for rate limiting/throttling functionality.

## Usage Examples

### Either - Basic Usage

```kotlin
import kotless.utilities.common.Either

// Creating Either values
val success: Either<String, Int> = Either.Right(42)
val failure: Either<String, Int> = Either.Left("Error occurred")

// Mapping
val doubled = success.map { it * 2 } // Either.Right(84)

// Chaining operations
fun divide(a: Int, b: Int): Either<String, Int> {
    return if (b == 0) {
        Either.Left("Division by zero")
    } else {
        Either.Right(a / b)
    }
}

val result = Either.Right(10)
    .flatMap { divide(it, 2) } // Either.Right(5)
    .flatMap { divide(it, 0) } // Either.Left("Division by zero")
```

### Either - Error Handling

```kotlin
import kotless.utilities.common.Either

fun processUser(userId: String?): Either<String, User> {
    return userId?.let { id ->
        // Fetch user from database
        val user = fetchUser(id)
        user?.let { Either.Right(it) } ?: Either.Left("User not found")
    } ?: Either.Left("User ID is null")
}

// Using fold to handle both cases
val result = processUser("123").fold(
    onLeft = { error -> println("Error: $error") },
    onRight = { user -> println("User: ${user.name}") }
)
```

### Raise Pattern

```kotlin
import kotless.utilities.common.either
import kotless.utilities.common.Either

fun fetchUser(id: String): Either<String, User> {
    return either {
        // bind() converts Either.Left to a raised error that is captured by 'either' block
        // If Left, the error is caught and returned as Either.Left
        val user = getUserFromDb(id).bind()
        val profile = getUserProfile(user.id).bind()
        User(user, profile)
    }
}

// Helper functions that return Either
fun getUserFromDb(id: String): Either<String, User> {
    // Implementation
}

fun getUserProfile(userId: String): Either<String, Profile> {
    // Implementation
}
```

### GenericUtils - Image Processing

```kotlin
import kotless.utilities.common.GenericUtils
import java.awt.image.BufferedImage

// Parse image from byte array
val imageBytes: ByteArray = // ... your image data
val image: BufferedImage? = GenericUtils.parseImage(imageBytes)

image?.let { img ->
    // Resize image maintaining aspect ratio
    val resized = GenericUtils.resizeImage(
        image = img,
        maxWidth = 800,
        maxHeight = 600
    )
    
    // Convert to base64 data URL
    val base64Image = GenericUtils.imageToBase64(resized, "png")
    // Returns: "data:image/png;base64,iVBORw0KGgo..."
}
```

### GenericUtils - Hashing

```kotlin
import kotless.utilities.common.GenericUtils

// Create MD5 hash
val data: ByteArray = "Hello, World!".toByteArray()
val hash = GenericUtils.createMd5(data)
// Returns base64-encoded MD5 hash
```

### GenericUtils - Iterator

```kotlin
import kotless.utilities.common.GenericUtils

// Generate infinite iterator starting from 10
val iterator = GenericUtils.iterator(10)
val first = iterator.next() // 10
val second = iterator.next() // 11
val third = iterator.next() // 12
```

### Benchmark

```kotlin
import kotless.utilities.common.Benchmark

// Measure execution time
val result = Benchmark.logTime("Database query") {
    // Your expensive operation
    database.query("SELECT * FROM users")
}

// The benchmark logs: "Database query runtime: 150ms"
```

### Converting Result to Either

```kotlin
import kotless.utilities.common.asEither
import kotless.utilities.common.Either

// Convert Result<T> to Either<Exception, T>
val result: Result<String> = Result.runCatching {
    riskyOperation()
}

val either: Either<Exception, String> = result.asEither()

// Use with Either operations
either.fold(
    onLeft = { ex -> println("Error: ${ex.message}") },
    onRight = { value -> println("Success: $value") }
)
```

### Complex Example - Combining Utilities

```kotlin
import kotless.utilities.common.*
import kotless.utilities.common.either

data class ProcessedImage(
    val originalHash: String,
    val resizedBase64: String,
    val width: Int,
    val height: Int
)

fun processImage(imageBytes: ByteArray): Either<String, ProcessedImage> {
    return either {
        // Parse image
        val image = GenericUtils.parseImage(imageBytes)
            ?: throw RuntimeException("Invalid image format")
        
        // Create hash
        val hash = Benchmark.logTime("Creating hash") {
            GenericUtils.createMd5(imageBytes)
        }
        
        // Resize image
        val resized = Benchmark.logTime("Resizing image") {
            GenericUtils.resizeImage(image, maxWidth = 800, maxHeight = 600)
        }
        
        // Convert to base64
        val base64 = GenericUtils.imageToBase64(resized, "png")
        
        ProcessedImage(
            originalHash = hash,
            resizedBase64 = base64,
            width = resized.width,
            height = resized.height
        )
    }
}

// Usage
val result = processImage(imageBytes).fold(
    onLeft = { error -> println("Failed: $error") },
    onRight = { processed -> 
        println("Processed: ${processed.width}x${processed.height}")
        println("Hash: ${processed.originalHash}")
    }
)
```

## Best Practices

1. **Use Either for Error Handling**: Prefer `Either` over exceptions for expected errors
2. **Use Raise Pattern for Readability**: When chaining multiple `Either` operations, use `either { }` block
3. **Benchmark Critical Operations**: Use `Benchmark.logTime` for performance-sensitive code
4. **Handle Nulls Properly**: Use `Either` to handle nullable values explicitly
5. **Compose Operations**: Use `flatMap` to chain operations that may fail

## Integration with Other Utilities

The Common utility is used extensively by other utilities:
- **Auth**: Uses `Either` for authentication results
- **Cache**: Uses `Either` for DynamoDB operations
- **Mail**: Uses `Either` for email sending results
- **Rest**: Uses `Either` for validation results

