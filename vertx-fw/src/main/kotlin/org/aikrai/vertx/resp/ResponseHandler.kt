package org.aikrai.vertx.resp

import io.vertx.ext.web.RoutingContext

/**
 * 响应处理器接口，负责处理API响应
 */
interface ResponseHandler {
    /**
     * 处理成功响应
     *
     * @param ctx 路由上下文
     * @param responseData 响应数据
     * @param customizeResponse 是否自定义响应，如果为true则由控制器自行处理响应
     */
    suspend fun handle(ctx: RoutingContext, responseData: Any?, customizeResponse: Boolean = false)
}