package kotless.utilities.auth.google

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.RSAKeyProvider
import kotless.utilities.auth.JwtAuthWrapper
import kotless.utilities.auth.SecretManager
import kotless.utilities.auth.utils.RsaKeyProviderBuilder
import kotless.utilities.common.Benchmark

class GoogleAuthWrapper private constructor(secretManager: SecretManager) : JwtAuthWrapper(
    usernameAttributeKey = "sub",
    serverSignIssuer = "CHANGE_ME"
) {
    companion object {
        private val googleJwtVerifier by lazy {
            val keyProvider: RSAKeyProvider = Benchmark.logTime("building GoogleRSAKeyProvider") {
                RsaKeyProviderBuilder.build(kidStoreUrl = "https://www.googleapis.com/oauth2/v3/certs")
            }

            Benchmark.logTime("building googleJwtVerifier") {
                val algorithm: Algorithm = Algorithm.RSA256(keyProvider)
                JWT.require(algorithm).build()
            }
        }

        fun build(secretManager: SecretManager): GoogleAuthWrapper {
            return GoogleAuthWrapper(secretManager = secretManager)
        }
    }

    private val serverSignAlgorithm by lazy {
        val serverSignSecret =
            secretManager.getSecret("SERVER_SIGN_SECRET") //CHANGE_ME create this secret on secret-manager or use hard-coded value
        Benchmark.logTime("building serverSignAlgorithm") { Algorithm.HMAC256(serverSignSecret) }
    }

    override fun jwtVerifier(): com.auth0.jwt.interfaces.JWTVerifier {
        return googleJwtVerifier
    }

    override fun serverSignAlgorithm(): Algorithm {
        return serverSignAlgorithm
    }
}