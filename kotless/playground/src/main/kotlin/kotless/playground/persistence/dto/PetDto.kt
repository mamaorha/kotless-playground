package kotless.playground.persistence.dto

data class PetDto(
    val id: Long? = null,
    val name: String,
    val type: String,
    val breed: String,
    val age: Int,
    val updatedAt: Long,
    val createdAt: Long
)
