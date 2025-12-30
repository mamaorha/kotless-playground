package kotless.utilities.mail.configuration

import kotless.utilities.auth.SecretManager
import kotless.utilities.mail.Mailer
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
open class MailConfiguration {
    @Bean
    @ConditionalOnMissingBean(name = ["noReply"])
    open fun noReply(secretManager: SecretManager): Mailer {
        //CHANG_ME create the following keys in "secret-manager"
        return Mailer(
            username = secretManager.getSecret("NO_REPLY_USERNAME"),
            password = secretManager.getSecret("NO_REPLY_PASSWORD")
        )
    }
}