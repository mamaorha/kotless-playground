package kotless.utilities.rest.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
open class RestConfigurator(@Autowired var objectMapper: ObjectMapper) {
    init {
        ObjectMapperConfigurator.registerModules(objectMapper)
    }
}