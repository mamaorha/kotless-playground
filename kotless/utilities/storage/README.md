# Storage Utility

The Storage utility provides abstracted file storage operations with AWS S3 implementation, supporting file uploads, downloads, and URL generation (both presigned and public URLs).

**Custom Implementations**: You can create your own `StorageClient` implementation (e.g., for Azure Blob Storage, Google Cloud Storage, local filesystem, etc.) and use dependency injection to provide the desired implementation throughout your application.

## Features

- **File Upload**: Upload files to storage
- **Presigned URLs**: Generate time-limited presigned URLs for private files
- **Public URLs**: Generate public URLs for publicly accessible files
- **File Existence Checks**: Check if files exist before generating URLs
- **File Deletion**: Delete files from storage
- **AWS S3 Integration**: Full AWS S3 support via `AwsStorageClient`

## Components

### StorageClient

Abstract base class for storage operations. Defines the interface for storage implementations.

**Key Methods:**
- `uploadFile()`: Upload a file to storage
- `getFileUrl()`: Get presigned URL for a file
- `getFileUrlIfExists()`: Get presigned URL only if file exists
- `getPublicFileUrl()`: Get public URL for a file
- `getPublicFileUrlIfExists()`: Get public URL only if file exists
- `deleteFile()`: Delete a file from storage

### AwsStorageClient

AWS S3 implementation of `StorageClient`. Requires S3 bucket annotation for Kotless deployment.

**Note**: While `AwsStorageClient` can be used directly as a singleton object, you can also create custom `StorageClient` implementations and use dependency injection to provide the desired implementation throughout your application.

## Configuration

### AWS S3 Setup

1. **Create S3 Bucket**: Create an S3 bucket in AWS
2. **Bucket Configuration**: 
   - For public files: Configure bucket policy for public read access
   - For private files: Use presigned URLs (no public access needed)

**Note**: Kotless will automatically assign the appropriate IAM permissions to your serverless function based on the `@S3Bucket` annotation. You don't need to manually configure IAM permissions.

### Kotless Annotation

The `AwsStorageClient` requires the `@S3Bucket` annotation:

```kotlin
import io.kotless.dsl.cloud.aws.S3Bucket
import kotless.utilities.storage.aws.AwsStorageClient

@S3Bucket(bucket = "my-app-storage", level = PermissionLevel.ReadWrite)
object MyStorageClient : AwsStorageClient(bucketName = "my-app-storage")
```

## Usage Examples

### Using StorageClient with Dependency Injection

The recommended approach is to inject `StorageClient` as a Spring bean:

```kotlin
import kotless.utilities.storage.StorageClient

class FileService(
    private val storageClient: StorageClient  // Injected Spring bean
) {
    fun uploadUserAvatar(userId: String, imageData: ByteArray) {
        val path = "avatars/$userId/profile.jpg"
        storageClient.uploadFile(path = path, byteArray = imageData)
    }
}
```

### Using AwsStorageClient Directly (Alternative)

If you prefer to use `AwsStorageClient` directly as a singleton object:

```kotlin
import kotless.utilities.storage.aws.AwsStorageClient
import io.kotless.dsl.cloud.aws.S3Bucket
import io.kotless.PermissionLevel

@S3Bucket(bucket = "my-app-storage", level = PermissionLevel.ReadWrite)
object AppStorageClient : AwsStorageClient(bucketName = "my-app-storage")

// Usage
fun uploadUserAvatar(userId: String, imageData: ByteArray) {
    val path = "avatars/$userId/profile.jpg"
    AppStorageClient.uploadFile(path = path, byteArray = imageData)
}
```

### Upload with Validation

```kotlin
import kotless.utilities.rest.validations.ImageValidation
import kotless.utilities.common.Either

fun uploadValidatedImage(
    userId: String,
    imageData: ByteArray
): Either<BadRequestException, String> {
    // Validate image first
    val validation = ImageValidation.validateImage(
        field = "image",
        byteArray = imageData,
        maxWidth = 1920,
        maxHeight = 1080
    )
    
    return validation.map {
        val path = "users/$userId/avatar.jpg"
        AppStorageClient.uploadFile(path = path, byteArray = imageData)
        path
    }
}
```

### Generate Presigned URLs

```kotlin
import java.net.URL
import java.util.concurrent.TimeUnit

fun getPrivateFileUrl(filePath: String, expirationHours: Long = 24): URL {
    val expirationMillis = TimeUnit.HOURS.toMillis(expirationHours)
    return AppStorageClient.getFileUrl(
        path = filePath,
        expirationInMillis = expirationMillis
    )
}

// Usage
fun shareDocument(userId: String, documentId: String): URL {
    val filePath = "documents/$userId/$documentId.pdf"
    return getPrivateFileUrl(filePath, expirationHours = 48) // 48 hour expiration
}
```

