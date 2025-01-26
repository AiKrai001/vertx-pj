package org.aikrai.vertx.config.resp

import io.vertx.core.http.HttpHeaders
import io.vertx.ext.web.RoutingContext
import mu.KotlinLogging
import org.aikrai.vertx.constant.HttpStatus
import org.aikrai.vertx.jackson.JsonUtil

class DefaultResponseHandler: ResponseHandlerInterface {
  private val logger = KotlinLogging.logger { }

  override suspend fun normal(
    ctx: RoutingContext,
    responseData: Any?,
    customizeResponse: Boolean
  ) {
    val resStr = JsonUtil.toJsonStr(responseData)
    ctx.put("responseData", resStr)
    if (customizeResponse) return
    ctx.response()
      .setStatusCode(HttpStatus.SUCCESS)
      .putHeader("Content-Type", "application/json")
      .end(resStr)
  }

  override suspend fun exception(ctx: RoutingContext, e: Exception) {
    logger.error { "${ctx.request().uri()}: ${ctx.failure().stackTraceToString()}" }
    val failure = ctx.failure()
    if (failure == null) {
      ctx.response()
        .setStatusCode(ctx.statusCode())
        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
        .end()
    } else {
      val resStr = JsonUtil.toJsonStr(failure)
      ctx.put("responseData", resStr)
      ctx.response()
        .setStatusCode(ctx.statusCode())
        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
        .end(resStr)
    }
  }
}