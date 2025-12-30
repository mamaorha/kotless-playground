package kotless.playground.apis

import kotless.playground.api.S3Api
import kotless.playground.core.MyBucket
import kotless.playground.model.FileUrlResponse
import kotless.utilities.auth.AuthWrapper
import kotless.utilities.common.either
import kotless.utilities.common.flatten
import kotless.utilities.rest.validations.FieldValidations.asNotFound
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController
import javax.validation.Valid
import javax.validation.constraints.NotNull
import kotlin.time.Duration.Companion.minutes

@RestController
class S3ExampleRest(
    private val authWrapper: AuthWrapper
) : S3Api {
    @RequestMapping(value = ["/s3/files/path"], produces = ["application/json"], method = [RequestMethod.DELETE])
    override fun deleteFile(
        authorization: String,
        path: @NotNull @Valid String
    ): ResponseEntity<Void> {
        return authWrapper.withAuth(token = authorization) { authContext ->
            MyBucket.deleteFile(path = path)
            ResponseEntity.noContent().build<Void>()
        }.getOrThrow()
    }

    @RequestMapping(value = ["/s3/files"], produces = ["application/json"], method = [RequestMethod.GET])
    override fun getFileUrl(
        authorization: String,
        path: @NotNull @Valid String,
        expirationMinutes: @Valid Int?
    ): ResponseEntity<FileUrlResponse> {
        return authWrapper.withAuth(token = authorization) { authContext ->
            either {
                val expirationInMillis = (expirationMinutes ?: 5).minutes.inWholeMilliseconds
                val url = MyBucket.getFileUrlIfExists(
                    path = path,
                    expirationInMillis = expirationInMillis
                ).asNotFound("path").bind()

                ResponseEntity.ok(FileUrlResponse().path(path).url(url.toString()))
            }
        }.flatten().getOrThrow()
    }

    @RequestMapping(
        value = ["/s3/files"],
        produces = ["application/json"],
        consumes = ["application/octet-stream"],
        method = [RequestMethod.POST]
    )
    override fun uploadFile(
        authorization: String,
        path: @NotNull @Valid String,
        body: ByteArray
    ): ResponseEntity<Void> {
        return authWrapper.withAuth(token = authorization) { authContext ->
            MyBucket.uploadFile(path = path, byteArray = body)
            ResponseEntity.noContent().build<Void>()
        }.getOrThrow()
    }
}