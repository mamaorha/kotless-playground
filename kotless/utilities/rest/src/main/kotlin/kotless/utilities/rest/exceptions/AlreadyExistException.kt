package kotless.utilities.rest.exceptions

import org.springframework.http.HttpStatus

class AlreadyExistException (private val type: String, private val identifier: String)  : RestException(error = "[$type] with identifier [$identifier] already exist", httpStatus = HttpStatus.CONFLICT)