package app.repository.impl

import app.data.domain.role.Role
import app.repository.RoleRepository
import com.google.inject.Inject
import io.vertx.sqlclient.SqlClient
import org.aikrai.vertx.db.wrapper.RepositoryImpl

class RoleRepositoryImpl @Inject constructor(
  sqlClient: SqlClient
) : RepositoryImpl<Long, Role>(sqlClient), RoleRepository