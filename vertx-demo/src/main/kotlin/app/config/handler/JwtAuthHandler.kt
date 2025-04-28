package app.config.handler

import app.service.auth.TokenService
import com.google.inject.Inject
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.AuthenticationHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.aikrai.vertx.config.ServerConfig
import org.aikrai.vertx.constant.HttpStatus
import org.aikrai.vertx.utlis.Meta
import org.slf4j.MDC

/**
 * JWT认证处理器
 */
class JwtAuthHandler @Inject constructor(
  val scope: CoroutineScope,
  val tokenService: TokenService,
  val serverConfig: ServerConfig,
  ) : AuthenticationHandler {
  override fun handle(ctx: RoutingContext) {
    val path = ctx.request().path().replace("${serverConfig.context}/", "/").replace("//", "/")
    if (isPathExcluded(path, anonymous)) {
      ctx.next()
    }

    scope.launch {
      try {
        val user = tokenService.getLoginUser(ctx)
        ctx.setUser(user)

        // 将用户ID放入MDC
        user.principal().getString("sub")?.let { userId ->
          MDC.put("userId", userId)
        }
        ctx.next()
      } catch (e: Throwable) {
        MDC.remove("userId")

        val metaError = when (e) {
          is Meta -> e
          else -> Meta.unauthorized(e.message ?: "认证失败")
        }
        ctx.fail(HttpStatus.UNAUTHORIZED, metaError)
      }
    }
  }

  var anonymous = mutableListOf(
    "/apidoc.json"
  )

  /**
   * 检查路径是否在排除列表中
   */
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