package org.aikrai.vertx.utlis

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.aikrai.vertx.jackson.JsonUtil

@JsonIgnoreProperties("localizedMessage", "suppressed", "stackTrace", "cause")
class Meta(
  val name: String,
  override val message: String = "",
  val data: Any? = null
) : RuntimeException(message, null, false, false) {

  fun stackTraceToString(): String {
    return JsonUtil.toJsonStr(this)
  }

  companion object {
    fun failure(name: String, message: String): Meta =
      Meta(name, message)

    fun unimplemented(message: String): Meta =
      Meta("unimplemented", message)

    fun unauthorized(message: String): Meta =
      Meta("unauthorized", message)

    fun timeout(message: String): Meta =
      Meta("timeout", message)

    fun requireArgument(argument: String, message: String): Meta =
      Meta("required_argument:$argument", message)

    fun invalidArgument(argument: String, message: String): Meta =
      Meta("invalid_argument:$argument", message)

    fun notFound(argument: String, message: String): Meta =
      Meta("not_found:$argument", message)

    fun badRequest(message: String): Meta =
      Meta("bad_request", message)

    fun notSupported(message: String): Meta =
      Meta("not_supported", message)

    fun forbidden(message: String): Meta =
      Meta("forbidden", message)
  }
}
