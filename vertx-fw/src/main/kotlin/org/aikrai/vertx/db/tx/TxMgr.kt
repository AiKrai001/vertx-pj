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
    get() = _txMgr.get() ?: throw Meta.failure(
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

class TxMgr(
  private val pool: Pool
) {

  private val logger = KotlinLogging.logger { }
  private val transactionStackMap = ConcurrentHashMap<CoroutineContext, Stack<TxCtxElem>>()

  /**
   * 在事务上下文中执行一个块。
   *
   * @param block 需要在事务中执行的挂起函数。
   * @return 块的结果。
   */
  suspend fun <T> withTransaction(block: suspend CoroutineScope.() -> T): Any? {
    val currentContext = coroutineContext
    val transactionStack = currentContext[TxCtxElem]?.transactionStack ?: Stack<TxCtxElem>()
    // 外层事务，嵌套事务，都创建新的连接和事务。实现外层事务回滚时所有嵌套事务回滚，嵌套事务回滚不影响外部事务
    val connection: SqlConnection = pool.connection.coAwait()
    val transaction: Transaction = connection.begin().coAwait()

    return try {
      val txCtxElem =
        TxCtxElem(connection, transaction, true, transactionStack.isNotEmpty(), transactionStack)
      transactionStack.push(txCtxElem)
      logger.debug { (if (txCtxElem.isNested) "嵌套" else "") + "事务Id:" + txCtxElem.transactionId + "开始" }

      withContext(currentContext + txCtxElem) {
        val result = block()
        if (txCtxElem.index == 0) {
          while (transactionStack.isNotEmpty()) {
            val txCtx = transactionStack.pop()
            txCtx.transaction.commit().coAwait()
            logger.debug { (if (txCtx.isNested) "嵌套" else "") + "事务Id:" + txCtx.transactionId + "提交" }
          }
        }
        result
      }
    } catch (e: Exception) {
      logger.error(e) { "Transaction failed, rollback" }
      if (transactionStack.isNotEmpty() && !transactionStack.peek().isNested) {
        // 外层事务失败，回滚所有事务
        logger.error { "Rolling back all transactions" }
        while (transactionStack.isNotEmpty()) {
          val txCtxElem = transactionStack.pop()
          txCtxElem.transaction.rollback().coAwait()
          logger.debug { (if (txCtxElem.isNested) "嵌套" else "") + "事务Id:" + txCtxElem.transactionId + "回滚" }
        }
        throw e
      } else {
        // 嵌套事务失败，只回滚当前事务
        val txCtxElem = transactionStack.pop()
        txCtxElem.transaction.rollback().coAwait()
        logger.debug(e) { (if (txCtxElem.isNested) "嵌套" else "") + "事务Id:" + txCtxElem.transactionId + "回滚" }
      }
    } finally {
      if (transactionStack.isEmpty()) {
        transactionStackMap.remove(currentContext) // 清理上下文
        connection.close() // 仅在外层事务时关闭连接
      }
    }
  }
}
