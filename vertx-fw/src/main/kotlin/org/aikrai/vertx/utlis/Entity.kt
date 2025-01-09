package org.aikrai.vertx.utlis

import jakarta.persistence.*
import java.time.Instant

@MappedSuperclass
open class Entity {

  @Column(name = "create_by", length = 64)
  var createBy: String? = null

  @Column(name = "create_time")
  var createTime: Instant? = null

  @Column(name = "update_by", length = 64)
  var updateBy: String? = null

  @Column(name = "update_time")
  var updateTime: Instant? = null

  @Version
  @Column(name = "version")
  var version: Long = 0
}
