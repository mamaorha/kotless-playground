package kotless.playground.extensions

import kotless.playground.model.DynamoDBItem
import kotless.playground.model.Pet
import kotless.playground.persistence.dto.ExampleDto
import kotless.playground.persistence.dto.PetDto

object DomainToResource {
    fun ExampleDto.asResource(): DynamoDBItem {
        return DynamoDBItem()
            .id(id)
            .name(data.name)
            .email(data.email)
            .createdAt(data.createdAt)
            .attributes(data.attributes)
    }

    fun PetDto.asResource(): Pet {
        return Pet()
            .id(id)
            .name(name)
            .type(type)
            .breed(breed)
            .age(age)
            .createdAt(createdAt)
            .updatedAt(updatedAt)
    }
}