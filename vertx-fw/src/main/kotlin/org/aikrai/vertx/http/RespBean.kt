package org.aikrai.vertx.http

import com.fasterxml.jackson.annotation.JsonInclude
import org.aikrai.vertx.constant.HttpStatus
import org.aikrai.vertx.utlis.Meta

/**
 * 标准API响应格式，用于所有HTTP响应
 *
 * @param code 状态码
 * @param message 消息
 * @param data 数据（可为null）
 * @param requestId 请求ID（用于跟踪请求）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class RespBean<T>(
    val code: Int,
    val message: String,
    val data: T? = null,
    var requestId: String? = null,
) {
    companion object {
        /**
         * 创建成功响应
         *
         * @param data 响应数据
         * @param message 成功消息
         * @param code 状态码，默认为HttpStatus.SUCCESS
         * @return ApiResponse实例
         */
        fun <T> success(data: T? = null, message: String = "Success", code: Int = HttpStatus.SUCCESS): RespBean<T> {
            val finalCode = if (data == null && code == HttpStatus.SUCCESS) HttpStatus.NO_CONTENT else code
            return RespBean(
                code = finalCode,
                message = message,
                data = data
            )
        }

        /**
         * 创建错误响应
         *
         * @param code 错误码
         * @param message 错误消息
         * @param data 错误相关数据（可选）
         * @return ApiResponse实例
         */
        fun <T> error(code: Int = HttpStatus.ERROR, message: String, data: T? = null): RespBean<T> {
            return RespBean(
                code = code,
                message = message,
                data = data
            )
        }

        /**
         * 从异常创建错误响应
         *
         * @param exception 异常
         * @param defaultStatusCode 默认状态码，如果无法从异常确定状态码
         * @return ApiResponse实例
         */
        fun <T> fromException(
            exception: Throwable,
            defaultStatusCode: Int = HttpStatus.ERROR
        ): RespBean<T> {
            // 确定状态码和消息
            var statusCode = defaultStatusCode
            val errorName: String
            val errorMessage: String
            val errorData: Any?

            when (exception) {
                is Meta -> {
                    // 根据Meta.name确定状态码
                    statusCode = when (exception.name) {
                        "Unauthorized" -> HttpStatus.UNAUTHORIZED
                        "Forbidden" -> HttpStatus.FORBIDDEN
                        "NotFound" -> HttpStatus.NOT_FOUND
                        "RequiredArgument", "InvalidArgument", "BadRequest" -> HttpStatus.BAD_REQUEST
                        "Timeout" -> HttpStatus.ERROR // 使用ERROR作为超时状态码
                        "NotSupported" -> HttpStatus.UNSUPPORTED_TYPE
                        "Unimplemented" -> HttpStatus.NOT_IMPLEMENTED
                        else -> defaultStatusCode
                    }
                    errorName = exception.name
                    errorMessage = exception.message
                    errorData = exception.data
                }
                is IllegalArgumentException -> {
                    statusCode = HttpStatus.BAD_REQUEST
                    errorName = "BadRequest"
                    errorMessage = exception.message ?: "Invalid argument"
                    errorData = null
                }
                else -> {
                    // 通用异常处理
                    errorName = exception.javaClass.simpleName
                    errorMessage = exception.message ?: "Internal Server Error"
                    errorData = null
                }
            }

            // 组合错误名称和消息
            val finalMessage = if (errorMessage.contains(errorName, ignoreCase = true)) {
                errorMessage
            } else {
                "$errorName: $errorMessage"
            }

            @Suppress("UNCHECKED_CAST")
            return RespBean(
                code = statusCode,
                message = finalMessage,
                data = errorData as? T
            )
        }
    }
}

/**
 * HTTP状态码映射，用于将Meta.name映射到HTTP状态码
 */
object HttpStatusMapping {
    private val mapping = mapOf(
        "Unauthorized" to HttpStatus.UNAUTHORIZED,
        "Forbidden" to HttpStatus.FORBIDDEN,
        "NotFound" to HttpStatus.NOT_FOUND,
        "RequiredArgument" to HttpStatus.BAD_REQUEST,
        "InvalidArgument" to HttpStatus.BAD_REQUEST,
        "BadRequest" to HttpStatus.BAD_REQUEST,
        "Timeout" to HttpStatus.ERROR, // 使用ERROR作为超时状态码
        "Repository" to HttpStatus.ERROR,
        "Unimplemented" to HttpStatus.NOT_IMPLEMENTED,
        "NotSupported" to HttpStatus.UNSUPPORTED_TYPE
    )

    /**
     * 获取与Meta.name对应的HTTP状态码
     *
     * @param name Meta.name或其前缀
     * @param defaultCode 默认状态码
     * @return HTTP状态码
     */
    fun getCode(name: String, defaultCode: Int = HttpStatus.ERROR): Int {
        // 检查是否包含前缀，如"Repository:"
        val baseName = name.substringBefore(':')
        return mapping[baseName] ?: mapping[name] ?: defaultCode
    }
} 