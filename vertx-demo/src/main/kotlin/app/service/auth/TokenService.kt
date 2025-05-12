package app.service.auth

import app.repository.AccountRepository
import app.utils.RedisUtil
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
import io.github.oshai.kotlinlogging.KotlinLogging
import io.vertx.redis.client.Redis
import org.aikrai.vertx.auth.AuthUser
import org.aikrai.vertx.constant.CacheConstants
import org.aikrai.vertx.constant.Constants
import org.aikrai.vertx.jackson.JsonUtil
import org.aikrai.vertx.utlis.Meta
import java.util.concurrent.TimeUnit

@Singleton
class TokenService @Inject constructor(
  redis: Redis,
  private val jwtAuth: JWTAuth,
  private val accountRepository: AccountRepository,
) {
  private val logger = KotlinLogging.logger { }
  private val expireSeconds = 60L * 60 * 24 * 7
  private val redisUtil = RedisUtil(redis)


  suspend fun getLoginUser(ctx: RoutingContext): AuthUser {
    val request = ctx.request()
    val authorization = request.getHeader("Authorization")
    if (UsageMessageFormatter.isNullOrEmpty(authorization) || !authorization.startsWith("token ")) {
      throw Meta.unauthorized("token")
    }
    val token = authorization.substring(6)
    val user = parseToken(token) ?: throw Meta.unauthorized("token")
    val userToken = user.principal().getString(Constants.LOGIN_USER_KEY) ?: throw Meta.unauthorized("token")
    val authInfoStr = redisUtil.getObject<String>(CacheConstants.LOGIN_TOKEN_KEY + userToken) ?: throw Meta.unauthorized("token")
    return JsonUtil.parseObject(authInfoStr, AuthUser::class.java)
  }

  suspend fun createToken(userId: Long, ip: String, client: String): String {
    val token = IdUtil.randomUUID()
    val userInfo = accountRepository.getInfo(userId)
    val user = userInfo?.account ?: throw Meta.notFound("AccountNotFound", "账号不存在")
    val authInfo = AuthUser(userInfo.account.userId, token, JsonUtil.toJsonObject(user), userInfo.rolesArr.toSet(), userInfo.accessArr.toSet(), ip, client)
    val authInfoStr = JsonUtil.toJsonStr(authInfo)
    redisUtil.setObject(CacheConstants.LOGIN_TOKEN_KEY + token, authInfoStr, expireSeconds, TimeUnit.SECONDS)
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
