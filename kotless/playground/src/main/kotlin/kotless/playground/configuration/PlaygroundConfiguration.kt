package kotless.playground.configuration

import kotless.playground.persistence.dao.PetDao
import kotless.playground.persistence.mysql.MysqlPetDao
import kotless.utilities.dao.DaoConnectionProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
open class PlaygroundConfiguration {
    @Bean
    fun petDao(daoConnectionProvider: DaoConnectionProvider): PetDao {
        return MysqlPetDao(daoConnectionProvider = daoConnectionProvider)
    }
}