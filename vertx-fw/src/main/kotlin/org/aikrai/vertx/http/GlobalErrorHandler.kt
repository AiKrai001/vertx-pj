package org.aikrai.vertx.http

import com.google.inject.Singleton
import io.vertx.core.Handler
import io.vertx.core.http.HttpHeaders
import io.vertx.ext.web.RoutingContext
import io.github.oshai.kotlinlogging.KotlinLogging
import org.aikrai.vertx.constant.HttpStatus
import org.aikrai.vertx.jackson.JsonUtil
import org.aikrai.vertx.utlis.Meta
import org.slf4j.MDC

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
     * 确定HTTP状态码
     */
    private fun determineStatusCode(ctx: RoutingContext, failure: Throwable?): Int {
        // 优先使用RoutingContext中设置的状态码
        if (ctx.statusCode() >= 400) {
            return ctx.statusCode()
        }

        // 根据异常类型确定状态码
        return when (failure) {
            is Meta -> HttpStatusMapping.getCode(failure.name, HttpStatus.ERROR)
            is IllegalArgumentException -> HttpStatus.BAD_REQUEST
            // 可添加更多异常类型的映射
            else -> HttpStatus.ERROR // 默认为500
        }
    }

    /**
     * 构建标准错误响应
     */
    private fun buildErrorResponse(failure: Throwable?, statusCode: Int): RespBean<Any?> {
        return when (failure) {
            null -> RespBean.error(statusCode, "发生未知错误")
            is Meta -> RespBean.fromException(failure, statusCode)
            else -> RespBean.fromException(failure, statusCode)
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

        // 将请求ID放入MDC
        MDC.put("requestId", requestId)

        try {
            val logMessage = buildString {
                append("处理请求失败 - ")
                append("请求ID: $requestId, ")
                append("方法: $method, ")
                append("URI: $uri, ")
                append("客户端IP: $remoteAddr, ")
                append("状态码: $statusCode")
                if (failure != null) {
                    append(", 异常类型: ${failure::class.java.name}")
                    if (!failure.message.isNullOrBlank()) {
                        append(", 消息: ${failure.message}")
                    }
                }
            }

            // 根据状态码选择日志级别
            if (statusCode >= 500 && failure != null) {
                logger.error(failure) { logMessage } // 记录带堆栈的日志
            } else {
                logger.warn { logMessage } // 400级别错误只记录警告
            }
        } finally {
            // 清理MDC
            MDC.remove("requestId")
        }
    }
} 