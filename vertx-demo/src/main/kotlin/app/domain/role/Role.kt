package app.domain.role

import org.aikrai.vertx.db.annotation.TableName
import org.aikrai.vertx.utlis.BaseEntity

@TableName("sys_role")
class Role : BaseEntity() {

  var roleId: Long = 0L

  var roleName: String? = null

  var roleKey: String? = null

  var roleSort: Int? = null

  var dataScope: Char? = null

  var status: Char? = null

  var delFlag: Char? = null
}
