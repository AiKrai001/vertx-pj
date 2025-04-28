package org.aikrai.vertx.http

import cn.hutool.core.lang.Snowflake
import com.google.inject.Inject
import io.vertx.core.Handler
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.github.oshai.kotlinlogging.KotlinLogging
import org.aikrai.vertx.jackson.JsonUtil
import org.aikrai.vertx.utlis.IpUtil
import org.slf4j.MDC
import java.time.Instant

/**
 * 请求日志处理器，负责生成请求ID，记录请求和响应的详细信息
 */
class RequestLogHandler @Inject constructor(
    private val snowflake: Snowflake
) : Handler<RoutingContext> {
    private val logger = KotlinLogging.logger {}

    override fun handle(ctx: RoutingContext) {
        val startTime = System.currentTimeMillis()
        
        // 生成请求ID
        val requestId = snowflake.nextIdStr()
        ctx.put("requestId", requestId)
        
        // 将请求ID放入MDC
        MDC.put("requestId", requestId)
        
        // 记录基本请求信息
        val request = ctx.request()
        val method = request.method()
        val path = request.path()
        val remoteAddr = IpUtil.getIpAddr(request) // 使用工具类获取真实IP
        
        // 将基本信息放入MDC
        MDC.put("method", method.name())
        MDC.put("path", path)
        MDC.put("remoteAddr", remoteAddr)
        
        // 记录开始日志
        logger.info { "请求开始 - 方法: $method, 路径: $path, 客户端IP: $remoteAddr, 请求ID: $requestId" }
        
        // 在请求结束时记录详细日志
        ctx.response().endHandler {
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            val response = ctx.response()
            val statusCode = response.statusCode
            
            try {
                // 构建详细日志数据
                val logData = JsonObject()
                    .put("timestamp", Instant.ofEpochMilli(endTime).toString())
                    .put("requestId", requestId)
                    .put("method", method.name())
                    .put("uri", request.uri())
                    .put("path", path)
                    .put("statusCode", statusCode)
                    .put("durationMs", duration)
                    .put("remoteAddr", remoteAddr)
                    .put("userAgent", request.getHeader(HttpHeaders.USER_AGENT))
                
                // 尝试获取用户信息
                val userId = ctx.user()?.principal()?.getString("sub")
                if (userId != null) {
                    logData.put("userId", userId)
                    MDC.put("userId", userId)
                }
                
                // 视请求方法，可能记录查询参数
                if (method == HttpMethod.GET || method == HttpMethod.DELETE) {
                    val queryParams = request.params().iterator().asSequence()
                        .map { it.key to it.value }
                        .toMap()
                    if (queryParams.isNotEmpty()) {
                        logData.put("queryParams", JsonObject(queryParams))
                    }
                }
                
                // 根据内容类型，可能记录请求体（小心处理敏感信息）
                if (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH) {
                    val contentType = request.getHeader(HttpHeaders.CONTENT_TYPE)
                    if (contentType?.contains("application/json") == true) {
                        val body = ctx.body().asString()
                        if (!body.isNullOrBlank()) {
                            try {
                                val bodyJson = JsonObject(body)
                                // 处理敏感字段，如密码
                                val sanitizedBody = sanitizeSensitiveData(bodyJson)
                                logData.put("requestBody", sanitizedBody)
                            } catch (e: Exception) {
                                logData.put("requestBodyRaw", "无法解析为JSON: " + body.take(100) + "...")
                            }
                        }
                    }
                }
                
                // 尝试获取和记录响应数据
                val responseData = ctx.get<String>("responseData")
                if (!responseData.isNullOrBlank()) {
                    try {
                        val responseJson = JsonObject(responseData)
                        logData.put("responseBody", responseJson)
                    } catch (e: Exception) {
                        logData.put("responseBodyRaw", responseData.take(100) + "...")
                    }
                }
                
                // 根据状态码选择日志级别
                MDC.put("statusCode", statusCode.toString())
                MDC.put("duration", duration.toString())
                
                val logMessage = buildString {
                    append("请求完成 - ")
                    append("方法: $method, ")
                    append("路径: $path, ")
                    append("状态码: $statusCode, ")
                    append("耗时: ${duration}ms, ")
                    append("请求ID: $requestId")
                }
                
                when {
                    statusCode >= 500 -> logger.error { logMessage }
                    statusCode >= 400 -> logger.warn { logMessage }
                    else -> logger.info { logMessage }
                }
                
                // 以JSON格式记录详细信息（可根据需要启用）
                logger.debug { "请求详细信息: ${JsonUtil.toJsonStr(logData)}" }
            } finally {
                // 清理MDC
                MDC.remove("requestId")
                MDC.remove("method")
                MDC.remove("path")
                MDC.remove("remoteAddr")
                MDC.remove("userId")
                MDC.remove("statusCode")
                MDC.remove("duration")
            }
        }
        
        // 继续下一个处理器
        ctx.next()
    }
    
    /**
     * 处理敏感数据，避免在日志中记录敏感信息
     */
    private fun sanitizeSensitiveData(json: JsonObject): JsonObject {
        val result = json.copy()
        val sensitiveFields = listOf("password", "passwordConfirm", "oldPassword", "newPassword", "token", "accessToken", "refreshToken")
        
        for (field in sensitiveFields) {
            if (result.containsKey(field)) {
                result.put(field, "******")
            }
        }
        
        return result
    }
} 