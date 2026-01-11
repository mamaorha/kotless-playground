package kotless.utilities.auth.utils

import com.auth0.jwk.JwkException
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.interfaces.RSAKeyProvider
import java.net.URI
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

object RsaKeyProviderBuilder {
    fun build(kidStoreUrl: String): RSAKeyProvider {
        val provider = JwkProviderBuilder(URI(kidStoreUrl).toURL()).build()

        return object : RSAKeyProvider {
            override fun getPublicKeyById(keyId: String?): RSAPublicKey? {
                return try {
                    provider[keyId].publicKey as RSAPublicKey
                } catch (e: JwkException) {
                    throw RuntimeException("Failed to get JWT kid=$keyId from kid_store_url=$kidStoreUrl")
                }
            }

            override fun getPrivateKey(): RSAPrivateKey? {
                return null
            }

            override fun getPrivateKeyId(): String? {
                return null
            }
        }
    }
}