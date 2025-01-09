package app.domain.user

import com.google.inject.ImplementedBy
import org.aikrai.vertx.db.Repository

@ImplementedBy(UserRepositoryImpl::class)
interface UserRepository : Repository<Long, User> {
  suspend fun getByName(name: String): User?

  suspend fun testTransaction(): User?

  suspend fun getList(): List<User>
}
