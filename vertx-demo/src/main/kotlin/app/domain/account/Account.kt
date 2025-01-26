package app.domain.account

import org.aikrai.vertx.db.annotation.*
import org.aikrai.vertx.utlis.BaseEntity
import java.sql.Timestamp

@TableName("sys_user")
class Account : BaseEntity() {

  @TableId(type = IdType.ASSIGN_ID)
  var userId: Long = 0L

  @TableField("user_name")
  var userName: String? = ""

  var userType: String? = ""

  var email: String? = ""

  var phone: String? = ""

  var avatar: String? = null

  var password: String? = null

  var status: Char? = null

  var delFlag: Char? = null

  var loginIp: String? = null

  @TableField(fill = FieldFill.UPDATE)
  var loginDate: Timestamp? = null
}
