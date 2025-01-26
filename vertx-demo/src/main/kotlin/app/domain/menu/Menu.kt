package app.domain.menu

import org.aikrai.vertx.db.annotation.IdType
import org.aikrai.vertx.db.annotation.TableId
import org.aikrai.vertx.db.annotation.TableName
import org.aikrai.vertx.utlis.BaseEntity
import kotlin.jvm.Transient

@TableName("sys_menu")
class Menu : BaseEntity() {

  @TableId(type = IdType.ASSIGN_ID)
  var menuId = 0L

  var menuName = ""

  var parentId = 0L

  var orderNum = 0

  var path = ""

  var component: String? = ""

  var menuType = ""

  var visible = "0"

  var perms = ""

  var parentName = ""

  @Transient
  var children = mutableListOf<Menu>()
}
