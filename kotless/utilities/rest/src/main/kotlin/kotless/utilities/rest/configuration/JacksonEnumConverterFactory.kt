package kotless.utilities.rest.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.core.convert.converter.Converter
import org.springframework.core.convert.converter.ConverterFactory
import org.springframework.stereotype.Component

@Component
class JacksonEnumConverterFactory(
    private val objectMapper: ObjectMapper
) : ConverterFactory<String, Enum<*>> {

    override fun <T : Enum<*>> getConverter(targetType: Class<T>): Converter<String, T> {
        return Converter { source ->
            try {
                objectMapper.readValue("\"$source\"", targetType)
            } catch (e: Exception) {
                throw IllegalArgumentException("Cannot convert $source to ${targetType.simpleName}", e)
            }
        }
    }
}