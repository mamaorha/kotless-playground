package kotless.utilities.rest.validations

import kotless.utilities.common.Either
import kotless.utilities.rest.exceptions.BadRequestException
import kotless.utilities.rest.exceptions.MissingMandatoryFieldException
import kotless.utilities.rest.exceptions.NotFoundException
import kotless.utilities.rest.exceptions.RestException

object FieldValidations {
    fun <T> T?.asMandatory(field: String): Either<MissingMandatoryFieldException, T> {
        return if (this == null) {
            Either.Left(MissingMandatoryFieldException(field = field))
        } else {
            Either.Right(this)
        }
    }

    fun String?.validate(field: String, minLength: Int, maxLength: Int): Either<RestException, String> {
        return if (this == null) {
            Either.Left(MissingMandatoryFieldException(field = field))
        } else if (this.length < minLength || this.length > maxLength) {
            Either.Left(BadRequestException(error = "field [$field] length must be between $minLength and $maxLength, actual: ${this.length}"))
        } else {
            Either.Right(this)
        }
    }

    fun Int?.validate(field: String, min: Int, max: Int): Either<RestException, Int> {
        return if (this == null) {
            Either.Left(MissingMandatoryFieldException(field = field))
        } else if (this < min || this > max) {
            Either.Left(BadRequestException(error = "field [$field] must be between $min and $max, actual: ${this}"))
        } else {
            Either.Right(this)
        }
    }

    fun <T> T?.asNotFound(resource: String): Either<NotFoundException, T> {
        return if (this == null) {
            Either.Left(NotFoundException(resource = resource))
        } else {
            Either.Right(this)
        }
    }
}