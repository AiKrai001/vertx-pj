package app.service.user.impl

import app.domain.role.RoleRepository
import app.domain.user.User
import app.domain.user.UserRepository
import app.service.user.UserService
import com.google.inject.Inject
import org.aikrai.vertx.db.tx.withTransaction

class UserServiceImpl @Inject constructor(
  private val userRepository: UserRepository,
  private val roleRepository: RoleRepository
) : UserService {

  override suspend fun updateUser(user: User) {
    userRepository.getByName(user.userName!!)?.let {
      userRepository.update(user)
      roleRepository.update(user.id!!, mapOf("type" to "normal"))
    }
  }

  override suspend fun testTransaction() {
    // withTransaction嵌套时, 使用的是同一个事务对象，要成功全部成功，要失败全部失败
    withTransaction {
      val execute1 = userRepository.execute<Int>("update sys_user set email = '88888' where user_name = '运若汐'")
      println("运若汐: $execute1")
      withTransaction {
        val execute = userRepository.execute<Int>("update sys_user set email = '88888' where user_name = '郸明'")
        println("郸明: $execute")
//        throw Meta.failure("test transaction", "test transaction")
      }
    }
  }
}
