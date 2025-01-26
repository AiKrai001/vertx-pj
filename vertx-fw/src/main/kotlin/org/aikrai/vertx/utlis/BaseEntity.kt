package org.aikrai.vertx.utlis

import org.aikrai.vertx.db.annotation.FieldFill
import org.aikrai.vertx.db.annotation.TableField
import org.aikrai.vertx.utlis.TimeUtil.now
import java.sql.Timestamp

open class BaseEntity {

  var createBy: String? = null

  @TableField(fill = FieldFill.INSERT)
  var createTime: Timestamp = now()

  var updateBy: String? = null

  @TableField(fill = FieldFill.UPDATE)
  var updateTime: Timestamp = now()

  var remark: String? = null

  var version: Long = 0
}
