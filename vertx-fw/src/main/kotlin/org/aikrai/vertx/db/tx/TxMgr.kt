package org.aikrai.vertx.db.tx

import io.vertx.kotlin.coroutines.coAwait
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.SqlConnection
import io.vertx.sqlclient.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.aikrai.vertx.utlis.Meta
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

suspend fun <T> withTransaction(block: suspend CoroutineScope.() -> T): Any? {
  return TxMgrHolder.txMgr.withTransaction(block)
}

object TxMgrHolder {
  private val _txMgr = AtomicReference<TxMgr?>(null)

  val txMgr: TxMgr
    get() = _txMgr.get() ?: throw Meta.error(
      "TransactionError",
      "TxMgr(TransactionManager)尚未初始化,请先调用initTxMgr()"
    )

  /**
   * 原子地初始化 TxMgr(TransactionManager)。
   * 如果已经初始化，该方法将直接返回。
   *
   * @param pool SQL 客户端连接池。
   */
  fun initTxMgr(pool: Pool) {
    if (_txMgr.get() != null) return
    val newManager = TxMgr(pool)
    _txMgr.compareAndSet(null, newManager)
  }
}

class TxMgr(private val pool: Pool) {
  private val logger = KotlinLogging.logger { }

  suspend fun <T> withTransaction(block: suspend CoroutineScope.() -> T): T {
    val currentContext = coroutineContext
    val currentTx = currentContext[TxCtxElem]

    // 已在事务中 - 创建SAVEPOINT
    if (currentTx != null) {
      return withSavepoint(currentTx, block)
    }

    // 外层事务 - 创建实际事务
    val connection = pool.connection.coAwait()
    val transaction = connection.begin().coAwait()
    val startTime = System.currentTimeMillis()

    try {
      // 创建根事务上下文
      val txElem = TxCtxElem(connection, transaction, depth = 0)
      logger.debug { "Root transaction ${txElem.transactionId} started" }

      val result = withContext(currentContext + txElem) {
        block()
      }

      // 提交事务
      if (!txElem.completed) {
        transaction.commit().coAwait()
        txElem.completed = true
        logger.debug { "Root transaction ${txElem.transactionId} committed, took ${System.currentTimeMillis() - startTime}ms" }
      }
      return result

    } catch (e: Exception) {
      logger.error(e) { "Root transaction failed, rolling back" }

      transaction.rollback().coAwait()

      throw e
    } finally {
      connection.close()
    }
  }


  private suspend fun <T> withSavepoint(
    parentTx: TxCtxElem,
    block: suspend CoroutineScope.() -> T
  ): T {
    val connection = parentTx.connection
    val savepointName = "sp_${UUID.randomUUID().toString().replace("-", "").substring(0, 10)}"
    val startTime = System.currentTimeMillis()

    // 创建保存点
    connection.query("SAVEPOINT $savepointName").execute().coAwait()
    logger.debug { "Nested transaction with savepoint $savepointName started" }

    try {
      // 创建嵌套事务上下文
      val nestedTxElem = TxCtxElem(
        connection = connection,
        transaction = null,  // 嵌套事务没有独立的Transaction对象
        savepointName = savepointName,
        depth = parentTx.depth + 1,
      )

      val result = withContext(coroutineContext + nestedTxElem) {
        block()
      }

      // 嵌套事务成功，释放保存点
      if (!nestedTxElem.completed) {
        connection.query("RELEASE SAVEPOINT $savepointName").execute().coAwait()
        nestedTxElem.completed = true
        logger.debug { "Savepoint $savepointName released, took ${System.currentTimeMillis() - startTime}ms" }
      }
      return result

    } catch (e: Exception) {
      logger.warn(e) { "Nested transaction failed, rolling back to savepoint $savepointName" }
      // 回滚到保存点，但不影响外层事务
      connection.query("ROLLBACK TO SAVEPOINT $savepointName").execute().coAwait()
      throw e
    }
  }
}
