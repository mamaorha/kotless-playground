package kotless.utilities.rest.exceptions

import org.springframework.http.HttpStatusCode

open class RestException(val error: String, val httpStatus: HttpStatusCode) : Exception(error)