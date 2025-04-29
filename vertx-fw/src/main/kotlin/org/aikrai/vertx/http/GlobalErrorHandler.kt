package org.aikrai.vertx.http

import com.google.inject.Singleton
import io.vertx.core.Handler
import io.vertx.core.http.HttpHeaders
import io.vertx.ext.web.RoutingContext
import io.github.oshai.kotlinlogging.KotlinLogging
import org.aikrai.vertx.auth.AuthUser
import org.aikrai.vertx.constant.HttpStatus
import org.aikrai.vertx.jackson.JsonUtil
import org.aikrai.vertx.utlis.Meta
import java.lang.reflect.InvocationTargetException

/**
 * 全局错误处理器，负责捕获并处理所有未捕获的异常
 */
@Singleton
class GlobalErrorHandler : Handler<RoutingContext> {
  private val logger = KotlinLogging.logger {}

  override fun handle(ctx: RoutingContext) {
    val failure = ctx.failure()
    val statusCode = determineStatusCode(ctx, failure)
    val requestId = ctx.get<String>("requestId") ?: "N/A"

    // 记录错误日志
    logError(ctx, failure, statusCode, requestId)

    // 构建标准错误响应
    val apiResponse = buildErrorResponse(failure, statusCode)
    apiResponse.requestId = requestId

    // 发送响应
    if (!ctx.response().ended()) {
      val responseJson = try {
        JsonUtil.toJsonStr(apiResponse)
      } catch (e: Exception) {
        logger.error(e) { "序列化错误响应失败 (请求ID: $requestId)" }
        // 回退到简单JSON
        """{"code":500,"message":"内部服务器错误 - 无法序列化错误响应","data":null,"requestId":"$requestId","timestamp":${System.currentTimeMillis()}}"""
      }

      ctx.put("responseData", responseJson) // 存储响应内容用于日志

      ctx.response()
        .setStatusCode(statusCode)
        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
        .end(responseJson)
    } else {
      logger.warn { "请求 ${ctx.request().uri()} 的响应已结束 (请求ID: $requestId)" }
    }
  }

  /**
   * 提取底层实际异常
   */
  private fun extractActualException(throwable: Throwable?): Throwable? {
    if (throwable == null) return null
    
    return when {
      // 处理InvocationTargetException和反射异常，它们通常包装了实际异常
      throwable is InvocationTargetException -> extractActualException(throwable.targetException)
      // 处理包含cause属性的异常
      throwable.cause != null && throwable::class.java.name == "java.lang.reflect.InvocationTargetException" -> extractActualException(throwable.cause)
      // 对于其他包含targetException属性的异常，尝试提取
      hasTargetException(throwable) -> getTargetException(throwable)
      else -> throwable
    }
  }

  /**
   * 检查异常是否有targetException属性
   */
  private fun hasTargetException(throwable: Throwable): Boolean {
    return try {
      val field = throwable::class.java.getDeclaredField("targetException")
      field.isAccessible = true
      true
    } catch (_: Exception) {
      false
    }
  }

  /**
   * 获取异常的targetException属性值
   */
  private fun getTargetException(throwable: Throwable): Throwable {
    return try {
      val field = throwable::class.java.getDeclaredField("targetException")
      field.isAccessible = true
      field.get(throwable) as Throwable
    } catch (_: Exception) {
      throwable
    }
  }

  /**
   * 确定HTTP状态码
   */
  private fun determineStatusCode(ctx: RoutingContext, failure: Throwable?): Int {
    // 优先使用RoutingContext中设置的状态码
    if (ctx.statusCode() >= 400) {
      return ctx.statusCode()
    }

    // 提取实际异常
    val actualException = extractActualException(failure)

    // 根据异常类型确定状态码
    return when (actualException) {
      is Meta -> HttpStatusMapping.getCode(actualException.name, HttpStatus.ERROR)
      is IllegalArgumentException -> HttpStatus.BAD_REQUEST
      // 可添加更多异常类型的映射
      else -> HttpStatus.ERROR // 默认为500
    }
  }

  /**
   * 构建标准错误响应
   */
  private fun buildErrorResponse(failure: Throwable?, statusCode: Int): RespBean<Any?> {
    // 提取实际异常
    val actualException = extractActualException(failure)
    
    return when (actualException) {
      null -> RespBean.error(statusCode, "发生未知错误")
      is Meta -> RespBean.fromException(actualException, statusCode)
      else -> RespBean.fromException(actualException, statusCode)
    }
  }

  /**
   * 记录错误日志
   */
  private fun logError(ctx: RoutingContext, failure: Throwable?, statusCode: Int, requestId: String) {
    val request = ctx.request()
    val uri = request.uri()
    val method = request.method().name()
    val remoteAddr = request.remoteAddress()?.host()
    val user = (ctx.user() as? AuthUser)
    
    // 提取实际异常
    val actualException = extractActualException(failure)
    
    val logMessage = buildString {
      append("处理请求失败 - ")
      append("请求ID: $requestId, ")
      append("用户ID: ${user?.id}, ")
      append("方法: $method, ")
      append("URI: $uri, ")
      append("客户端IP: $remoteAddr, ")
      append("状态码: $statusCode")
      if (actualException != null) {
        append(", 异常类型: ${actualException::class.java.name}")
        if (!actualException.message.isNullOrBlank()) {
          append(", 消息: ${actualException.message}")
        }
      }
    }

    // 根据状态码选择日志级别
    if (statusCode >= 500 && actualException != null) {
      logger.error(actualException) { logMessage } // 记录带堆栈的日志
    } else {
      logger.warn { logMessage } // 400级别错误只记录警告
    }
  }
} 