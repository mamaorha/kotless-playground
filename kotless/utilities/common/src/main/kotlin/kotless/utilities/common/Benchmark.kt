package kotless.utilities.common

import org.slf4j.LoggerFactory

object Benchmark {
    private val logger = LoggerFactory.getLogger(Benchmark::class.java)

    fun <T> logTime(message: String, f: () -> T): T {
        val start = System.currentTimeMillis()

        try {
            return f()
        } finally {
            logger.info("$message runtime: ${System.currentTimeMillis() - start}ms")
        }
    }
}