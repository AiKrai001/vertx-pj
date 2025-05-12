package app.repository

import app.data.domain.role.Role
import app.repository.impl.RoleRepositoryImpl
import com.google.inject.ImplementedBy
import org.aikrai.vertx.db.wrapper.Repository

@ImplementedBy(RoleRepositoryImpl::class)
interface RoleRepository : Repository<Long, Role>