package kotless.playground.persistence.dto

import kotless.playground.data.Example

data class ExampleDto(
    val username: String,
    val id: String,
    val cas: Long?,
    val data: Example
)
