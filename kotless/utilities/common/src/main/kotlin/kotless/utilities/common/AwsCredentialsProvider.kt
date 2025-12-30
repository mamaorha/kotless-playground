package kotless.utilities.common

import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider

object AwsCredentialsProvider {
    val credentialsProvider by lazy {
        Benchmark.logTime("building AwsCredentialsProvider") {
            if (System.getenv()["_HANDLER"]?.isNotEmpty() == true) {
                EnvironmentVariableCredentialsProvider.create()
            } else {
                ProfileCredentialsProvider.create()
            }
        }
    }
}