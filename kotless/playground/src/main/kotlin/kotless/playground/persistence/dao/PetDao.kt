package kotless.playground.persistence.dao

import kotless.playground.persistence.dto.PetDto

interface PetDao {
    fun insert(petDto: PetDto): PetDto
    fun delete(petId: Long): Boolean
    fun get(petId: Long): PetDto?
    fun list(cursor: Long?, pageSize: Int): List<PetDto>
    fun update(petDto: PetDto): PetDto?
}