package app.domain.menu

import app.base.domain.auth.menu.MenuRepository
import com.google.inject.Inject
import io.vertx.sqlclient.SqlClient
import org.aikrai.vertx.db.RepositoryImpl

class MenuRepositoryImpl @Inject constructor(
  sqlClient: SqlClient
) : RepositoryImpl<Long, Menu>(sqlClient), MenuRepository {

  override suspend fun list(name: String?, accountId: Long?, roleId: Long?): List<Menu> {
    return emptyList()
  }
}
