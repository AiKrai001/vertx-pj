package org.aikrai.vertx.utlis

import io.vertx.core.MultiMap
import mu.KotlinLogging
import java.sql.Timestamp
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.collections.forEach
import kotlin.collections.set
import kotlin.stackTraceToString
import kotlin.text.isNullOrBlank
import kotlin.text.trim

object LangUtil {
  private val logger = KotlinLogging.logger { }

  fun <R> letTry(clause: () -> R): R? {
    return try {
      clause()
    } catch (ex: Throwable) {
      logger.error { ex.stackTraceToString() }
      null
    }
  }

  fun <T, R> T?.letIf(clause: (T) -> R): R? {
    if (this != null) {
      return clause(this)
    }
    return null
  }

  fun String?.defaultAsNull(): String? {
    if (this.isNullOrBlank()) {
      return null
    }
    return this.trim()
  }

  fun Long?.defaultAsNull(): Long? {
    if (this == 0L) {
      return null
    }
    return this
  }

  fun Int?.defaultAsNull(): Int? {
    if (this == 0) {
      return null
    }
    return this
  }

  fun Double?.defaultAsNull(): Double? {
    if (this == 0.0) {
      return null
    }
    return this
  }

  fun MultiMap.toMap(): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    forEach { map[it.key] == it.value }
    return map
  }

  fun MultiMap.toStringMap(): Map<String, String> {
    val map = mutableMapOf<String, String>()
    forEach { map[it.key] = it.value.toString() }
    return map
  }

  fun offsetDateTime(epochMilli: Long?): OffsetDateTime? {
    if (epochMilli == null || epochMilli == 0L) return null
    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneId.systemDefault())
  }

  fun timestamp(): Timestamp {
    return Timestamp.from(Instant.now())
  }
}
