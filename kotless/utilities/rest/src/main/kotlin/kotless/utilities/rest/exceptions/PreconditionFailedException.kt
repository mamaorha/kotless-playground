package kotless.utilities.rest.exceptions

import org.springframework.http.HttpStatus

class PreconditionFailedException(error: String) :
    RestException(error = error, httpStatus = HttpStatus.PRECONDITION_FAILED)
