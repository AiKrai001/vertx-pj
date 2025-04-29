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
      return
    }

    scope.launch {
      try {
        val user = tokenService.getLoginUser(ctx)
        ctx.setUser(user)
        ctx.next()
      } catch (e: Throwable) {
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