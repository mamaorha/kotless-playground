package kotless.utilities.auth.aws

import kotless.utilities.auth.AuthWrapper
import kotless.utilities.auth.SecretManager
import kotless.utilities.common.AwsConstants.awsRegion
import kotless.utilities.common.AwsConstants.awsUserPoolsId
import kotless.utilities.common.AwsCredentialsProvider
import kotless.utilities.common.Benchmark
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.RSAKeyProvider
import io.kotless.PermissionLevel
import io.kotless.dsl.cloud.aws.Cognito
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest

/*
    this works with "cognito" so make sure you properly configure it in aws
*/
@Cognito(userPoolsId = awsUserPoolsId, PermissionLevel.Read)
class AwsAuthWrapper private constructor(secretManager: SecretManager) : AuthWrapper() {
    companion object {
        fun build(secretManager: SecretManager): AwsAuthWrapper {
            return AwsAuthWrapper(secretManager = secretManager)
        }
    }

    private val cognitoClient: CognitoIdentityProviderClient by lazy {
        val credentialsProvider = AwsCredentialsProvider.credentialsProvider

        Benchmark.logTime("building CognitoIdentityProvider client") {
            CognitoIdentityProviderClient.builder()
                .region(awsRegion)
                .credentialsProvider(credentialsProvider)
                .build()
        }
    }

    private val awsJwtVerifierValue by lazy {
        val keyProvider: RSAKeyProvider = Benchmark.logTime("building AwsCognitoRSAKeyProvider") {
            AwsCognitoRSAKeyProvider(
                awsCognitoRegion = awsRegion.id(),
                awsUserPoolsId = awsUserPoolsId
            )
        }

        Benchmark.logTime("building awsJwtVerifier") {
            val algorithm: Algorithm = Algorithm.RSA256(keyProvider)
            JWT.require(algorithm).build()
        }
    }

    private val serverSignAlgorithmValue by lazy {
        val serverSignSecret = secretManager.getSecret("SERVER_SIGN_SECRET") //CHANGE_ME create this secret on secret-manager or use
        Benchmark.logTime("building serverSignAlgorithm") { Algorithm.HMAC256(serverSignSecret) }
    }

    private val serverSignJwtVerifierValue by lazy {
        Benchmark.logTime("building serverSignJwtVerifier") {
            JWT.require(
                serverSignAlgorithmValue
            ).build()
        }
    }

    override fun getJwtVerifier(): JWTVerifier {
        return awsJwtVerifierValue
    }

    override fun getServerSignAlgorithm(): Algorithm {
        return serverSignAlgorithmValue
    }

    override fun getServerSignJwtVerifier(): JWTVerifier {
        return serverSignJwtVerifierValue
    }

    override fun getUserAttributes(username: String): Map<String, String> {
        val response = cognitoClient.adminGetUser(
            AdminGetUserRequest.builder().username(username).userPoolId(awsUserPoolsId).build()
        )

        return response.userAttributes().associate { it.name() to it.value() }
    }
}