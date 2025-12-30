@file:JvmMultifileClass
@file:JvmName("RaiseKt")
@file:OptIn(ExperimentalContracts::class, ExperimentalTypeInference::class)

package kotless.utilities.common

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.experimental.ExperimentalTypeInference

interface Raise<in Error> {
    fun <B> Either<Error, B>.bind(): B = when (this) {
        is Either.Right -> value
        is Either.Left -> throw RaisedException(getException())
    }
}

class DefaultRaise : Raise<Any?>

class RaisedException(val value: Any?) : Exception()

inline fun <Error, A> either(@BuilderInference block: Raise<Error>.() -> A): Either<Error, A> {
    return fold({ block.invoke(this) }, { Either.Left(it) }, { Either.Right(it) })
}

@OptIn(ExperimentalContracts::class)
inline fun <Error, A, B> fold(
    @BuilderInference block: Raise<Error>.() -> A,
    recover: (error: Error) -> B,
    transform: (value: A) -> B,
): B {
    contract {
        callsInPlace(recover, InvocationKind.AT_MOST_ONCE)
        callsInPlace(transform, InvocationKind.AT_MOST_ONCE)
    }

    val raise = DefaultRaise()

    return try {
        val res = block(raise)
        transform(res)
    } catch (e: RaisedException) {
        recover(e.value as Error)
    } catch (e: Throwable) {
        throw e
    }
}