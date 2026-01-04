# REST Utility

The REST utility provides comprehensive REST API support including exception handling, validation, CORS configuration, Feign client building, and image validation.

## Features

- **Exception Handling**: Centralized exception handling with proper HTTP status codes
- **Field Validation**: Utilities for validating request fields
- **Image Validation**: Validate and resize images
- **CORS Configuration**: Cross-Origin Resource Sharing support
- **Feign Client Builder**: Build Feign HTTP clients with Jackson serialization
- **Custom Object Mapper**: Configured Jackson ObjectMapper for JSON handling
- **Enum Conversion**: Support for enum conversion in REST endpoints

## Components

### ExceptionHandlerController

Global exception handler that converts exceptions to appropriate HTTP responses.

**Handles:**
- `RestException`: Custom REST exceptions with HTTP status codes
- `AuthException`: Authentication/authorization errors
- `ThrottlerException`: Rate limiting errors
- `HttpMessageNotReadableException`: JSON parsing errors
- Generic exceptions: Returns 500 Internal Server Error

### FieldValidations

Utility functions for validating request fields:
- `asMandatory()`: Ensure field is not null
- `validate()`: Validate string/integer length/range
- `asNotFound()`: Check if resource exists

### ImageValidation

Utilities for validating and processing images:
- `validateImage()`: Validate image dimensions
- `validateImageAndResizeIfNeeded()`: Validate and auto-resize if too large

### FeignBuilder

Builder for creating Feign HTTP clients with Jackson serialization.

### CorsConfiguration

CORS filter that allows cross-origin requests.

## Usage Examples

### Exception Handling

The `ExceptionHandlerController` automatically handles exceptions. Just throw the appropriate exception:

```kotlin
import kotless.utilities.rest.exceptions.*
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users")
class UserController {
    
    @GetMapping("/{id}")
    fun getUser(@PathVariable id: String): User {
        val user = userService.findById(id)
            ?: throw NotFoundException(resource = "User")
        
        return user
    }
    
    @PostMapping
    fun createUser(@RequestBody request: CreateUserRequest): User {
        // Validate required fields
        if (request.name.isNullOrBlank()) {
            throw MissingMandatoryFieldException(field = "name")
        }
        
        if (request.email.isNullOrBlank()) {
            throw MissingMandatoryFieldException(field = "email")
        }
        
        // Check if user already exists
        if (userService.existsByEmail(request.email)) {
            throw AlreadyExistException(resource = "User")
        }
        
        return userService.create(request)
    }
    
    @PutMapping("/{id}")
    fun updateUser(
        @PathVariable id: String,
        @RequestBody request: UpdateUserRequest
    ): User {
        val user = userService.findById(id)
            ?: throw NotFoundException(resource = "User")
        
        // Check permissions
        if (!hasPermission(user)) {
            throw PermissionDeniedException(resource = "User")
        }
        
        return userService.update(id, request)
    }
}
```

### Field Validation

```kotlin
import kotless.utilities.rest.validations.FieldValidations
import kotless.utilities.common.Either
import kotless.utilities.common.either

@PostMapping("/api/users")
fun createUser(@RequestBody request: CreateUserRequest): Either<RestException, User> {
    return either {
        // Validate mandatory fields
        val name = request.name.asMandatory("name").bind()
        val email = request.email.asMandatory("email").bind()
        
        // Validate string length
        val validatedName = name.validate("name", minLength = 2, maxLength = 50).bind()
        val validatedEmail = email.validate("email", minLength = 5, maxLength = 100).bind()
        
        // Validate integer range
        val age = request.age?.validate("age", min = 18, max = 120)
            ?: throw MissingMandatoryFieldException(field = "age")
        val validatedAge = age.bind()
        
        // Create user
        userService.create(
            name = validatedName,
            email = validatedEmail,
            age = validatedAge
        )
    }
}
```

### Image Validation

```kotlin
import kotless.utilities.rest.validations.ImageValidation
import kotless.utilities.common.Either
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/images")
class ImageController {
    
    @PostMapping("/upload")
    fun uploadImage(
        @RequestParam("file") file: MultipartFile
    ): Either<BadRequestException, ImageUploadResponse> {
        // Validate image
        val validationResult = ImageValidation.validateImage(
            field = "file",
            byteArray = file.bytes,
            maxWidth = 1920,
            maxHeight = 1080
        )
        
        return validationResult.map {
            // Image is valid, proceed with upload
            val imageUrl = imageService.upload(file.bytes)
            ImageUploadResponse(imageUrl = imageUrl)
        }
    }
    
    @PostMapping("/upload-resize")
    fun uploadAndResizeImage(
        @RequestParam("file") file: MultipartFile
    ): Either<BadRequestException, ImageUploadResponse> {
        // Validate and auto-resize if needed
        val resizedImage = ImageValidation.validateImageAndResizeIfNeeded(
            field = "file",
            byteArray = file.bytes,
            maxWidth = 1920,
            maxHeight = 1080
        )
        
        return resizedImage.map { imageData ->
            // Upload resized image
            val imageUrl = imageService.upload(imageData)
            ImageUploadResponse(imageUrl = imageUrl)
        }
    }
}
```

### Using Feign Client

