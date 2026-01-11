package kotless.utilities.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.TokenExpiredException
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.interfaces.JWTVerifier
import kotless.utilities.auth.data.AuthContext
import kotless.utilities.auth.data.AuthContextPayload
import kotless.utilities.auth.exceptions.AuthException
import kotless.utilities.common.Benchmark
import kotless.utilities.common.Either
import kotless.utilities.common.either
import org.slf4j.LoggerFactory

private const val serverSignPrefix = "SRV."
private const val serverAttributeKey = "signing-server"

abstract class JwtAuthWrapper(
    val usernameAttributeKey: String,
    val serverSignIssuer: String
) : AuthWrapper {
    companion object {
        private val logger = LoggerFactory.getLogger(JwtAuthWrapper::class.java)
    }

    abstract fun jwtVerifier(): JWTVerifier

    abstract fun serverSignAlgorithm(): Algorithm

    private val serverSignJwtVerifier by lazy {
        Benchmark.logTime("building serverSignJwtVerifier") {
            JWT.require(
                serverSignAlgorithm()
            ).build()
        }
    }

    private fun isServerToken(token: String): Boolean {
        return token.startsWith(serverSignPrefix)
    }

    override fun <T> withAuth(
        token: String,
        f: (AuthContext) -> T
    ): Either<AuthException, T> {
        return either {
            val decodedJwt = getDecodedJwt(token = token).bind()
            val authContext = buildAuthContext(token = token, decodedJwt = decodedJwt).bind()

            f(authContext)
        }
    }

    override fun <T> withServerAuth(
        token: String,
        f: (AuthContext) -> T
    ): Either<AuthException, T> {
        if (!isServerToken(token = token)) {
            return Either.Left(AuthException(statusCode = 403))
        }

        return withAuth(token = token) {
            f(it)
        }
    }

    override fun buildServerSignToken(server: String, payload: AuthContextPayload): String {
        return serverSignPrefix + JWT.create()
            .withIssuer(serverSignIssuer)
            //CHANGE_ME -> add claims based on the payload
            .withClaim(usernameAttributeKey, payload.username)
            .withClaim(serverAttributeKey, server)
            .sign(serverSignAlgorithm())
    }

    private fun buildAuthContext(
        token: String,
        decodedJwt: DecodedJWT
    ): Either<AuthException, AuthContext> {
        return either {
            val server = decodedJwt.claims[serverAttributeKey]?.asString()

            //CHANGE_ME in case you need more/fewer fields adjust this as you see fit
            val username = decodedJwt.claims[usernameAttributeKey].asEither().bind().asString()

            val payload = AuthContextPayload(
                username = username
            )

            AuthContext(
                authorization = token,
                server = server,
                payload = payload
            )
        }
    }

    private fun getDecodedJwt(token: String): Either<AuthException, DecodedJWT> {
        val isServer = isServerToken(token = token)

        val (cleanToken, verifier) = if (isServer) {
            token.removePrefix(serverSignPrefix) to serverSignJwtVerifier
        } else {
            token.removePrefix("Bearer ") to jwtVerifier()
        }

        return verify(token = cleanToken, jwtVerifier = verifier)
    }

    private fun verify(
        token: String,
        jwtVerifier: JWTVerifier,
        retry: Boolean = true
    ): Either<AuthException, DecodedJWT> {
        return try {
            Either.Right(jwtVerifier.verify(token))
        } catch (e: Exception) {
            if (e is TokenExpiredException) {
                return Either.Left(AuthException(401))
            }

            if (retry && e.message?.contains("The Token can't be used before") == true) {
                logger.info("token is not ready yet, waiting a bit and retrying")
                Thread.sleep(500)
                return verify(token = token, jwtVerifier = jwtVerifier, retry = false)
            }

            return Either.Left(AuthException(403))
        }
    }

    private fun <T> T?.asEither(): Either<AuthException, T> {
        return this?.let { Either.Right(this) } ?: Either.Left(AuthException(statusCode = 403))
    }
}