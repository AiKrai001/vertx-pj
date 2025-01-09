package org.aikrai.vertx.context

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Controller(val prefix: String = "")
