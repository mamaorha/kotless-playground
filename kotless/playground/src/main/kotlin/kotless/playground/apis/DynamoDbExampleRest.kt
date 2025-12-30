package kotless.playground.apis

import kotless.playground.api.DynamoDbApi
import kotless.playground.data.Example
import kotless.playground.extensions.DomainToResource.asResource
import kotless.playground.model.DynamoDBItem
import kotless.playground.model.DynamoDBItemUpdate
import kotless.playground.model.QueryItemsResponse
import kotless.playground.persistence.dto.ExampleDto
import kotless.playground.persistence.dynamo.DynamoExampleDao
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

@RestController
class DynamoDbExampleRest(
    private val authWrapper: AuthWrapper
) : DynamoDbApi {
    @RequestMapping(
        value = ["/dynamodb/items"],
        produces = ["application/json"],
        consumes = ["application/json"],
        method = [RequestMethod.POST]
    )
    override fun createItem(
        authorization: String,
        body: @Valid DynamoDBItem
    ): ResponseEntity<DynamoDBItem> {
        return authWrapper.withAuth(token = authorization) { authContext ->
            either {
                val exampleDto = DynamoExampleDao.upsert(
                    exampleDto = ExampleDto(
                        username = authContext.username,
                        id = body.id,
                        cas = null, //we want to make sure "insert"
                        data = Example(
                            name = body.name,
                            email = body.email,
                            createdAt = body.createdAt,
                            attributes = body.attributes
                        )
                    )
                ).bind()

                ResponseEntity.ok(exampleDto.asResource())
            }
        }.flatten().getOrThrow()
    }

    @RequestMapping(value = ["/dynamodb/items/id"], produces = ["application/json"], method = [RequestMethod.DELETE])
    override fun deleteItem(
        authorization: String,
        id: @NotNull @Valid String
    ): ResponseEntity<Void> {
        return authWrapper.withAuth(token = authorization) { authContext ->
            either {
                DynamoExampleDao.delete(username = authContext.username, id = id).bind()
                ResponseEntity.noContent().build<Void>()
            }
        }.flatten().getOrThrow()
    }

    @RequestMapping(value = ["/dynamodb/items/id"], produces = ["application/json"], method = [RequestMethod.GET])
    override fun getItem(
        authorization: String,
        id: @NotNull @Valid String
    ): ResponseEntity<DynamoDBItem> {
        return authWrapper.withAuth(token = authorization) { authContext ->
            either {
                val exampleDto =
                    DynamoExampleDao.get(username = authContext.username, id = id).asNotFound("example").bind()

                ResponseEntity.ok(exampleDto.asResource())
            }
        }.flatten().getOrThrow()
    }

    @RequestMapping(value = ["/dynamodb/items"], produces = ["application/json"], method = [RequestMethod.GET])
    override fun queryItems(
        authorization: String,
        limit: @Valid Int?
    ): ResponseEntity<QueryItemsResponse> {
        return authWrapper.withAuth(token = authorization) { authContext ->
            val exampleDtoList = DynamoExampleDao.query(username = authContext.username, limit = limit)

            ResponseEntity.ok(QueryItemsResponse().items(exampleDtoList.map { it.asResource() }))
        }.getOrThrow()
    }

    @RequestMapping(
        value = ["/dynamodb/items/id"],
        produces = ["application/json"],
        consumes = ["application/json"],
        method = [RequestMethod.PUT]
    )
    override fun updateItem(
        authorization: String,
        id: @NotNull @Valid String,
        body: @Valid DynamoDBItemUpdate
    ): ResponseEntity<DynamoDBItem> {
        return authWrapper.withAuth(token = authorization) { authContext ->
            either {
                val exampleDto = DynamoExampleDao.upsert(
                    username = authContext.username,
                    id = id
                ) { currDto ->
                    currDto?.copy(
                        data = currDto.data.copy(
                            name = body.name,
                            email = body.email,
                            attributes = body.attributes
                        )
                    )
                }.bind().second.asNotFound("example").bind()

                ResponseEntity.ok(exampleDto.asResource())
            }
        }.flatten().getOrThrow()
    }
}