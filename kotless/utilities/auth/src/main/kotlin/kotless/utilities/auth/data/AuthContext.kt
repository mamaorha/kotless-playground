package kotless.utilities.auth.data

data class AuthContext(
    val authorization: String,
    val server: String?,
    val username: String,
    val email: String,
    //CHANGE_ME -> add here more fields as you see fits to your scenario
)



