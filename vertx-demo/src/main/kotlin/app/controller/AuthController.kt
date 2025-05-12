package app.controller

import app.data.dto.account.LoginDTO
import app.service.account.AccountService
import com.google.inject.Inject
import io.vertx.ext.web.RoutingContext
import org.aikrai.vertx.auth.AllowAnonymous
import org.aikrai.vertx.context.Controller
import org.aikrai.vertx.context.D

@AllowAnonymous
@D("认证")
@Controller("/auth")
class AuthController @Inject constructor(
  private val accountService: AccountService,
) {

  @D("注册")
  suspend fun doSign(
    context: RoutingContext,
    @D("loginInfo", "账号信息") loginInfo: LoginDTO
  ): String {
    return accountService.sign(context, loginInfo)
  }

  @D("登录")
  suspend fun doLogin(
    context: RoutingContext,
    @D("loginInfo", "账号信息") loginInfo: LoginDTO
  ): String {
    return accountService.login(context, loginInfo)
  }
}
