package kotless.utilities.rest.exceptions

import org.springframework.http.HttpStatus

class PermissionDeniedException(error: String) : RestException(error = error, httpStatus = HttpStatus.FORBIDDEN)