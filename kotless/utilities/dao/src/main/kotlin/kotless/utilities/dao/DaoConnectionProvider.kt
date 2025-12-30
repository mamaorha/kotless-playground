package kotless.utilities.dao

import kotless.utilities.auth.SecretManager
import java.sql.Connection
import java.sql.DriverManager

class DaoConnectionProvider(private val secretManager: SecretManager) {
    //CHANG_ME create the following keys in "secret-manager"
    val url by lazy { System.getenv()["MYSQL_HOST"] ?: secretManager.getSecret("AURORA_HOST") }
    val user by lazy { System.getenv()["MYSQL_USER"] ?: secretManager.getSecret("AURORA_USER") }
    val password by lazy { System.getenv()["MYSQL_PASS"] ?: secretManager.getSecret("AURORA_PASS") }

    fun <T> useConnection(f: (Connection) -> T): T {
        return buildConnection().use(f)
    }

    private fun buildConnection(): Connection {
        return DriverManager.getConnection(url, user, password).apply { this.autoCommit = true }
    }
}