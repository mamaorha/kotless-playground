package kotless.utilities.auth.aws

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.RSAKeyProvider
import io.kotless.PermissionLevel
import io.kotless.dsl.cloud.aws.Cognito
import kotless.utilities.auth.JwtAuthWrapper
import kotless.utilities.auth.SecretManager
import kotless.utilities.auth.utils.RsaKeyProviderBuilder
import kotless.utilities.common.AwsConstants.awsRegion
import kotless.utilities.common.AwsConstants.awsUserPoolsId
import kotless.utilities.common.AwsCredentialsProvider
import kotless.utilities.common.Benchmark
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest

/*
    this works with "cognito" so make sure you properly configure it in aws
*/
@Cognito(userPoolsId = awsUserPoolsId, PermissionLevel.Read)
class AwsAuthWrapper private constructor(secretManager: SecretManager) : JwtAuthWrapper(
    usernameAttributeKey = "cognito:username",
    serverSignIssuer = "CHANGE_ME"
) {
    companion object {
        private val cognitoClient: CognitoIdentityProviderClient by lazy {
            val credentialsProvider = AwsCredentialsProvider.credentialsProvider

            Benchmark.logTime("building CognitoIdentityProvider client") {
                CognitoIdentityProviderClient.builder()
                    .region(awsRegion)
                    .credentialsProvider(credentialsProvider)
                    .build()
            }
        }

        private val awsJwtVerifier by lazy {
            val keyProvider: RSAKeyProvider = Benchmark.logTime("building AwsCognitoRSAKeyProvider") {
                val kidStoreUrl =
                    "https://cognito-idp.${awsRegion.id()}.amazonaws.com/$awsUserPoolsId/.well-known/jwks.json"

                RsaKeyProviderBuilder.build(kidStoreUrl = kidStoreUrl)
            }

            Benchmark.logTime("building awsJwtVerifier") {
                val algorithm: Algorithm = Algorithm.RSA256(keyProvider)
                JWT.require(algorithm).build()
            }
        }

        fun build(secretManager: SecretManager): AwsAuthWrapper {
            return AwsAuthWrapper(secretManager = secretManager)
        }
    }

    private val serverSignAlgorithm by lazy {
        val serverSignSecret =
            secretManager.getSecret("SERVER_SIGN_SECRET") //CHANGE_ME create this secret on secret-manager or use hard-coded value
        Benchmark.logTime("building serverSignAlgorithm") { Algorithm.HMAC256(serverSignSecret) }
    }

    override fun jwtVerifier(): com.auth0.jwt.interfaces.JWTVerifier {
        return awsJwtVerifier
    }

    override fun serverSignAlgorithm(): Algorithm {
        return serverSignAlgorithm
    }

    fun getUserAttributes(username: String): Map<String, String> {
        val response = cognitoClient.adminGetUser(
            AdminGetUserRequest.builder().username(username).userPoolId(awsUserPoolsId).build()
        )

        return response.userAttributes().associate { it.name() to it.value() }
    }
}