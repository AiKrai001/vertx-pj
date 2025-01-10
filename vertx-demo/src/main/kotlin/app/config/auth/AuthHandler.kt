package app.config.auth

import app.config.Constant
import app.domain.user.UserRepository
import app.util.CacheUtil
import com.google.inject.Inject
import io.vertx.ext.auth.jwt.JWTAuth
import org.aikrai.vertx.auth.Attributes
import org.aikrai.vertx.auth.AuthUser
import org.aikrai.vertx.auth.Principal
import org.aikrai.vertx.auth.TokenUtil

class AuthHandler @Inject constructor(
  private val jwtAuth: JWTAuth,
  private val userRepository: UserRepository,
  private val cacheUtil: CacheUtil
) {

  suspend fun handle(token: String): AuthUser? {
    val userInfo = TokenUtil.authenticate(jwtAuth, token) ?: return null
    val userId = userInfo.principal().getString("id").toLong()
    val user = cacheUtil.get(Constant.USER + userId) ?: userRepository.get(userId)?.let {
      cacheUtil.put(Constant.USER + userId, it)
    } ?: return null
    return AuthUser(
      Principal(userId, user),
      // get roles and permissions from database
      Attributes(setOf("admin"), setOf("user:list")),
    )
  }
}
