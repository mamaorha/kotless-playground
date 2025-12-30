package kotless.utilities.rest.exceptions

import org.springframework.http.HttpStatus

class NotFoundException(val resource: String) :
    RestException(error = "Resource: [$resource] couldn't be found", httpStatus = HttpStatus.NOT_FOUND)
