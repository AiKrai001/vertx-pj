package org.aikrai.vertx.db.tx

import io.vertx.sqlclient.SqlConnection
import io.vertx.sqlclient.Transaction
import java.util.*
import kotlin.coroutines.CoroutineContext

class TxCtxElem(
  val sqlConnection: SqlConnection,
  val transaction: Transaction,
  val isActive: Boolean = true,
  val isNested: Boolean = false,
  val transactionStack: Stack<TxCtxElem>,
  val index: Int = transactionStack.size,
  val transactionId: String = UUID.randomUUID().toString()
) : CoroutineContext.Element {
  companion object Key : CoroutineContext.Key<TxCtxElem>
  override val key: CoroutineContext.Key<*> = Key

  override fun toString(): String {
    return "TransactionContextElement(transactionId=$transactionId, isActive=$isActive, isNested=$isNested)"
  }
}

object TxCtx {
  fun getTransactionId(context: CoroutineContext): String? {
    return context[TxCtxElem.Key]?.transactionId
  }

  fun currentTransaction(context: CoroutineContext): Transaction? {
    return context[TxCtxElem.Key]?.transaction
  }

  fun currentSqlConnection(context: CoroutineContext): SqlConnection? {
    return context[TxCtxElem.Key]?.sqlConnection
  }

  fun isTransactionActive(context: CoroutineContext): Boolean {
    return context[TxCtxElem.Key]?.isActive ?: false
  }
}
