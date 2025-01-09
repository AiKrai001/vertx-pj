package org.aikrai.vertx.context

import kotlin.annotation.AnnotationTarget
import kotlin.annotation.Target

@Target(allowedTargets = [AnnotationTarget.FUNCTION])
@Retention(AnnotationRetention.RUNTIME)
annotation class CustomizeRequest(
  val method: String
)

@Target(allowedTargets = [AnnotationTarget.FUNCTION])
@Retention(AnnotationRetention.RUNTIME)
annotation class CustomizeResponse
