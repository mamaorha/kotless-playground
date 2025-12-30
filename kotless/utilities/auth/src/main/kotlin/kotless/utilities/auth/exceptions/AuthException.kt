package kotless.utilities.auth.exceptions

class AuthException(val statusCode: Int) : Exception("authentication failure")