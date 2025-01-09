package app.domain.user

import com.google.inject.Inject
import io.vertx.sqlclient.SqlClient
import org.aikrai.vertx.db.RepositoryImpl

class UserRepositoryImpl @Inject constructor(
  sqlClient: SqlClient
) : RepositoryImpl<Long, User>(sqlClient), UserRepository {

  override suspend fun getByName(name: String): User? {
    val user = queryBuilder()
      .eq(User::userName, name)
      .getOne()
    return user
  }

  override suspend fun testTransaction(): User? {
//    throw Meta.failure("test transaction", "test transaction")
    return queryBuilder().getOne()
  }

  override suspend fun getList(): List<User> {
    return queryBuilder().getList()
  }
}
