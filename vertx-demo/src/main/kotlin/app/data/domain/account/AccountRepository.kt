package app.data.domain.account

import app.data.domain.account.modle.AccountRoleAccessDTO
import app.data.domain.account.modle.AccountRoleDTO
import com.google.inject.ImplementedBy
import org.aikrai.vertx.db.Repository

@ImplementedBy(AccountRepositoryImpl::class)
interface AccountRepository : Repository<Long, Account> {
  suspend fun getByName(name: String): Account?
  suspend fun getUserList(userName: String?, phone: String?): List<Account>
  suspend fun getInfo(id: Long): AccountRoleAccessDTO?

  suspend fun getAccountRole(id: Long): AccountRoleDTO?
  suspend fun bindRoles(id: Long, roles: List<Long>)
  suspend fun removeAllRole(id: Long): Int
}
