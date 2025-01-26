package app.config.auth

import app.config.RespBean
import com.google.inject.Singleton
import io.vertx.ext.web.RoutingContext
import mu.KotlinLogging
import org.aikrai.vertx.config.resp.ResponseHandlerInterface
import org.aikrai.vertx.constant.HttpStatus
import org.aikrai.vertx.jackson.JsonUtil
import org.aikrai.vertx.utlis.Meta

@Singleton
class ResponseHandler: ResponseHandlerInterface {
  private val logger = KotlinLogging.logger { }

  override suspend fun normal(
    ctx: RoutingContext,
    responseData: Any?,
    customizeResponse: Boolean
  ) {
    val requestId = ctx.get<Long>("requestId") ?: -1L
    val code: Int
    val resStr = when (responseData) {
      is Unit -> {
        code = HttpStatus.NO_CONTENT
        null
      }
      is RespBean -> {
        code = responseData.code
        responseData.requestId = requestId
        JsonUtil.toJsonStr(responseData)
      }

      else -> {
        val respBean = RespBean.success(responseData).apply {
          this.requestId = requestId
        }
        code = respBean.code
        JsonUtil.toJsonStr(respBean)
      }
    }
    ctx.put("responseData", resStr)
    if (customizeResponse) return
    ctx.response()
      .setStatusCode(code)
      .putHeader("Content-Type", "application/json")
      .end(resStr)
  }

  // 业务异常处理
  override suspend fun exception(ctx: RoutingContext, e: Exception) {
    logger.error { "${ctx.request().uri()}: ${e.stackTraceToString()}" }
    val resObj = when(e) {
      is Meta -> {
        RespBean.failure("${e.name}:${e.message}", e.data)
      }
      else -> {
        RespBean.failure("${e.javaClass.simpleName}${if (e.message != null) ":${e.message}" else ""}")
      }
    }
    val resStr = JsonUtil.toJsonStr(resObj)
    ctx.put("responseData", resStr)
    ctx.response()
      .setStatusCode(HttpStatus.ERROR)
      .putHeader("Content-Type", "application/json")
      .end(resStr)
  }
}