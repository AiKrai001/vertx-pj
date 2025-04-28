package app.service.auth

import app.data.domain.account.AccountRepository
import app.port.reids.RedisClient
import cn.hutool.core.util.IdUtil
import com.google.inject.Inject
import com.google.inject.Singleton
import io.vertx.core.cli.UsageMessageFormatter
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.JWTOptions
import io.vertx.ext.auth.User
import io.vertx.ext.auth.authentication.TokenCredentials
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.coAwait
import mu.KotlinLogging
import org.aikrai.vertx.auth.AuthUser
import org.aikrai.vertx.constant.CacheConstants
import org.aikrai.vertx.constant.Constants
import org.aikrai.vertx.jackson.JsonUtil
import org.aikrai.vertx.utlis.Meta

@Singleton
class TokenService @Inject constructor(
  private val jwtAuth: JWTAuth,
  private val redisClient: RedisClient,
  private val accountRepository: AccountRepository,
) {
  private val logger = KotlinLogging.logger { }
  private val expireSeconds = 60 * 60 * 24 * 7

  suspend fun getLoginUser(ctx: RoutingContext): AuthUser {
    val request = ctx.request()
    val authorization = request.getHeader("Authorization")
    if (UsageMessageFormatter.isNullOrEmpty(authorization) || !authorization.startsWith("token ")) {
      throw Meta.unauthorized("token")
    }
    val token = authorization.substring(6)
    val user = parseToken(token) ?: throw Meta.unauthorized("token")
    val userToken = user.principal().getString(Constants.LOGIN_USER_KEY) ?: throw Meta.unauthorized("token")
    val authInfoStr = redisClient.get(CacheConstants.LOGIN_TOKEN_KEY + userToken) ?: throw Meta.unauthorized("token")
    return JsonUtil.parseObject(authInfoStr, AuthUser::class.java)
  }

  suspend fun createToken(userId: Long, ip: String, client: String): String {
    val token = IdUtil.randomUUID()
    val userInfo = accountRepository.getInfo(userId)
    val user = userInfo?.account ?: throw Meta.notFound("AccountNotFound", "账号不存在")
    val authInfo = AuthUser(token, JsonUtil.toJsonObject(user), userInfo.rolesArr.toSet(), userInfo.accessArr.toSet(), ip, client)
    val authInfoStr = JsonUtil.toJsonStr(authInfo)
    redisClient.set(CacheConstants.LOGIN_TOKEN_KEY + token, authInfoStr, expireSeconds)
    return genToken(mapOf(Constants.LOGIN_USER_KEY to token))
  }


  private fun genToken(info: Map<String, Any>, expires: Int? = null): String {
    val jwtOptions = JWTOptions().setExpiresInSeconds(expires ?: (60 * 60 * 24 * 7))
    return jwtAuth.generateToken(JsonObject(info), jwtOptions)
  }

  private suspend fun parseToken(token: String): User? {
    val tokenCredentials = TokenCredentials(token)
    return jwtAuth.authenticate(tokenCredentials).coAwait() ?: return null
  }
}
