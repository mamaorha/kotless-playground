package kotless.utilities.auth

interface SecretManager {
    fun getSecret(secretId: String): String

    fun createSecret(name: String, description: String, secret: String)
}