### Check File Existence Before Getting URL

```kotlin
fun getFileUrlIfExists(filePath: String): URL? {
    val expirationMillis = TimeUnit.HOURS.toMillis(24)
    return AppStorageClient.getFileUrlIfExists(
        path = filePath,
        expirationInMillis = expirationMillis
    )
}

// Usage
fun getDocumentUrl(userId: String, documentId: String): URL? {
    val filePath = "documents/$userId/$documentId.pdf"
    return getFileUrlIfExists(filePath)
        ?: throw NotFoundException("Document not found")
}
```

### Public File URLs

```kotlin
fun getPublicFileUrl(filePath: String): URL {
    return AppStorageClient.getPublicFileUrl(path = filePath)
}

// Usage for public assets
fun getPublicAssetUrl(assetPath: String): URL {
    val fullPath = "public/$assetPath"
    return getPublicFileUrl(fullPath)
}
```

### Complete File Management Service

```kotlin
import kotless.utilities.common.Either
import kotless.utilities.common.either
import kotless.utilities.rest.exceptions.NotFoundException
import java.util.concurrent.TimeUnit

object FileService {
    fun uploadFile(
        userId: String,
        fileName: String,
        fileData: ByteArray,
        folder: String = "uploads"
    ): Either<Exception, String> {
        return either {
            val path = "$folder/$userId/$fileName"
            AppStorageClient.uploadFile(path = path, byteArray = fileData)
            path
        }
    }
    
    fun getFileDownloadUrl(
        filePath: String,
        expirationHours: Long = 24
    ): Either<NotFoundException, URL> {
        return either {
            val expirationMillis = TimeUnit.HOURS.toMillis(expirationHours)
            AppStorageClient.getFileUrlIfExists(
                path = filePath,
                expirationInMillis = expirationMillis
            ) ?: throw NotFoundException("File not found: $filePath")
        }
    }
    
    fun deleteFile(filePath: String): Either<Exception, Unit> {
        return either {
            AppStorageClient.deleteFile(path = filePath)
        }
    }
    
    fun fileExists(filePath: String): Boolean {
        val expirationMillis = TimeUnit.HOURS.toMillis(1)
        return AppStorageClient.getFileUrlIfExists(
            path = filePath,
            expirationInMillis = expirationMillis
        ) != null
    }
}
```

### Image Upload with Resizing

```kotlin
import kotless.utilities.common.GenericUtils
import kotless.utilities.rest.validations.ImageValidation
import kotless.utilities.common.Either
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

fun uploadAndResizeImage(
    userId: String,
    originalImage: ByteArray
): Either<BadRequestException, ImageUploadResult> {
    return ImageValidation.validateImageAndResizeIfNeeded(
        field = "image",
        byteArray = originalImage,
        maxWidth = 1920,
        maxHeight = 1080
    ).map { resizedImage ->
        // Upload original
        val originalPath = "images/$userId/original.jpg"
        AppStorageClient.uploadFile(path = originalPath, byteArray = originalImage)
        
        // Upload resized
        val resizedPath = "images/$userId/resized.jpg"
        AppStorageClient.uploadFile(path = resizedPath, byteArray = resizedImage)
        
        // Generate URLs
        val originalUrl = AppStorageClient.getFileUrl(
            path = originalPath,
            expirationInMillis = TimeUnit.DAYS.toMillis(7)
        )
        
        val resizedUrl = AppStorageClient.getPublicFileUrl(path = resizedPath)
        
        ImageUploadResult(
            originalUrl = originalUrl.toString(),
            resizedUrl = resizedUrl.toString()
        )
    }
}
```

### Batch File Operations

```kotlin
fun uploadMultipleFiles(
    userId: String,
    files: List<Pair<String, ByteArray>> // (filename, data)
): List<Pair<String, Either<Exception, String>>> {
    return files.map { (filename, data) ->
        val path = "uploads/$userId/$filename"
        val result = Result.runCatching {
            AppStorageClient.uploadFile(path = path, byteArray = data)
            path
        }
        
        filename to result.fold(
            onSuccess = { Either.Right(it) },
            onFailure = { Either.Left(it) }
        )
    }
}
```

### File Versioning

