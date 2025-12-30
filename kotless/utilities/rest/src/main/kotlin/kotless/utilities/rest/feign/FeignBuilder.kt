package kotless.utilities.rest.feign

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import feign.Feign
import feign.spring.SpringContract

object FeignBuilder {
    private val objectMapper = ObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .configure(SerializationFeature.INDENT_OUTPUT, true)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val contract = SpringContract()
    private val jacksonEncoder = JacksonEncoder(objectMapper)
    private val jacksonDecoder = JacksonDecoder(objectMapper)
    private val responseEntityDecoder = ResponseEntityDecoder(jacksonDecoder)

    fun <T> build(target: Class<T>, url: String): T {
        return Feign.builder().contract(contract).encoder(jacksonEncoder)
            .decoder(responseEntityDecoder)
            .target(target, url)
    }
}