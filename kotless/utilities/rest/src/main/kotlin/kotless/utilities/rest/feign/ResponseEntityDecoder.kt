package kotless.utilities.rest.feign

import feign.Response
import feign.codec.Decoder
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.*


class ResponseEntityDecoder(private val decoder: Decoder) : Decoder {
    override fun decode(response: Response, type: Type): Any {
        return if (isParameterizeHttpEntity(type)) {
            val actualType = (type as ParameterizedType).actualTypeArguments[0]
            val decodedObject: Any = decoder.decode(response, actualType)

            createResponse(decodedObject, response)
        } else if (isHttpEntity(type)) {
            createResponse(null, response)
        } else {
            decoder.decode(response, type)
        }
    }

    private fun isParameterizeHttpEntity(type: Type): Boolean {
        return if (type is ParameterizedType) {
            isHttpEntity(type.rawType)
        } else false
    }

    private fun isHttpEntity(type: Type): Boolean {
        return if (type is Class<*>) {
            HttpEntity::class.java.isAssignableFrom(type)
        } else false
    }

    private fun createResponse(instance: Any?, response: Response): ResponseEntity<Any> {
        val headers = HttpHeaders()

        for (key in response.headers().keys) {
            headers[key] = response.headers()[key]?.let { LinkedList(it) }
        }
        return ResponseEntity(instance, headers, HttpStatus.valueOf(response.status()))
    }
}