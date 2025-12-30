package kotless.playground.persistence.mysql

import kotless.playground.persistence.dao.PetDao
import kotless.playground.persistence.dto.PetDto
import kotless.utilities.common.GenericUtils
import kotless.utilities.dao.DaoConnectionProvider
import java.sql.ResultSet

/*
    you need to create a table in mysql called "pets" see kotless/sql-scripts/pets-schema.sql
*/
class MysqlPetDao(
    private val daoConnectionProvider: DaoConnectionProvider
) : PetDao {
    companion object {
        private const val table = "`pets`"
        private const val idColumn = "`id`"
        private const val nameColumn = "`name`"
        private const val typeColumn = "`type`"
        private const val breedColumn = "`breed`"
        private const val ageColumn = "`age`"
        private const val updateTimeColumn = "`update_time`"
        private const val creationTimeColumn = "`creation_time`"

        private val allColumns = sequenceOf(
            idColumn, nameColumn, typeColumn, breedColumn, ageColumn,
            updateTimeColumn, creationTimeColumn
        )

        //Note im filtering idColumn out as its marked in the schema as "auto generated"
        private val insertQuery = """
            INSERT INTO $table SET ${
            allColumns.filterNot { it == idColumn }.map { column -> "$column = ?" }.joinToString(", ")
        }
        """.trimIndent()

        private val deleteQuery = """
            DELETE FROM $table WHERE $idColumn = ?
        """.trimIndent()

        private val getQuery = """
            SELECT ${allColumns.joinToString(", ")} FROM $table WHERE $idColumn = ?
        """.trimIndent()

        private val listQuery = """
            SELECT ${allColumns.joinToString(", ")} FROM $table WHERE $idColumn > ? ORDER BY $idColumn ASC LIMIT ?
        """.trimIndent()

        //update columns (we dont update id/creationTime) by id
        private val updateQuery = """
            UPDATE $table SET ${
            allColumns.filterNot { it == idColumn || it == creationTimeColumn }.map { column -> "$column = ?" }
                .joinToString(", ")
        } WHERE $idColumn = ?
        """.trimIndent()
    }

    override fun insert(petDto: PetDto): PetDto {
        val iterator = GenericUtils.iterator(1)

        return daoConnectionProvider.useConnection { connection ->
            connection.prepareStatement(insertQuery).use { preparedStatement ->
                preparedStatement.setString(iterator.next(), petDto.name)
                preparedStatement.setString(iterator.next(), petDto.type)
                preparedStatement.setString(iterator.next(), petDto.breed)
                preparedStatement.setInt(iterator.next(), petDto.age)
                preparedStatement.setLong(iterator.next(), petDto.updatedAt)
                preparedStatement.setLong(iterator.next(), petDto.createdAt)

                preparedStatement.executeUpdate()

                preparedStatement.generatedKeys.use { idRs ->
                    idRs.next()
                    petDto.copy(id = idRs.getLong(1))
                }
            }
        }
    }

    override fun delete(petId: Long): Boolean {
        val iterator = GenericUtils.iterator(1)

        return daoConnectionProvider.useConnection { connection ->
            connection.prepareStatement(deleteQuery)
                .use { preparedStatement ->
                    preparedStatement.setLong(iterator.next(), petId)

                    preparedStatement.executeUpdate() > 0
                }
        }
    }

    override fun get(petId: Long): PetDto? {
        val iterator = GenericUtils.iterator(1)

        return daoConnectionProvider.useConnection { connection ->
            connection.prepareStatement(getQuery)
                .use { preparedStatement ->
                    preparedStatement.setLong(iterator.next(), petId)

                    preparedStatement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            readPet(resultSet = resultSet)
                        } else {
                            null
                        }
                    }
                }
        }
    }

    override fun list(cursor: Long?, pageSize: Int): List<PetDto> {
        val iterator = GenericUtils.iterator(1)

        return daoConnectionProvider.useConnection { connection ->
            connection.prepareStatement(listQuery)
                .use { preparedStatement ->
                    preparedStatement.setLong(iterator.next(), cursor ?: -1)
                    preparedStatement.setInt(iterator.next(), pageSize)

                    val result = mutableListOf<PetDto>()

                    preparedStatement.executeQuery().use { resultSet ->
                        while (resultSet.next()) {
                            result.add(readPet(resultSet = resultSet))
                        }
                    }

                    result
                }
        }
    }

    override fun update(petDto: PetDto): PetDto? {
        val petId = requireNotNull(petDto.id) { "id is a mandatory field" }
        val iterator = GenericUtils.iterator(1)

        daoConnectionProvider.useConnection { connection ->
            connection.prepareStatement(updateQuery).use { preparedStatement ->
                preparedStatement.setString(iterator.next(), petDto.name)
                preparedStatement.setString(iterator.next(), petDto.type)
                preparedStatement.setString(iterator.next(), petDto.breed)
                preparedStatement.setInt(iterator.next(), petDto.age)
                preparedStatement.setLong(iterator.next(), petDto.updatedAt)
                preparedStatement.setLong(iterator.next(), petId)

                preparedStatement.executeUpdate()
            }
        }

        return get(petId = petId)
    }

    private fun readPet(resultSet: ResultSet): PetDto {
        return PetDto(
            id = resultSet.getLong(idColumn.replace("`", "")),
            name = resultSet.getString(nameColumn.replace("`", "")),
            type = resultSet.getString(typeColumn.replace("`", "")),
            breed = resultSet.getString(breedColumn.replace("`", "")),
            age = resultSet.getInt(ageColumn.replace("`", "")),
            updatedAt = resultSet.getLong(updateTimeColumn.replace("`", "")),
            createdAt = resultSet.getLong(creationTimeColumn.replace("`", ""))
        )
    }
}