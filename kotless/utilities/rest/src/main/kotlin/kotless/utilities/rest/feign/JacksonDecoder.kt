package kotless.utilities.rest.feign

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.RuntimeJsonMappingException
import feign.Response
import feign.Util
import feign.codec.Decoder
import java.io.BufferedReader
import java.io.IOException
import java.lang.reflect.Type

class JacksonDecoder(private val objectMapper: ObjectMapper) : Decoder {
    override fun decode(response: Response, type: Type): Any? {
        if (response.status() == 404 || response.status() == 204) return Util.emptyValueOf(type)
        if (response.body() == null) return null

        if (type.typeName == ByteArray::class.java.typeName) {
            return response.body().asInputStream().readAllBytes()
        }

        var reader = response.body().asReader(response.charset())
        if (!reader.markSupported()) {
            reader = BufferedReader(reader, 1)
        }
        try {
            // Read the first byte to see if we have any data
            reader.mark(1)
            if (reader.read() == -1) {
                return null // Eagerly returning null avoids "No content to map due to end-of-input"
            }
            reader.reset()

            if (type.typeName == String::class.java.typeName) {
                return reader.readText()
            }

            return objectMapper.readValue(reader, objectMapper.constructType(type))
        } catch (e: RuntimeJsonMappingException) {
            if (e.cause != null && e.cause is IOException) {
                throw IOException::class.java.cast(e.cause)
            }
            throw e
        }
    }
}