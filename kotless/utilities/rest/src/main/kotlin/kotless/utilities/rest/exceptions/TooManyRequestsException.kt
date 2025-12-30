package kotless.utilities.rest.exceptions

import org.springframework.http.HttpStatus

class TooManyRequestsException (val resource: String)  : RestException(error = "Resource [$resource] is exhausted, you made too many requests", httpStatus = HttpStatus.TOO_MANY_REQUESTS)