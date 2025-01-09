package org.aikrai.vertx.auth

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class AllowAnonymous

@Target(AnnotationTarget.TYPE, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class CheckPermission(
  val type: String = "",
  val value: Array<String> = [],
  val mode: Mode = Mode.AND,
  val orRole: Array<String> = []
)

@Target(AnnotationTarget.TYPE, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class CheckRole(
  val type: String = "",
  val value: Array<String> = [],
  val mode: Mode = Mode.AND
)

enum class Mode {
  AND,
  OR
}
