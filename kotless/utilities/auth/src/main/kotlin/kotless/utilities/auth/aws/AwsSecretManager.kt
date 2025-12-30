package kotless.utilities.auth.aws

import kotless.utilities.auth.SecretManager
import kotless.utilities.common.AwsConstants.awsRegion
import kotless.utilities.common.AwsCredentialsProvider
import kotless.utilities.common.Benchmark
import io.kotless.PermissionLevel
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest
import io.kotless.dsl.cloud.aws.SecretManager as SecretManagerPermission

@SecretManagerPermission(pattern = "*", level = PermissionLevel.Read)
class AwsSecretManager private constructor() : SecretManager {
    companion object {
        val awsSecretManager by lazy {
            AwsSecretManager()
        }
    }

    private val secretsManagerClient: SecretsManagerClient by lazy {
        val credentialsProvider = AwsCredentialsProvider.credentialsProvider

        Benchmark.logTime("building SecretsManager client") {
            SecretsManagerClient.builder()
                .region(awsRegion)
                .credentialsProvider(credentialsProvider)
                .build()
        }
    }

    override fun getSecret(secretId: String): String {
        //make sure the init time is not counted twice
        secretsManagerClient

        return Benchmark.logTime("get secret: $secretId") {
            val getSecretValueRequest = GetSecretValueRequest.builder().secretId(secretId).build()
            secretsManagerClient.getSecretValue(getSecretValueRequest).secretString()
        }
    }

    override fun createSecret(name: String, description: String, secret: String) {
        secretsManagerClient

        Benchmark.logTime("create secret: $name") {
            val createSecretRequest = CreateSecretRequest.builder()
                .name(name)
                .description(description)
                .secretString(secret)
                .build()

            secretsManagerClient.createSecret(
                createSecretRequest
            )
        }
    }
}