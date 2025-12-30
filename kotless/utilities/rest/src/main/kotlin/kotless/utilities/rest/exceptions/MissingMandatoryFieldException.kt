package kotless.utilities.rest.exceptions

import org.springframework.http.HttpStatus

class MissingMandatoryFieldException(val field: String) :
    RestException(error = "Missing mandatory field: [$field]", httpStatus = HttpStatus.BAD_REQUEST)
