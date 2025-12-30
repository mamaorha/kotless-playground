package kotless.utilities.auth.configuration

import kotless.utilities.auth.AuthWrapper
import kotless.utilities.auth.SecretManager
import kotless.utilities.auth.aws.AwsAuthWrapper
import kotless.utilities.auth.aws.AwsSecretManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
open class AuthConfiguration {
    @Bean
    @ConditionalOnMissingBean
    open fun authWrapper(secretManager: SecretManager): AuthWrapper {
        return AwsAuthWrapper.Companion.build(secretManager = secretManager)
    }

    @Bean
    @ConditionalOnMissingBean
    open fun secretManager(): SecretManager {
        return AwsSecretManager.Companion.awsSecretManager
    }
}