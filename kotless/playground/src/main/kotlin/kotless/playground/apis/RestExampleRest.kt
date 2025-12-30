package kotless.playground.apis

import kotless.playground.api.RestApi
import kotless.playground.extensions.DomainToResource.asResource
import kotless.playground.model.Pet
import kotless.playground.model.PetCreateRequest
import kotless.playground.model.PetListResponse
import kotless.playground.model.PetUpdateRequest
import kotless.playground.persistence.dao.PetDao
import kotless.playground.persistence.dto.PetDto
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
class RestExampleRest(
    private val authWrapper: AuthWrapper,
    private val petDao: PetDao
) : RestApi {
    //Please note that the entire pet example is not using the "authContext.username" so everybody share pets

    @RequestMapping(
        value = ["/rest/pets"],
        produces = ["application/json"],
        consumes = ["application/json"],
        method = [RequestMethod.POST]
    )
    override fun createPet(
        authorization: String,
        body: @Valid PetCreateRequest
    ): ResponseEntity<Pet> {
        return authWrapper.withAuth(token = authorization) { authContext ->
            val now = System.currentTimeMillis()

            val petDto = petDao.insert(
                petDto = PetDto(
                    name = body.name,
                    type = body.type,
                    breed = body.breed,
                    age = body.age,
                    createdAt = now,
                    updatedAt = now
                )
            )

            ResponseEntity.ok(petDto.asResource())
        }.getOrThrow()
    }

    @RequestMapping(value = ["/rest/pets/petId"], produces = ["application/json"], method = [RequestMethod.DELETE])
    override fun deletePet(
        authorization: String,
        petId: @NotNull @Valid Long
    ): ResponseEntity<Void> {
        return authWrapper.withAuth(token = authorization) { authContext ->
            petDao.delete(petId = petId)

            ResponseEntity.noContent().build<Void>()
        }.getOrThrow()
    }

    @RequestMapping(value = ["/rest/pets/petId"], produces = ["application/json"], method = [RequestMethod.GET])
    override fun getPet(
        authorization: String,
        petId: @NotNull @Valid Long
    ): ResponseEntity<Pet> {
        return authWrapper.withAuth(token = authorization) { authContext ->
            either {
                val petDto = petDao.get(petId = petId).asNotFound("pet").bind()

                ResponseEntity.ok(petDto.asResource())
            }
        }.flatten().getOrThrow()
    }

    @RequestMapping(value = ["/rest/pets"], produces = ["application/json"], method = [RequestMethod.GET])
    override fun listPets(
        authorization: String,
        cursor: @Valid Long?,
        pageSize: @Valid Int?
    ): ResponseEntity<PetListResponse> {
        return authWrapper.withAuth(token = authorization) { authContext ->
            val actualPageSize = pageSize ?: 50

            val petDtoList = petDao.list(cursor = cursor, pageSize = actualPageSize)
            val nextCursor = petDtoList.mapNotNull { it.id }.maxOrNull() ?: -1

            val response = PetListResponse()
                .pets(petDtoList.map { it.asResource() })
                .pageSize(actualPageSize)
                .nextCursor(nextCursor)

            ResponseEntity.ok(response)
        }.getOrThrow()
    }

    @RequestMapping(
        value = ["/rest/pets/petId"],
        produces = ["application/json"],
        consumes = ["application/json"],
        method = [RequestMethod.PUT]
    )
    override fun updatePet(
        authorization: String,
        petId: @NotNull @Valid Long,
        body: @Valid PetUpdateRequest
    ): ResponseEntity<Pet> {
        return authWrapper.withAuth(token = authorization) { authContext ->
            either {
                val petDto = petDao.update(
                    petDto = PetDto(
                        id = petId,
                        name = body.name,
                        type = body.type,
                        breed = body.breed,
                        age = body.age,
                        updatedAt = System.currentTimeMillis(),
                        createdAt = 0L //ignored
                    )
                ).asNotFound("pet").bind()

                ResponseEntity.ok(petDto.asResource())
            }
        }.flatten().getOrThrow()
    }

}