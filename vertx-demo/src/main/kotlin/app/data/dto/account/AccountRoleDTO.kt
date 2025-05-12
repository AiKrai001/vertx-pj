package app.data.dto.account

import app.data.domain.role.Role

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
