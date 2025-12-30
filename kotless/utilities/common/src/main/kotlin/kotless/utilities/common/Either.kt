package kotless.utilities.common

sealed class Either<out A, out B> {
    fun <C> map(f: (B) -> C): Either<A, C> {
        return when (this) {
            is Left -> this
            is Right -> Right(f(value))
        }
    }

    fun <C> mapLeft(f: (A) -> C): Either<C, B> {
        return when (this) {
            is Left -> Left(f(value))
            is Right -> this
        }
    }

    fun isRight(): Boolean {
        return this is Right
    }

    fun isLeft(): Boolean {
        return this is Left
    }

    fun getOrNull(): B? {
        return when (this) {
            is Left -> null
            is Right -> value
        }
    }

    fun getOrThrow(): B {
        return when (this) {
            is Left -> throw getException()
            is Right -> value
        }
    }

    fun getOrDefault(f: (A) -> @UnsafeVariance B): B {
        return when (this) {
            is Left -> f(value)
            is Right -> value
        }
    }

    fun mapAsUnit(): Either<A, Unit> {
        return when (this) {
            is Left -> this
            is Right -> Right(Unit)
        }
    }

    fun <A1, B1> fold(onLeft: (A) -> Either<A1, B1>, onRight: (B) -> Either<A1, B1>): Either<A1, B1> {
        return when (this) {
            is Left -> onLeft(value)
            is Right -> onRight(value)
        }
    }

    fun recover(f: (A) -> Either<@UnsafeVariance A, @UnsafeVariance B>): Either<A, B> {
        return when (this) {
            is Left -> f(value)
            is Right -> this
        }
    }

    class Left<out E>(val value: E) : Either<E, Nothing>() {
        fun getException(): Throwable {
            return when (value) {
                is Throwable -> value
                else -> RuntimeException("Left value is populated but its not an exception, $value")
            }
        }
    }

    class Right<out V>(val value: V) : Either<Nothing, V>()
}

inline fun <A, B, C> Either<A, B>.flatMap(f: (right: B) -> Either<A, C>): Either<A, C> {
    return when (this) {
        is Either.Right -> f(this.value)
        is Either.Left -> this
    }
}

fun <A, B> Either<A, Either<A, B>>.flatten(): Either<A, B> =
    flatMap { it }

fun <B> Result<B>.asEither(): Either<Exception, B> {
    return this.fold(
        onSuccess = { Either.Right(it) },
        onFailure = {
            when (it) {
                is Exception -> Either.Left(it)
                else -> throw it
            }
        }
    )
}