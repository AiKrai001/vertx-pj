package org.aikrai.vertx.utlis

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.aikrai.vertx.jackson.JsonUtil

@JsonIgnoreProperties("localizedMessage", "suppressed", "stackTrace", "cause")
class Meta(
  val name: String,
  override val message: String = "Internal Server Error",
  val data: Any? = null
) : RuntimeException(message, null, true, false) {

  fun stackTraceToString(): String {
    return JsonUtil.toJsonStr(this)
  }

  companion object {
    fun error(name: String, message: String): Meta =
      Meta(name, message)

    fun unimplemented(message: String): Meta =
      Meta("Unimplemented", message)

    fun unauthorized(message: String): Meta =
      Meta("Unauthorized", message)

    fun timeout(message: String): Meta =
      Meta("Timeout", message)

    fun requireArgument(argument: String, message: String): Meta =
      Meta("RequiredArgument:$argument", message)

    fun invalidArgument(argument: String, message: String): Meta =
      Meta("InvalidArgument:$argument", message)

    fun notFound(argument: String, message: String): Meta =
      Meta("NotFound:$argument", message)

    fun badRequest(message: String): Meta =
      Meta("BadRequest", message)

    fun notSupported(message: String): Meta =
      Meta("NotSupported", message)

    fun forbidden(message: String): Meta =
      Meta("Forbidden", message)

    fun repository(name: String, message: String?): Meta =
      Meta("Repository:$name", message ?: "")
  }
}
