package app.domain.role

import com.google.inject.Inject
import io.vertx.sqlclient.SqlClient
import org.aikrai.vertx.db.RepositoryImpl

class RoleRepositoryImpl @Inject constructor(
  sqlClient: SqlClient
) : RepositoryImpl<Long, Role>(sqlClient), RoleRepository
