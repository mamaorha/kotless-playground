# Kotless Playground
this is a showcase on how to use ["kotless"](https://github.com/mamaorha/Kotless) to the fullest :)

* if you wish to "copy-paste" things i suggest to look for "CHANGE_ME" in the project and plug in your specific values according to your needs.
* this project uses the default "Aws" implementations for things like "Storage/Authentication/etc" but you can override it with your own implementation using Bean override, for example under "PlaygroundConfiguration" you can add 
```kotlin
@Bean
open fun authWrapper(): AuthWrapper {
   return object : AuthWrapper() {
      //Your implementation
      override fun getJwtVerifier(): JWTVerifier {
         TODO("Not yet implemented")
      }

      override fun getServerSignAlgorithm(): Algorithm {
         TODO("Not yet implemented")
      }

      override fun getServerSignJwtVerifier(): JWTVerifier {
         TODO("Not yet implemented")
      }

      override fun getUserAttributes(username: String): Map<String, String> {
         TODO("Not yet implemented")
      }
   }
}
```

## before deployment
1. don't use "path" param, instead use "query" (api gateway limitation)
2. names should be short as we later deploy using: module-package-class-api
3. buy a domain, for example: https://domains.livedns.co.il
4. add the domain to route53 in Amazon under "hosted zones"
5. take the NS from amazon (prev step) and go to your domain management (where you bought it from) and put the NS there
6. go to "certificate management" in aws and add the domain under "us-east-1" (must be this region), now press on "create records in route53"
7. if you are facing "cors" issues make sure you use "kotless.webapp.cors.enabled" in your "build.gradle.kts"

## first deployment
1. create a bucket in s3, bucket name appears in the "build.gradle.kts" under "kotless.config.aws.storage.bucket"
2. certificate manager - request a new certificate for the domain (set under "dns" alias.zone)
   A. note that our certificates sits under "us-east-1" region
   B. Create DNS records in Amazon Route 53 (there is a button in the ui)

## ongoing deployments
1. sometimes kotless-bin is not showing under "build" due to caching, if missing copy it from 1 of the other kotless builds or run "./gradlew :kotless:<REPLACE_WITH_MODULE>:download_terraform --rerun-tasks"

## GraalVM deployments
1. it is recommended to first deploy without graalVM and see that everything works, once you are satisfied enable graalVM on the build.gradle file
2. when graalVM is enabled there is a "local-agent" option in the gradle menu under "kotless", this will run the project locally while attaching graalVM agent that will auto generate metadata for you.
run the local-agent and do as many operations on the local server as you can to simulate "real behavior" you expect later, once you are satisfied type "exit" and press the "return key" to let the process exit properly and generate the relevant files under: "build/graal-agent", you should copy those files and put them under "src/main/resources/META-INF/native-image/REPLCE_ME_WITH_YOUR_FULL_PACKAGE_NAME/"
3. you need java build with graal + docker for desktop for deployments
4. common pitful -> if you have failures with library "load" you might need to adjust "jni-config" and add the following to the json array
```
{
    "name":"java.lang.System",
    "methods":[{"name":"load","parameterTypes":["java.lang.String"] }]
}
```