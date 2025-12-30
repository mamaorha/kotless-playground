package kotless.playground.core

import io.kotless.PermissionLevel
import io.kotless.dsl.cloud.aws.S3Bucket
import kotless.utilities.storage.aws.AwsStorageClient

private const val bucketName = "CHANGE_ME"

@S3Bucket(bucket = bucketName, level = PermissionLevel.ReadWrite)
object MyBucket : AwsStorageClient(bucketName = bucketName)