package kotless.utilities.rest.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule

object ObjectMapperConfigurator {
    fun registerModules(objectMapper: ObjectMapper): ObjectMapper {
        return objectMapper.registerModule(KotlinModule.Builder().build())
    }
}