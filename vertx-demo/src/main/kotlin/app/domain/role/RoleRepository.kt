package app.domain.role

import com.google.inject.ImplementedBy
import org.aikrai.vertx.db.Repository

@ImplementedBy(RoleRepositoryImpl::class)
interface RoleRepository : Repository<Long, Role>
