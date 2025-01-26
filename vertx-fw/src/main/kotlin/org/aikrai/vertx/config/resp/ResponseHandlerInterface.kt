package org.aikrai.vertx.config.resp

import io.vertx.ext.web.RoutingContext

interface ResponseHandlerInterface {
  suspend fun normal(ctx: RoutingContext, responseData: Any?, customizeResponse: Boolean = false)
  suspend fun exception(ctx: RoutingContext, e: Exception)
}