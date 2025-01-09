package app.controller

import app.config.Constant
import app.domain.user.LoginDTO
import app.domain.user.User
import app.domain.user.UserRepository
import app.util.CacheUtil
import cn.hutool.core.lang.Snowflake
import cn.hutool.crypto.SecureUtil
import com.google.inject.Inject
import io.vertx.ext.auth.jwt.JWTAuth
import org.aikrai.vertx.auth.AllowAnonymous
import org.aikrai.vertx.auth.TokenUtil
import org.aikrai.vertx.context.Controller
import org.aikrai.vertx.context.D
import org.aikrai.vertx.utlis.Meta

@AllowAnonymous
@D("认证")
@Controller("/auth")
class AuthController @Inject constructor(
  private val jwtAuth: JWTAuth,
  private val snowflake: Snowflake,
  private val userRepository: UserRepository,
  private val cacheUtil: CacheUtil
) {

  @D("注册")
  suspend fun doSign(
    @D("loginInfo", "账号信息") loginInfo: LoginDTO
  ): String {
    userRepository.getByName(loginInfo.username)?.let {
      throw Meta.failure("LoginFailed", "用户名已被使用")
    }
    val user = User().apply {
      this.id = snowflake.nextId()
      this.userName = loginInfo.username
      this.password = SecureUtil.sha1(loginInfo.password)
      this.loginName = loginInfo.username
    }
    cacheUtil.put(Constant.USER + user.id, user)
    userRepository.create(user)
    return TokenUtil.genToken(jwtAuth, mapOf("id" to user.id!!))
  }

  @D("登录")
  suspend fun doLogin(
    @D("loginInfo", "账号信息") loginInfo: LoginDTO
  ): String {
    val user = userRepository.getByName(loginInfo.username) ?: throw Meta.failure("LoginFailed", "用户名或密码错误")
    if (user.password == SecureUtil.sha1(loginInfo.password)) {
      cacheUtil.put(Constant.USER + user.id, user)
      return TokenUtil.genToken(jwtAuth, mapOf("id" to user.id!!))
    } else {
      throw Meta.failure("LoginFailed", "用户名或密码错误")
    }
  }
}
