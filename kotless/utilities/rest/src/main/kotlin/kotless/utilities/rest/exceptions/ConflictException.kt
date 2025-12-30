package kotless.utilities.rest.exceptions

import org.springframework.http.HttpStatus

class ConflictException(error: String) :
    RestException(error = error, httpStatus = HttpStatus.CONFLICT)