```kotlin
import kotless.utilities.rest.feign.FeignBuilder
import org.springframework.web.bind.annotation.*

// Define the Feign client interface
interface ExternalApiClient {
    @GetMapping("/users/{id}")
    fun getUser(@PathVariable("id") id: String): User
    
    @PostMapping("/users")
    fun createUser(@RequestBody user: CreateUserRequest): User
    
    @PutMapping("/users/{id}")
    fun updateUser(
        @PathVariable("id") id: String,
        @RequestBody user: UpdateUserRequest
    ): User
}

// Build and use the client
class UserService {
    // Lazy initialization - client is only created when needed
    private val externalApi: ExternalApiClient by lazy {
        FeignBuilder.build(
            target = ExternalApiClient::class.java,
            url = "https://api.example.com"
        )
    }
    
    fun fetchExternalUser(id: String): User {
        return externalApi.getUser(id)
    }
    
    fun createExternalUser(request: CreateUserRequest): User {
        return externalApi.createUser(request)
    }
}
```

### Custom Exception Handling

```kotlin
import kotless.utilities.rest.exceptions.RestException
import org.springframework.http.HttpStatus

// Create custom exception
class InsufficientFundsException(
    val balance: Double,
    val required: Double
) : RestException(
    httpStatus = HttpStatus.PAYMENT_REQUIRED,
    error = "Insufficient funds. Balance: $balance, Required: $required"
)

// Use in controller
@PostMapping("/api/payments")
fun processPayment(@RequestBody request: PaymentRequest): PaymentResponse {
    val balance = accountService.getBalance(request.accountId)
    
    if (balance < request.amount) {
        throw InsufficientFundsException(
            balance = balance,
            required = request.amount
        )
    }
    
    return paymentService.process(request)
}
```

### Resource Not Found Validation

```kotlin
import kotless.utilities.rest.validations.FieldValidations
import kotless.utilities.common.either

@GetMapping("/api/users/{id}")
fun getUser(@PathVariable id: String): Either<RestException, User> {
    return either {
        val user = userService.findById(id)
            .asNotFound("User")
            .bind()
        
        user
    }
}
```

### Complex Validation Example

```kotlin
import kotless.utilities.rest.validations.FieldValidations
import kotless.utilities.common.either

data class CreateProductRequest(
    val name: String?,
    val description: String?,
    val price: Double?,
    val stock: Int?,
    val categoryId: String?
)

@PostMapping("/api/products")
fun createProduct(
    @RequestBody request: CreateProductRequest
): Either<RestException, Product> {
    return either {
        // Validate all mandatory fields
        val name = request.name
            .asMandatory("name")
            .bind()
            .validate("name", minLength = 3, maxLength = 100)
            .bind()
        
        val description = request.description
            .asMandatory("description")
            .bind()
            .validate("description", minLength = 10, maxLength = 1000)
            .bind()
        
        val price = request.price
            ?.let { 
                if (it <= 0) {
                    throw BadRequestException("Price must be greater than 0")
                }
                it
            }
            ?: throw MissingMandatoryFieldException(field = "price")
        
        val stock = request.stock
            ?.validate("stock", min = 0, max = 10000)
            ?: throw MissingMandatoryFieldException(field = "stock")
        val validatedStock = stock.bind()
        
        // Check if category exists
        val category = categoryService.findById(request.categoryId)
            .asNotFound("Category")
            .bind()
        
        // Create product
        productService.create(
            name = name,
            description = description,
            price = price,
            stock = validatedStock,
            categoryId = category.id
        )
    }
}
```

### CORS Configuration

The `CorsConfiguration` is automatically applied. To customize CORS behavior, extend the class:

```kotlin
import kotless.utilities.rest.configuration.CorsConfiguration
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class CustomCorsConfiguration(
    @Value("\${cors.allowed-origins:*}") 
    private val allowedOrigins: String
) : CorsConfiguration() {
    
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // Custom CORS headers
        response.addHeader("Access-Control-Allow-Origin", allowedOrigins)
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        response.addHeader("Access-Control-Max-Age", "3600")
        
        if (request.method == "OPTIONS") {
            response.status = HttpServletResponse.SC_OK
            return
        }
        
        filterChain.doFilter(request, response)
    }
}
```

### Available REST Exceptions

```kotlin
// 400 Bad Request
BadRequestException(error: String)

// 401 Unauthorized (handled by AuthException)

// 403 Forbidden
PermissionDeniedException(resource: String)

// 404 Not Found
NotFoundException(resource: String)

// 409 Conflict
ConflictException(resource: String)

// 412 Precondition Failed
PreconditionFailedException(resource: String)

// 422 Unprocessable Entity
MissingMandatoryFieldException(field: String)

// 429 Too Many Requests
TooManyRequestsException(resource: String)

// 500 Internal Server Error (default for unhandled exceptions)
```

### Error Response Format

All exceptions return a consistent error response:

```json
{
  "code": 404,
  "error": "User not found"
}
```

## Best Practices

1. **Use FieldValidations**: Always validate input using `FieldValidations` utilities
2. **Throw Appropriate Exceptions**: Use the correct exception type for each error scenario
3. **Image Validation**: Always validate image dimensions before processing
4. **Error Messages**: Provide clear, user-friendly error messages
5. **CORS Configuration**: Configure CORS appropriately for your security requirements
6. **Feign Clients**: Use Feign clients for external API calls
7. **Exception Handling**: Let `ExceptionHandlerController` handle exceptions; don't catch and convert manually
8. **Validation Order**: Validate mandatory fields first, then format/range validations
9. **Resource Checks**: Use `asNotFound()` to check if resources exist before operations
10. **Type Safety**: Use `Either` pattern for operations that can fail

## Integration with Other Utilities

The REST utility integrates with:
- **Auth**: Uses `AuthException` for authentication errors
- **Common**: Uses `Either` for functional error handling
- **Cache**: Can use throttling exceptions
- **Storage**: Can validate images before uploading

