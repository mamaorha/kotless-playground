package kotless.playground

import io.kotless.dsl.spring.Kotless
import org.springframework.boot.autoconfigure.SpringBootApplication
import kotlin.reflect.KClass

@SpringBootApplication(scanBasePackages = ["kotless.playground", "kotless.utilities"], proxyBeanMethods = false)
open class Application : Kotless() {
    override val bootKlass: KClass<*> = this::class
}