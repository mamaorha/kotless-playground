package kotless.utilities.auth.manual

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.JWTVerifier
import kotless.utilities.auth.JwtAuthWrapper
import kotless.utilities.auth.SecretManager
import kotless.utilities.common.Benchmark

class ManualAuthWrapper private constructor(secretManager: SecretManager) : JwtAuthWrapper(
    usernameAttributeKey = "username",
    serverSignIssuer = "CHANGE_ME"
) {
    companion object {
        fun build(secretManager: SecretManager): ManualAuthWrapper {
            return ManualAuthWrapper(secretManager = secretManager)
        }
    }

    private val serverSignAlgorithm by lazy {
        val serverSignSecret =
            secretManager.getSecret("SERVER_SIGN_SECRET") //CHANGE_ME create this secret on secret-manager or use hard-coded value
        Benchmark.logTime("building serverSignAlgorithm") { Algorithm.HMAC256(serverSignSecret) }
    }

    private val manualSignAlgorithm by lazy {
        val manualSignSecret =
            secretManager.getSecret("MANUAL_SIGN_SECRET") //CHANGE_ME create this secret on secret-manager or use hard-coded value
        Benchmark.logTime("building manualSignAlgorithm") { Algorithm.HMAC256(manualSignSecret) }
    }

    private val manualSignJwtVerifier by lazy {
        Benchmark.logTime("building manualSignJwtVerifier") {
            JWT.require(
                manualSignAlgorithm
            ).build()
        }
    }

    fun manualSign(username: String): String {
        return JWT.create()
            .withIssuer("CHANGE_ME")
            .withClaim("username", username)
            //TODO - add more claims as you see fit
            .sign(manualSignAlgorithm)
    }

    override fun jwtVerifier(): JWTVerifier {
        return manualSignJwtVerifier
    }

    override fun serverSignAlgorithm(): Algorithm {
        return serverSignAlgorithm()
    }
}