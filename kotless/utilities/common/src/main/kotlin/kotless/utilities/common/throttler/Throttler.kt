package kotless.utilities.common.throttler

import kotless.utilities.common.Either

interface Throttler {
    fun <T> throttle(key: String, resource: String, f: () -> T): Either<ThrottlerException, T>
}