```kotlin
fun uploadFileVersion(
    userId: String,
    fileName: String,
    fileData: ByteArray,
    version: Int
): String {
    val path = "files/$userId/$fileName.v$version"
    AppStorageClient.uploadFile(path = path, byteArray = fileData)
    return path
}

fun getLatestFileVersion(userId: String, fileName: String): URL? {
    var version = 1
    
    while (true) {
        val path = "files/$userId/$fileName.v$version"
        val url = AppStorageClient.getFileUrlIfExists(
            path = path,
            expirationInMillis = TimeUnit.HOURS.toMillis(24)
        )
        
        if (url != null) {
            version++
        } else {
            if (version == 1) return null
            val previousPath = "files/$userId/$fileName.v${version - 1}"
            return AppStorageClient.getFileUrl(
                path = previousPath,
                expirationInMillis = TimeUnit.HOURS.toMillis(24)
            )
        }
    }
}
```

### Custom Storage Implementation

You can create your own `StorageClient` implementation for different storage backends (e.g., Azure Blob Storage, Google Cloud Storage, local filesystem, etc.) and use dependency injection to provide it throughout your application:

```kotlin
import kotless.utilities.storage.StorageClient
import java.net.URL
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// Example: Custom storage client for Azure Blob Storage
class AzureStorageClient(bucketName: String) : StorageClient(bucketName) {
    override fun uploadFile(path: String, byteArray: ByteArray) {
        // Your Azure Blob Storage upload implementation
    }
    
    override fun getFileUrl(path: String, expirationInMillis: Long): URL {
        // Generate Azure Blob Storage presigned URL
        return URL("https://your-storage-account.blob.core.windows.net/$bucketName/$path?expires=$expirationInMillis")
    }
    
    override fun getFileUrlIfExists(path: String, expirationInMillis: Long): URL? {
        return if (fileExists(path)) {
            getFileUrl(path, expirationInMillis)
        } else {
            null
        }
    }
    
    override fun getPublicFileUrl(path: String): URL {
        return URL("https://your-storage-account.blob.core.windows.net/$bucketName/$path")
    }
    
    override fun getPublicFileUrlIfExists(path: String): URL? {
        return if (fileExists(path)) {
            getPublicFileUrl(path)
        } else {
            null
        }
    }
    
    override fun deleteFile(path: String) {
        // Your Azure Blob Storage delete implementation
    }
    
    private fun fileExists(path: String): Boolean {
        // Your custom existence check
        return true
    }
}

// Define as Spring bean in configuration
@Configuration
class StorageConfiguration {
    @Bean
    fun storageClient(): StorageClient {
        // Return your custom implementation
        return AzureStorageClient(bucketName = "my-storage-container")
        
        // Or return AwsStorageClient if using AWS S3
        // return AwsStorageClient(bucketName = "my-app-storage")
    }
}
```

Then inject and use it in your services:

```kotlin
import kotless.utilities.storage.StorageClient

class FileService(
    private val storageClient: StorageClient  // Your custom implementation will be injected
) {
    fun uploadFile(userId: String, fileName: String, fileData: ByteArray): String {
        val path = "uploads/$userId/$fileName"
        storageClient.uploadFile(path = path, byteArray = fileData)
        return path
    }
    
    fun getFileUrl(filePath: String, expirationHours: Long = 24): URL {
        val expirationMillis = TimeUnit.HOURS.toMillis(expirationHours)
        return storageClient.getFileUrl(
            path = filePath,
            expirationInMillis = expirationMillis
        )
    }
}
```

This approach allows you to:
- Switch between different storage implementations (AWS S3, Azure, Google Cloud, etc.) by changing the bean definition
- Test with mock implementations
- Use different storage backends for different environments

## Best Practices

1. **Path Organization**: Use organized folder structures (e.g., `users/{userId}/files/`)
2. **File Naming**: Use unique filenames to prevent conflicts (timestamps, UUIDs)
3. **URL Expiration**: Set appropriate expiration times for presigned URLs
4. **File Validation**: Validate files before uploading (size, type, content)
5. **Error Handling**: Always handle storage errors appropriately
6. **Public vs Private**: Use public URLs only for truly public content
7. **File Cleanup**: Implement cleanup strategies for temporary files
8. **Versioning**: Consider versioning for important files
9. **Metadata**: Store file metadata separately (database) for better querying
10. **Security**: Validate file types and scan for malicious content

## Security Considerations

1. **Access Control**: Use presigned URLs for private files
2. **File Type Validation**: Always validate file types before upload
3. **Size Limits**: Enforce file size limits
4. **Virus Scanning**: Scan uploaded files for malware
5. **Path Traversal**: Sanitize file paths to prevent directory traversal attacks
6. **Bucket Policies**: Configure S3 bucket policies appropriately
7. **IAM Permissions**: Use least-privilege IAM policies

## Integration with Other Utilities

The Storage utility integrates with:
- **Common**: Uses `GenericUtils` for image processing
- **REST**: Uses `ImageValidation` for validating uploads
- **Auth**: Can be used with authentication for secure file access
- **Cache**: Can cache file metadata in DynamoDB

