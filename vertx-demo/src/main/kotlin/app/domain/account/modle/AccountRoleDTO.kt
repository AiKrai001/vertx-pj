package app.base.domain.auth.modle

import app.domain.role.Role

data class AccountRoleDTO(
  val id: Long,
  val name: String,
  val phone: String,
//  val status: AccountStatus,
  val avatar: String,
  val openid: String,
  val unionid: String,
  val sopenid: String,
  val roles: List<Role>,
)
