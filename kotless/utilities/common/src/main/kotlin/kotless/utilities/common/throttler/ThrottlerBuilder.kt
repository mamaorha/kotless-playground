package kotless.utilities.common.throttler

interface ThrottlerBuilder {
    fun build(ttlInSeconds: Long, maxRequests: Int): Throttler
}