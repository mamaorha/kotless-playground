package kotless.utilities.auth

import kotless.utilities.auth.data.AuthContext
import kotless.utilities.auth.data.AuthContextPayload
import kotless.utilities.auth.exceptions.AuthException
import kotless.utilities.common.Either

interface AuthWrapper {
    fun <T> withAuth(token: String, f: (AuthContext) -> T): Either<AuthException, T>
    fun <T> withServerAuth(token: String, f: (AuthContext) -> T): Either<AuthException, T>
    fun buildServerSignToken(server: String, payload: AuthContextPayload): String
}