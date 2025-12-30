package kotless.utilities.cache.configuration

import kotless.utilities.cache.dynamoDB.DynamoThrottler
import kotless.utilities.common.throttler.Throttler
import kotless.utilities.common.throttler.ThrottlerBuilder
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
open class CacheConfiguration {

    @Bean
    @ConditionalOnMissingBean
    open fun throttlerBuilder(): ThrottlerBuilder {
        return object : ThrottlerBuilder {
            override fun build(ttlInSeconds: Long, maxRequests: Int): Throttler {
                return DynamoThrottler(ttlInSeconds = ttlInSeconds, maxRequests = maxRequests)
            }
        }
    }
}