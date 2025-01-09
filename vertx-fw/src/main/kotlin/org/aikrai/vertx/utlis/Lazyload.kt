package org.aikrai.vertx.utlis

import io.vertx.core.Closeable
import io.vertx.core.Promise
import io.vertx.core.Vertx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

open class Lazyload<T>(private val block: suspend () -> T) {
  protected var value: T? = null
  private val mutex = Mutex()

  fun dirty() {
    value = null
  }

  open suspend fun get(): T {
    if (value == null) {
      mutex.withLock {
        if (value == null) {
          value = block()
        }
      }
    }
    return value!!
  }
}

// timeout: milliseconds
class Autoload<T>(
  private val vertx: Vertx,
  private val coroutineScope: CoroutineScope,
  private val timeout: Long,
  private val block: suspend () -> T
) : Lazyload<T>(block), Closeable {

  private var periodicId = vertx.setPeriodic(timeout) {
    coroutineScope.launch {
      value = block()
    }
  }

  override fun close(completion: Promise<Void>?) {
    vertx.cancelTimer(periodicId)
    completion?.complete()
  }
}

class Aliveload<T>(
  private val vertx: Vertx,
  private val coroutineScope: CoroutineScope,
  private val timeout: Long,
  private val block: suspend () -> T
) : Lazyload<T>(block), Closeable {

  private var needsUpdate = true

  private var periodicId = vertx.setPeriodic(timeout) {
    if (needsUpdate) {
      needsUpdate = false
      coroutineScope.launch {
        value = block()
      }
    }
  }

  override suspend fun get(): T {
    needsUpdate = true
    return super.get()
  }

  override fun close(completion: Promise<Void>?) {
    vertx.cancelTimer(periodicId)
    completion?.complete()
  }
}
