package kotless.utilities.dao.configuration

import kotless.utilities.auth.SecretManager
import kotless.utilities.dao.DaoConnectionProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
open class DaoProviderConfiguration {
    @Bean
    @ConditionalOnMissingBean
    open fun daoConnectionProvider(secretManager: SecretManager): DaoConnectionProvider {
        return DaoConnectionProvider(secretManager = secretManager)
    }
}