package app.config

import org.aikrai.vertx.constant.HttpStatus


data class RespBean(
  val code: Int,
  val message: String,
  val data: Any?
) {
  var requestId: Long = -1L

  companion object {
    /**
     * 创建一个成功的响应
     *
     * @param data 响应数据
     * @return RespBean 实例
     */
    fun success(data: Any? = null): RespBean {
      val code = when (data) {
        null -> HttpStatus.NO_CONTENT
        else -> HttpStatus.SUCCESS
      }
      return RespBean(code, "Success", data)
    }

    /**
     * 创建一个失败的响应
     *
     * @param status 状态码
     * @param message 错误消息
     * @return RespBean 实例
     */
    fun failure(message: String, data: Any? = null): RespBean {
      return failure(HttpStatus.ERROR, message, data)
    }

    fun failure(code: Int, message: String, data: Any? = null): RespBean {
      return RespBean(HttpStatus.ERROR, message, data)
    }


    // 访问受限，授权过期
    fun forbidden(message: String?): RespBean {
      return failure(HttpStatus.FORBIDDEN, message ?: "Restricted access, expired authorizations")
    }

    // 未授权
    fun unauthorized(message: String?): RespBean {
      return failure(HttpStatus.UNAUTHORIZED, message ?: "Unauthorized")
    }
  }
}
