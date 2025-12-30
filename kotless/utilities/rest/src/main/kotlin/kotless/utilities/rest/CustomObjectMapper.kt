package kotless.utilities.rest

import kotless.utilities.rest.configuration.ObjectMapperConfigurator
import com.fasterxml.jackson.databind.ObjectMapper

object CustomObjectMapper {
    val objectMapper by lazy { ObjectMapperConfigurator.registerModules(ObjectMapper()) }
}