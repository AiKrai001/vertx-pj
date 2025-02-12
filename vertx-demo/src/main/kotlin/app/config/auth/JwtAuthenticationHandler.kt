package app.config.auth

import cn.hutool.core.lang.Snowflake
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.AuthenticationHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.aikrai.vertx.utlis.Meta

class JwtAuthenticationHandler(
  val scope: CoroutineScope,
  val tokenService: TokenService,
  val context: String,
  val snowflake: Snowflake
) : AuthenticationHandler {
  override fun handle(event: RoutingContext) {
    event.put("requestId", snowflake.nextId())
    val path = event.request().path().replace("$context/", "/").replace("//", "/")
    if (isPathExcluded(path, anonymous)) {
      event.next()
      return
    }
    scope.launch {
      try {
        val user = tokenService.getLoginUser(event)
        tokenService.verifyToken(user)
        event.setUser(user)
        event.next()
      } catch (e: Throwable) {
        event.fail(401, Meta.unauthorized(e.message ?: "token"))
      }
    }
  }

  var anonymous = mutableListOf(
    "/apidoc.json"
  )

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
