package kotless.utilities.rest.exceptions

import org.springframework.http.HttpStatus

class BadRequestException(error: String) : RestException(error = error, httpStatus = HttpStatus.BAD_REQUEST)