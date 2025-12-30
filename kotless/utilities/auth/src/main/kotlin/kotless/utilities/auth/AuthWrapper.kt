package kotless.utilities.auth

import kotless.utilities.auth.data.AuthContext
import kotless.utilities.auth.exceptions.AuthException
import kotless.utilities.common.Either
import kotless.utilities.common.either
import kotless.utilities.common.flatMap
import kotless.utilities.common.flatten
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.TokenExpiredException
import com.auth0.jwt.interfaces.DecodedJWT
import org.slf4j.LoggerFactory

const val serverSignPrefix = "SRV."
const val serverAttributeKey = "signing-server"

abstract class AuthWrapper {
    companion object {
        private val logger = LoggerFactory.getLogger(AuthWrapper::class.java)
    }

    fun <T> withAuth(token: String, f: (AuthContext) -> T): Either<AuthException, T> {
        return either {
            val attributes = extractAttributes(token).bind()
            val authContext = buildAuthContext(
                token = token,
                attributes = attributes
            ).bind()

            f(authContext)
        }
    }

    fun <T> withServerAuth(token: String, f: (AuthContext) -> T): Either<AuthException, T> {
        return withAuth(token = token) {
            it.server ?: return@withAuth Either.Left(AuthException(statusCode = 403))
            Either.Right(f(it))
        }.flatten()
    }

    fun buildServerSignToken(username: String, server: String): String {
        return serverSignPrefix + JWT.create()
            .withIssuer("CHANGE_ME")
            .withClaim("username", username)
            .withClaim(serverAttributeKey, server)
            .sign(getServerSignAlgorithm())
    }

    protected abstract fun getJwtVerifier(): JWTVerifier
    protected abstract fun getServerSignAlgorithm(): Algorithm

    protected abstract fun getServerSignJwtVerifier(): JWTVerifier

    protected abstract fun getUserAttributes(username: String): Map<String, String>

    private fun getEnrichedUserAttributes(username: String): Map<String, String> {
        return getUserAttributes(username = username) + mapOf("cognito:username" to username)
    }

    private fun extractAttributes(token: String): Either<AuthException, Map<String, String>> {
        return if (token.startsWith(serverSignPrefix)) {
            verify(
                token = token.substring(serverSignPrefix.length),
                jwtVerifier = getServerSignJwtVerifier()
            ).flatMap { decodedJwt ->
                Result.runCatching {
                    val username = decodedJwt.getClaim("username").asString()
                    val server = decodedJwt.getClaim(serverAttributeKey).asString()

                    getEnrichedUserAttributes(username = username) + mapOf(serverAttributeKey to server)
                }.fold(
                    onSuccess = { userAttributes ->
                        Either.Right(userAttributes)
                    },
                    onFailure = { Either.Left(AuthException(403)) }
                )
            }
        } else {
            verify(token = token.removePrefix("Bearer "), jwtVerifier = getJwtVerifier()).map { decodedJwt ->
                decodedJwt.claims.map { it.key to it.value.asString() }.toMap()
            }
        }
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

    private fun buildAuthContext(
        token: String,
        attributes: Map<String, String>,
    ): Either<AuthException, AuthContext> {
        return either {
            val username = attributes["cognito:username"].asEither().bind()
            val email = attributes["email"].asEither().bind()
            val server = attributes[serverAttributeKey]

            //CHANGE in case you need more/less fields adjust this as you see fit
            AuthContext(
                authorization = token,
                username = username,
                email = email,
                server = server
            )
        }
    }

    private fun <T> T?.asEither(): Either<AuthException, T> {
        return this?.let { Either.Right(this) } ?: Either.Left(AuthException(statusCode = 403))
    }
}