package kotless.utilities.storage

import java.net.URL

abstract class StorageClient(val bucketName: String) {
    abstract fun uploadFile(path: String, byteArray: ByteArray)
    abstract fun getFileUrl(path: String, expirationInMillis: Long): URL
    abstract fun getFileUrlIfExists(path: String, expirationInMillis: Long): URL?
    abstract fun getPublicFileUrl(path: String): URL
    abstract fun getPublicFileUrlIfExists(path: String): URL?
    abstract fun deleteFile(path: String)
}