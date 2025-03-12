package app.data.domain.account.modle

import app.data.domain.account.Account
import app.data.domain.menu.Menu
import app.data.domain.role.Role

data class AccountRoleAccessDTO(
  val account: Account,
  val roles: List<Role>,
  val access: List<Menu>,
) {
  val rolesArr: List<String>
    get() {
      return roles.mapNotNull { it.roleKey }.filter { it.isNotEmpty() }
    }
  val accessArr: List<String>
    get() {
      return if (rolesArr.contains("admin")) {
        listOf("*:*:*")
      } else {
        access.map { it.perms }.filter { it.isNotEmpty() }
      }
    }
}
