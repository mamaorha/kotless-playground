package kotless.utilities.auth.data

data class AuthContext(
    val authorization: String,
    val server: String?,
    val payload: AuthContextPayload
)



