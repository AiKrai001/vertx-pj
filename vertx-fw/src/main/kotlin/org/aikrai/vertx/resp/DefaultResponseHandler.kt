package org.aikrai.vertx.resp

import io.vertx.core.http.HttpHeaders
import io.vertx.ext.web.RoutingContext
import org.aikrai.vertx.http.RespBean
import org.aikrai.vertx.jackson.JsonUtil

/**
 * 默认响应处理器实现
 */
class DefaultResponseHandler : ResponseHandlerInterface {

    /**
     * 处理成功响应
     */
    override suspend fun handle(
        ctx: RoutingContext,
        responseData: Any?,
        customizeResponse: Boolean
    ) {
        val requestId = ctx.get<String>("requestId")
        
        // 使用RespBean包装响应数据
        val respBean = RespBean.success(responseData)
        respBean.requestId = requestId
        
        val resStr = JsonUtil.toJsonStr(respBean)
        // 存储响应内容用于日志
        ctx.put("responseData", resStr)
        
        // 如果需要自定义响应，则不发送标准响应
        if (customizeResponse) return
        
        // 发送标准成功响应
        ctx.response()
            .setStatusCode(respBean.code)
            .putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
            .end(resStr)
    }
}