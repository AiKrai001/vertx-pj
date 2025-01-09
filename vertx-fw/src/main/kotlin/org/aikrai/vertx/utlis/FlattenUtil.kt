package org.aikrai.vertx.utlis

import io.vertx.core.json.JsonObject

object FlattenUtil {

  fun flattenMap(map: Map<String, Any>, parentKey: String = "", separator: String = "."): Map<String, Any> {
    val flatMap = mutableMapOf<String, Any>()
    for ((key, value) in map) {
      val newKey = if (parentKey.isEmpty()) key else "$parentKey$separator$key"
      when (value) {
        is Map<*, *> -> {
          @Suppress("UNCHECKED_CAST")
          flatMap.putAll(flattenMap(value as Map<String, Any>, newKey, separator))
        }

        else -> flatMap[newKey] = value
      }
    }
    return flatMap
  }

  fun flattenJsonObject(obj: JsonObject, parentKey: String = "", separator: String = "."): Map<String, Any> {
    val flatMap = mutableMapOf<String, Any>()
    for (key in obj.fieldNames()) {
      val value = obj.getValue(key)
      val newKey = if (parentKey.isEmpty()) key else "$parentKey$separator$key"
      when (value) {
        is JsonObject -> {
          flatMap.putAll(flattenJsonObject(value, newKey, separator))
        }

        is Iterable<*> -> {
          // 如果值是数组，可以根据需要展开或处理
          flatMap[newKey] = value
        }

        else -> {
          flatMap[newKey] = value
        }
      }
    }
    return flatMap
  }
}
