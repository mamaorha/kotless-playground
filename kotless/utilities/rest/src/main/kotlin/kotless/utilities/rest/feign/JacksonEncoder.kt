package kotless.utilities.rest.feign

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import feign.RequestTemplate
import feign.Util
import feign.codec.EncodeException
import feign.codec.Encoder
import java.lang.reflect.Type


class JacksonEncoder(private val objectMapper: ObjectMapper) : Encoder {
    override fun encode(value: Any?, bodyType: Type, template: RequestTemplate) {
        try {
            val javaType = objectMapper.typeFactory.constructType(bodyType)
            template.body(objectMapper.writerFor(javaType).writeValueAsBytes(value), Util.UTF_8)
        } catch (e: JsonProcessingException) {
            throw EncodeException(e.message, e)
        }
    }
}