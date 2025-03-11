package org.aikrai.vertx.db.tx

import io.vertx.kotlin.coroutines.coAwait
import io.vertx.sqlclient.SqlClient
import io.vertx.sqlclient.SqlConnection
import io.vertx.sqlclient.Transaction
import org.aikrai.vertx.utlis.Meta
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

class TxCtxElem(
  val connection: SqlConnection,
  val transaction: Transaction?,  // 外层事务才有实际transaction对象
  val savepointName: String? = null,  // 内层事务使用savepoint名称
  val depth: Int = 0,  // 事务嵌套深度
) : CoroutineContext.Element {
  companion object Key : CoroutineContext.Key<TxCtxElem>
  override val key: CoroutineContext.Key<*> = Key

  val isRoot: Boolean = depth == 0
  val isNested: Boolean = depth > 0
  val transactionId: String = UUID.randomUUID().toString().substring(0, 8)

  // 标记是否已回滚或提交
  var completed: Boolean = false
}

object TxCtx {
  /**
   * 判断当前是否在事务上下文中
   */
  fun isTransactionActive(context: CoroutineContext): Boolean {
    return context[TxCtxElem] != null
  }

  /**
   * 获取当前事务的连接
   */
  fun currentSqlConnection(context: CoroutineContext): SqlClient? {
    return context[TxCtxElem]?.connection
  }

  /**
   * 获取当前事务深度
   */
  fun currentTransactionDepth(context: CoroutineContext): Int {
    return context[TxCtxElem]?.depth ?: 0
  }

  /**
   * 手动控制设置当前事务回滚点
   */
  suspend fun setSavepoint(name: String): String {
    val context = coroutineContext
    val txElem = context[TxCtxElem] ?: throw Meta.error(
      "TransactionError",
      "Cannot set savepoint. No active transaction."
    )

    val connection = txElem.connection
    val pointName = "manual_$name"
    connection.query("SAVEPOINT $pointName").execute().coAwait()
    return pointName
  }

  /**
   * 手动回滚到指定保存点
   */
  suspend fun rollbackToSavepoint(name: String) {
    val context = coroutineContext
    val txElem = context[TxCtxElem] ?: throw Meta.error(
      "TransactionError",
      "Cannot rollback to savepoint. No active transaction."
    )
    val connection = txElem.connection
    connection.query("ROLLBACK TO SAVEPOINT $name").execute().coAwait()
  }
}

