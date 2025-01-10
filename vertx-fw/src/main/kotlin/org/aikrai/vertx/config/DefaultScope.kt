package org.aikrai.vertx.config

import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext
import kotlin.properties.Delegates

class DefaultScope(private val vertx: Vertx) : CoroutineScope {
  override var coroutineContext: CoroutineContext by Delegates.notNull()

  init {
    coroutineContext = vertx.orCreateContext.dispatcher()
  }
}
