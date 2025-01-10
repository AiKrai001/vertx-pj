package app.config.auth

import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.AuthenticationHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.aikrai.vertx.utlis.Meta

class JwtAuthenticationHandler(
  private val coroutineScope: CoroutineScope,
  private val authHandler: AuthHandler,
  private val context: String,
) : AuthenticationHandler {

  var exclude = mutableListOf(
    "/auth/**",
  )

  override fun handle(event: RoutingContext) {
    val path = event.request().path().replace("$context/", "/").replace("//", "/")
    if (isPathExcluded(path, exclude)) {
      event.next()
      return
    }

    val authorization = event.request().getHeader("Authorization") ?: null
    if (authorization == null || !authorization.startsWith("token ")) {
      event.fail(401, Meta.unauthorized("无效Token"))
      return
    }

    val token = authorization.substring(6)

    coroutineScope.launch {
      val authUser = authHandler.handle(token)
      if (authUser != null) {
        event.setUser(authUser)
        event.next()
      } else {
        event.fail(401, Meta.unauthorized("token"))
      }
    }
  }

  private fun isPathExcluded(path: String, excludePatterns: List<String>): Boolean {
    for (pattern in excludePatterns) {
      val regexPattern = pattern
        .replace("**", ".+")
        .replace("*", "[^/]+")
        .replace("?", ".")
      val isExclude = path.matches(regexPattern.toRegex())
      if (isExclude) return true
    }
    return false
  }
}
