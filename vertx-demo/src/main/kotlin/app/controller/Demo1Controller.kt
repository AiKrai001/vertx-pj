package app.controller

import app.domain.CargoType
import app.domain.account.Account
import app.domain.account.AccountRepository
import app.service.account.AccountService
import com.google.inject.Inject
import mu.KotlinLogging
import org.aikrai.vertx.auth.AllowAnonymous
import org.aikrai.vertx.config.Config
import org.aikrai.vertx.context.Controller
import org.aikrai.vertx.context.D

/**
 * 推荐代码示例
 */
@D("测试1:测试")
@Controller
class Demo1Controller @Inject constructor(
  private val accountService: AccountService,
  private val accountRepository: AccountRepository
) {
  private val logger = KotlinLogging.logger { }

  @AllowAnonymous
  @D("参数测试", "详细说明......")
  suspend fun test1(
    @D("name", "姓名") name: String?,
    @D("age", "年龄") age: Int?,
    @D("list", "列表") list: List<String>?,
    @D("cargoType", "货物类型") cargoType: CargoType?
  ) {
    logger.info { "你好" }
    println(age)
    println(list)
    println("test-$name")
    println(cargoType)
  }

  @D("事务测试")
  suspend fun testT() {
    accountService.testTransaction()
  }

  @D("查询测试")
  suspend fun getUserList(
    @D("userName", "用户名") userName: String?,
    @D("phone", "手机号") phone: String?
  ): List<Account> {
    val list = accountRepository.getUserList(userName, phone)
    println(list)
    return list
  }

  @AllowAnonymous
  @D("配置读取测试")
  suspend fun testRetriever(
    @D("key", "key") key: String
  ) {
    val configMap = Config.getKey(key)
    println(configMap)
  }
}
