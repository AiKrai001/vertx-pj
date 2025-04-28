package app.config.handler

import com.google.inject.Singleton
import io.vertx.core.http.HttpHeaders
import io.vertx.ext.web.RoutingContext
import mu.KotlinLogging
import org.aikrai.vertx.http.RespBean
import org.aikrai.vertx.jackson.JsonUtil
import org.aikrai.vertx.resp.ResponseHandlerInterface

/**
 * 响应处理器，负责处理API响应
 */
@Singleton
class ResponseHandler : ResponseHandlerInterface {
  private val logger = KotlinLogging.logger { }

  /**
   * 处理成功响应
   */
  override suspend fun handle(
    ctx: RoutingContext,
    responseData: Any?,
    customizeResponse: Boolean
  ) {
    // 使用RequestLogHandler设置的请求ID
    val requestId = ctx.get<String>("requestId")
    val code: Int
    val resStr = when (responseData) {
      is RespBean<*> -> {
        code = responseData.code
        responseData.requestId = requestId
        JsonUtil.toJsonStr(responseData)
      }
      // 否则使用RespBean包装
      else -> {
        val respBean = RespBean.Companion.success(responseData)
        respBean.requestId = requestId
        code = respBean.code
        JsonUtil.toJsonStr(respBean)
      }
    }
    ctx.put("responseData", resStr) // 存储响应内容用于日志

    if (customizeResponse) return // 如果需要自定义响应，则不发送标准响应

    ctx.response()
      .setStatusCode(code)
      .putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
      .end(resStr)
  }
}