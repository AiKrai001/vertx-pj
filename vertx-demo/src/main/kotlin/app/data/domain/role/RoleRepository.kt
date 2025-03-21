package app.data.domain.role

import com.google.inject.ImplementedBy
import org.aikrai.vertx.db.wrapper.Repository

@ImplementedBy(RoleRepositoryImpl::class)
interface RoleRepository : Repository<Long, Role>
