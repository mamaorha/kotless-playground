package kotless.utilities.auth.aws

import com.auth0.jwk.JwkException
import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.interfaces.RSAKeyProvider
import java.net.URI
import java.net.URL
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

class AwsCognitoRSAKeyProvider(
    awsCognitoRegion: String,
    awsUserPoolsId: String
) : RSAKeyProvider {
    private val awsKidStoreUrl: URL
    private val provider: JwkProvider

    init {
        val url = "https://cognito-idp.$awsCognitoRegion.amazonaws.com/$awsUserPoolsId/.well-known/jwks.json"
        awsKidStoreUrl = URI(url).toURL()
        provider = JwkProviderBuilder(awsKidStoreUrl).build()
    }

    override fun getPublicKeyById(keyId: String): RSAPublicKey {
        return try {
            provider[keyId].publicKey as RSAPublicKey
        } catch (e: JwkException) {
            throw RuntimeException("Failed to get JWT kid=$keyId from aws_kid_store_url=$awsKidStoreUrl")
        }
    }

    override fun getPrivateKey(): RSAPrivateKey? {
        return null
    }

    override fun getPrivateKeyId(): String? {
        return null
    }
}