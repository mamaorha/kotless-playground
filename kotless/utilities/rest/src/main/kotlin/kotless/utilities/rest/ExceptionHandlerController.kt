package kotless.utilities.rest

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import kotless.utilities.auth.exceptions.AuthException
import kotless.utilities.common.throttler.ThrottlerException
import kotless.utilities.rest.exceptions.RestException
import kotless.utilities.rest.exceptions.TooManyRequestsException

@ControllerAdvice
class ExceptionHandlerController {
    companion object {
        private val logger = LoggerFactory.getLogger(ExceptionHandlerController::class.java)
    }

    @ExceptionHandler(Throwable::class)
    fun handler(request: HttpServletRequest, throwable: Throwable): ResponseEntity<RestError> {
        logger.error(throwable.message, throwable)

        return when (throwable) {
            is RestException -> RestError(code = throwable.httpStatus.value(), error = throwable.error).asResponseEntity()
            is AuthException -> RestError(code = throwable.statusCode, error = throwable.message).asResponseEntity()
            is ThrottlerException -> TooManyRequestsException(resource = throwable.resource).let {
                RestError(code = it.httpStatus.value(), error = it.error).asResponseEntity()
            }

            is ServletException -> RestError(code = HttpStatus.BAD_REQUEST.value(), error = throwable.message).asResponseEntity()
            is HttpMessageNotReadableException -> {
                val cause = throwable.cause

                val message = when (cause) {

                    is JsonParseException -> {
                        "Malformed JSON at line ${cause.location.lineNr}, column ${cause.location.columnNr}."
                    }

                    is InvalidFormatException -> {
                        val field = cause.path.joinToString(".") { it.fieldName ?: "[index]" }
                        val expected = cause.targetType.simpleName
                        val value = cause.value
                        "Invalid value '$value' for field '$field'. Expected type: $expected."
                    }

                    is MismatchedInputException -> {
                        val field = cause.path.joinToString(".") { it.fieldName ?: "[index]" }
                        val expected = cause.targetType?.simpleName ?: "correct type"
                        "Incorrect type for field '$field'. Expected: $expected."
                    }

                    else -> {
                        "Invalid or unreadable request body."
                    }
                }

                RestError(code = HttpStatus.BAD_REQUEST.value(), error = message).asResponseEntity()
            }

            else -> RestError(code = HttpStatus.INTERNAL_SERVER_ERROR.value(), error = "internal error").asResponseEntity()
        }
    }

    data class RestError(
        val code: Int,
        val error: String?,
    )

    fun RestError.asResponseEntity(): ResponseEntity<RestError> {
        return ResponseEntity(this, HttpStatus.valueOf(code))
    }
}