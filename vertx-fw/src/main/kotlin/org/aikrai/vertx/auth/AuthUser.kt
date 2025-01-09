package org.aikrai.vertx.auth

import io.vertx.ext.auth.impl.UserImpl
import org.aikrai.vertx.jackson.JsonUtil

class AuthUser(
  principal: Principal,
  attributes: Attributes,
) : UserImpl(JsonUtil.toJsonObject(principal), JsonUtil.toJsonObject(attributes))

class Principal(
  val id: Long,
  val info: Any,
)

class Attributes(
  val role: Set<String>,
  val permissions: Set<String>,
)
