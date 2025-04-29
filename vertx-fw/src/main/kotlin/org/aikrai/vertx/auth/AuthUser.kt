package org.aikrai.vertx.auth

import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.impl.UserImpl
import org.aikrai.vertx.utlis.Meta

class AuthUser(
  val id: Long,
  val token: String,
  val user: JsonObject,
  val roles: Set<String>,
  val accesses: Set<String>,
  val loginIp: String? = null,
  val client: String? = null,
) : UserImpl(JsonObject(), JsonObject()) {

  companion object {
    fun AuthUser.isAdmin(): Boolean {
      return roles.contains("admin")
    }

    fun AuthUser.validateAuth(permission: CheckPermission? = null) {
      validateAuth(null, permission)
    }

    fun AuthUser.validateAuth(role: CheckRole? = null, permission: CheckPermission? = null) {
      // 如果没有权限要求，直接返回
      if (role == null && permission == null) return

      // 验证角色
      role?.let { r ->
        val roleSet = roles.toSet()
        if (roleSet.contains("admin")) return
        if (roleSet.isEmpty()) {
          throw Meta.forbidden("权限不足")
        } else {
          val reqRoleSet = (r.value + r.type).filter { it.isNotBlank() }.toSet()
          if (!validateSet(reqRoleSet, roleSet, r.mode)) {
            throw Meta.forbidden("权限不足")
          }
        }
      }

      // 验证权限
      permission?.let { p ->
        val permissionSet = accesses.toSet()
        val roleSet = roles.toSet()
        if (roleSet.contains("admin")) return
        if (permissionSet.isEmpty() && roleSet.isEmpty()) {
          throw Meta.forbidden("权限不足")
        } else {
          if (p.orRole.isNotEmpty()) {
            val roleBoolean = validateSet(p.orRole.toSet(), roleSet, Mode.AND)
            if (roleBoolean) return
          }
          val reqPermissionSet = (p.value + p.type).filter { it.isNotBlank() }.toSet()
          if (!validateSet(reqPermissionSet, permissionSet, p.mode)) {
            throw Meta.forbidden("权限不足")
          }
        }
      }
    }

    private fun validateSet(
      required: Set<String>,
      actual: Set<String>,
      mode: Mode
    ): Boolean {
      if (required.isEmpty()) return true
      return when (mode) {
        Mode.AND -> required == actual
        Mode.OR -> required.any { it in actual }
      }
    }
  }
}
