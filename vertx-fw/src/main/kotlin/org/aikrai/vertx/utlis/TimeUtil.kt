package org.aikrai.vertx.utlis

import java.sql.Timestamp
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

object TimeUtil {
  private class Time {

    @Volatile
    var currentTimeMillis: Long

    init {
      currentTimeMillis = System.currentTimeMillis()
      scheduleTick()
    }

    private fun scheduleTick() {
      ScheduledThreadPoolExecutor(1) { runnable: Runnable? ->
        val thread = Thread(runnable, "current-time-millis")
        thread.isDaemon = true
        thread
      }.scheduleAtFixedRate(
        { currentTimeMillis = System.currentTimeMillis() },
        1,
        1,
        TimeUnit.MILLISECONDS
      )
    }
  }

  private val instance = Time()

  val now: Long
    get() {
      return instance.currentTimeMillis
    }

  fun now(): Timestamp {
    return Timestamp(instance.currentTimeMillis)
  }
}
