package app.service.account

import app.data.domain.account.Account
import app.data.domain.account.AccountRepository
import app.data.domain.account.LoginDTO
import app.service.auth.TokenService
import cn.hutool.core.lang.Snowflake
import cn.hutool.crypto.SecureUtil
import com.google.inject.Inject
import io.vertx.ext.web.RoutingContext
import io.github.oshai.kotlinlogging.KotlinLogging
import org.aikrai.vertx.db.tx.withTransaction
import org.aikrai.vertx.utlis.IpUtil
import org.aikrai.vertx.utlis.Meta
import java.security.SecureRandom

class AccountService @Inject constructor(
  private val snowflake: Snowflake,
  private val accountRepository: AccountRepository,
  private val tokenService: TokenService,
) {
  private val logger = KotlinLogging.logger { }

  suspend fun testTransaction() {
    withTransaction {
      accountRepository.update(1L, mapOf("avatar" to "test0001"))

      try {
        withTransaction {
          accountRepository.update(1L, mapOf("avatar" to "test002"))
          throw Meta.error("test transaction", "test transaction")
        }
      } catch (e: Exception) {
        logger.info { "内层事务失败已处理: ${e.message}" }
      }
    }
  }

  suspend fun sign(
    context: RoutingContext,
    loginInfo: LoginDTO
  ): String {
    val ipAddr = IpUtil.getIpAddr(context.request())
    accountRepository.getByField(Account::userName, loginInfo.username)?.let {
      throw Meta("authentication_failed", "名称已被占用")
    }
    val account = Account().apply {
//      this.userId = snowflake.nextId()
      this.userName = loginInfo.username
      this.password = SecureUtil.sha1(loginInfo.password)
    }
    accountRepository.create(account)
    return tokenService.createToken(account.userId, ipAddr, "management")
  }

  suspend fun login(
    context: RoutingContext,
    loginInfo: LoginDTO
  ): String {
    val ipAddr = IpUtil.getIpAddr(context.request())
    val account = accountRepository.getByField(Account::userName, loginInfo.username) ?: throw Meta(
      "authentication_failed",
      "账号或密码错误"
    )
    if (!SecureUtil.sha1(loginInfo.password).equals(account.password)) {
      throw Meta(
        "authentication_failed",
        "账号或密码错误"
      )
    }
    return tokenService.createToken(account.userId, ipAddr, "management")
  }

  private fun genNumericStr(length: Int): String {
    val random = SecureRandom()
    val numericCode = StringBuilder(length)
    for (i in 0 until length) {
      val digit = random.nextInt(10)
      numericCode.append(digit)
    }
    return numericCode.toString()
  }
}
