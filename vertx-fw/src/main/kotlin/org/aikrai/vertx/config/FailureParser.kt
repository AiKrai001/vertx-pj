package org.aikrai.vertx.config

import io.vertx.mysqlclient.MySQLException
import io.vertx.pgclient.PgException
import org.aikrai.vertx.jackson.JsonUtil
import org.aikrai.vertx.utlis.Meta
import java.sql.SQLException

object FailureParser {
  private fun Throwable.toMeta(): Meta {
    val error = this as? Meta
    if (error != null) {
      return error
    }

    val name = javaClass.simpleName

    val pgException = this as? PgException
    if (pgException != null) {
      return Meta(name, pgException.errorMessage ?: "", null)
    }

    val mysqlException = this as? MySQLException
    if (mysqlException != null) {
      return Meta(name, mysqlException.message ?: "", null)
    }

    val message = if (message != null) message.orEmpty() else toString()
    return Meta(name, message, null)
  }

  private fun Throwable.info(): String {
    if (this is Meta) {
      return JsonUtil.toJsonStr(this)
    }
    return stackTraceToString()
  }

  data class Failure(val statusCode: Int, val response: Meta)

  fun parse(statusCode: Int, error: Throwable): Failure {
    return when (error) {
      is SQLException -> Failure(statusCode, Meta.failure(error.javaClass.name, "执行错误"))
      else -> Failure(statusCode, error.toMeta())
    }
  }
}
