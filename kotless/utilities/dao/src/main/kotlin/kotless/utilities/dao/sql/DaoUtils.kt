package kotless.utilities.dao.sql

import java.sql.ResultSet

object DaoUtils {
    fun <T> getNullable(rs: ResultSet, f: (ResultSet) -> T): T? {
        val value = f(rs)

        return if (rs.wasNull()) null
        else value
    }
}