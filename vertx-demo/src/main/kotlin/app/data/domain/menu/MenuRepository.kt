package app.data.domain.menu

import com.google.inject.ImplementedBy
import org.aikrai.vertx.db.wrapper.Repository

@ImplementedBy(MenuRepositoryImpl::class)
interface MenuRepository : Repository<Long, Menu> {
  suspend fun list(name: String? = null, accountId: Long? = null, roleId: Long? = null): List<Menu>
}
