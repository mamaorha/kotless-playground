package kotless.utilities.common.throttler

class ThrottlerException(val resource: String, message: String) : Exception(message)