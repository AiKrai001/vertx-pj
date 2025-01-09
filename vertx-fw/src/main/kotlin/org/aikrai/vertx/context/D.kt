package org.aikrai.vertx.context

import kotlin.annotation.AnnotationTarget
import kotlin.annotation.Target

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class D(
  val name: String = "",
  val caption: String = ""
)
