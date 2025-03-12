package app.data.domain.menu

import app.data.domain.account.Account
import com.google.inject.Inject
import com.google.inject.Singleton
import io.vertx.ext.auth.User
import org.aikrai.vertx.auth.AuthUser
import org.aikrai.vertx.auth.AuthUser.Companion.isAdmin
import org.aikrai.vertx.utlis.Meta

@Singleton
class MenuManager @Inject constructor(
  private val menuRepository: MenuRepository
) {
  suspend fun get(id: Long) = menuRepository.get(id)
  suspend fun findAll() = menuRepository.list()

  suspend fun getMenuTree(user: User): List<Menu> {
    return getChildPerms(list(user), 0).toMutableList()
  }

  suspend fun list(user: User, name: String? = null): List<Menu> {
    val authUser = (user as AuthUser)
    return if (authUser.isAdmin()) {
      menuRepository.list(name)
    } else {
      menuRepository.list(name, (authUser.user as Account).userId)
    }
  }

  suspend fun findByRoleId(user: User, roleId: Long): List<Long> {
    if ((user as AuthUser).isAdmin()) {
      return menuRepository.list().map { it.menuId }
    }
    val menuList = menuRepository.list(roleId = roleId)
    return menuList.map { it.menuId }
  }

  suspend fun add(
    menuName: String,
    parentId: Long?,
    orderNum: Int?,
    path: String,
    component: String?,
    menuType: String?,
    visible: String?,
    perms: String
  ) {
    if (menuRepository.list(menuName).isNotEmpty()) {
      throw Meta.error("MenuNameConflict", "菜单名称已存在")
    }
    val menu = Menu().apply {
      this.menuName = menuName
      this.path = path
      this.perms = perms
      parentId?.let { this.parentId = it }
      orderNum?.let { this.orderNum = it }
      component?.let { this.component = it }
      menuType?.let { this.menuType = it }
      visible?.let { this.visible = it }
    }
    menuRepository.create(menu)
  }

  suspend fun edit(
    menuId: Long,
    menuName: String?,
    parentId: Long?,
    orderNum: Int?,
    path: String?,
    component: String?,
    menuType: String?,
    visible: String?,
    perms: String?
  ) {
    val menu = menuRepository.get(menuId) ?: throw Meta.notFound("MenuNotFound", "菜单不存在")

    if (menuName != null && menuName != menu.menuName && menuRepository.list(menuName).isNotEmpty()) {
      throw Meta.error("MenuNameConflict", "菜单名称已存在")
    }

    menu.apply {
      menuName?.let { this.menuName = it }
      path?.let { this.path = it }
      perms?.let { this.perms = it }
      parentId?.let { this.parentId = it }
      orderNum?.let { this.orderNum = it }
      component?.let { this.component = it }
      menuType?.let { this.menuType = it }
      visible?.let { this.visible = it }
    }
    menuRepository.update(menu)
  }

  suspend fun remove(menuId: Long) {
    menuRepository.delete(menuId)
  }

  companion object {
    /**
     * 根据父节点的ID获取所有子节点
     *
     * @param list 分类表
     * @param parentId 传入的父节点ID
     * @return List<SysMenu>
     */
    fun getChildPerms(list: List<Menu>, parentId: Long): List<Menu> {
      val returnList = mutableListOf<Menu>()
      for (t in list) {
        // 根据传入的某个父节点ID,遍历该父节点的所有子节点
        if (t.parentId == parentId) {
          recursionFn(list, t)
          returnList.add(t)
        }
      }
      return returnList
    }

    /**
     * 递归列表
     *
     * @param list 分类表
     * @param t 子节点
     */
    private fun recursionFn(list: List<Menu>, t: Menu) {
      // 得到子节点列表
      val childList = getChildList(list, t).toMutableList()
      t.children = childList
      for (tChild in childList) {
        if (hasChild(list, tChild)) {
          recursionFn(list, tChild)
        }
      }
    }

    /**
     * 得到子节点列表
     */
    private fun getChildList(list: List<Menu>, t: Menu): List<Menu> {
      return list.filter { it.parentId == t.menuId }
    }

    /**
     * 判断是否有子节点
     */
    private fun hasChild(list: List<Menu>, t: Menu): Boolean {
      return getChildList(list, t).isNotEmpty()
    }
  }
}
