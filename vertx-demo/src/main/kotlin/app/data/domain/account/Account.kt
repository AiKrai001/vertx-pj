package app.data.domain.account

import app.data.emun.SexType
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

  @TableFieldComment("部门ID")
  var deptId: Long? = 0L

  @TableField(length = 30)
  @TableFieldComment("用户账号")
  var userName: String = ""

  @TableField(length = 30)
  @TableFieldComment("用户昵称")
  var nickName: String = ""

  @TableField(length = 2)
  @TableFieldComment("用户类型")
  var userType: String? = ""

  @TableField(length = 50)
  @TableFieldComment("用户邮箱")
  var email: String? = ""

  @TableField(length = 11)
  @TableFieldComment("手机号码")
  var phonenumber: String? = ""

  @TableField(length = 1)
  @TableFieldComment("用户性别（0男 1女 2未知）")
  var sex: SexType? = SexType.UNKNOWN

  @TableField(length = 100)
  @TableFieldComment("头像地址")
  var avatar: String? = null

  @TableField(length = 100)
  @TableFieldComment("密码")
  var password: String? = null

  @TableField(length = 1)
  @TableFieldComment("帐号状态（0正常 1停用）")
  var status: Status? = Status.ACTIVE

  @TableField(length = 1)
  @TableFieldComment("删除标志（0代表存在 2代表删除）")
  var delFlag: Char? = null

  var loginIp: String? = null

  @TableField(fill = FieldFill.UPDATE)
  var loginDate: Timestamp? = null

  override fun toString(): String {
    return JsonUtil.toJsonStr(this)
  }
}
