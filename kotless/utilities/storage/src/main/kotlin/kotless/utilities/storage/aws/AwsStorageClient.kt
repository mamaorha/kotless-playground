package kotless.utilities.storage.aws

import kotless.utilities.common.AwsConstants.awsRegion
import kotless.utilities.common.AwsCredentialsProvider
import kotless.utilities.common.Benchmark
import kotless.utilities.storage.StorageClient
import io.kotless.dsl.cloud.aws.S3Bucket
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.net.URL
import java.time.Duration

open class AwsStorageClient(bucketName: String) : StorageClient(bucketName = bucketName) {
    companion object {
        private val credentialsProvider = AwsCredentialsProvider.credentialsProvider

        private val client by lazy {
            Benchmark.logTime("building AmazonS3 client") {
                S3Client.builder()
                    .region(awsRegion)
                    .credentialsProvider(credentialsProvider)
                    .build()
            }
        }

        private val presigner by lazy {
            Benchmark.logTime("building AmazonS3 Presigner client") {
                S3Presigner.builder()
                    .region(awsRegion)
                    .credentialsProvider(credentialsProvider)
                    .build()
            }
        }
    }

    init {
        val annotation = javaClass.getAnnotation(S3Bucket::class.java)
        assert(annotation != null)
        assert(bucketName == annotation.bucket)
    }

    override fun uploadFile(path: String, byteArray: ByteArray) {
        val putObjectRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(path)
            .build()

        client.putObject(putObjectRequest, RequestBody.fromBytes(byteArray))
    }

    override fun getFileUrl(path: String, expirationInMillis: Long): URL {
        val getObjectRequest = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(path)
            .build()

        val getObjectPresignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMillis(expirationInMillis))
            .getObjectRequest(getObjectRequest)
            .build()

        val presignGetObject = presigner.presignGetObject(getObjectPresignRequest)
        return presignGetObject.url()
    }

    override fun getFileUrlIfExists(path: String, expirationInMillis: Long): URL? {
        val exists = Result.runCatching {
            val headObjectRequest = HeadObjectRequest.builder().bucket(bucketName).key(path).build()
            client.headObject(headObjectRequest)
            true
        }.getOrElse { false }

        return if (exists) getFileUrl(
            path = path,
            expirationInMillis = expirationInMillis
        ) else null
    }

    override fun getPublicFileUrl(path: String): URL {
        val request = GetUrlRequest.builder()
            .bucket(bucketName)
            .key(path)
            .build()

        return client.utilities().getUrl(request)
    }

    override fun getPublicFileUrlIfExists(path: String): URL? {
        val exists = Result.runCatching {
            val headObjectRequest = HeadObjectRequest.builder().bucket(bucketName).key(path).build()
            client.headObject(headObjectRequest)
            true
        }.getOrElse { false }

        return if (exists) getPublicFileUrl(
            path = path
        ) else null
    }

    override fun deleteFile(path: String) {
            val deleteRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(path)
                .build()
            client.deleteObject(deleteRequest)
            Unit
    }

}