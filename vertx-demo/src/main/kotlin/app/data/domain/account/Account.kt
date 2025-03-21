package app.data.domain.account

import app.data.emun.Status
import org.aikrai.vertx.db.annotation.*
import org.aikrai.vertx.jackson.JsonUtil
import org.aikrai.vertx.utlis.BaseEntity
import java.sql.Timestamp

@TableName("sys_user")
class Account : BaseEntity() {

  @TableId(type = IdType.ASSIGN_ID)
  @TableFieldComment("用户ID")
  var userId: Long = 0L

  @TableField("user_name")
  var userName: String? = ""

  var userType: String? = ""

  var email: String? = ""

  var phone: String? = ""

  var avatar: String? = null

  var password: String? = null

  var status: Status? = Status.ACTIVE

  var delFlag: Char? = null

  var loginIp: String? = null

  @TableField(fill = FieldFill.UPDATE)
  var loginDate: Timestamp? = null

  override fun toString(): String {
    return JsonUtil.toJsonStr(this)
  }
}
