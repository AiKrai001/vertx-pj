package org.aikrai.vertx.config

import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.config.ConfigStoreOptions
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.coAwait
import org.aikrai.vertx.utlis.FlattenUtil
import java.util.concurrent.atomic.AtomicReference

object Config {
  private val retriever = AtomicReference<ConfigRetriever?>(null)
  private val configMapRef = AtomicReference<Map<String, Any>>(emptyMap())

  suspend fun init(vertx: Vertx) {
    if (retriever.get() != null) return
    val configRetriever = load(vertx)
    val cas = retriever.compareAndSet(null, configRetriever)
    if (cas) {
      val configObj = configRetriever.config.coAwait()
      // 存储扁平化的 Map
      configMapRef.set(FlattenUtil.flattenJsonObject(configObj))
    }
  }

  fun getString(key: String, defaultValue: String): String {
    return configMapRef.get()[key]?.toString() ?: defaultValue
  }

  fun getStringOrNull(key: String): String? {
    return configMapRef.get()[key]?.toString()
  }

  fun getInt(key: String, defaultValue: Int): Int {
    return configMapRef.get()[key]?.toString()?.toIntOrNull() ?: defaultValue
  }

  fun getIntOrNull(key: String): Int? {
    return configMapRef.get()[key]?.toString()?.toIntOrNull()
  }

  fun getLong(key: String, defaultValue: Long): Long {
    return configMapRef.get()[key]?.toString()?.toLongOrNull() ?: defaultValue
  }

  fun getLongOrNull(key: String): Long? {
    return configMapRef.get()[key]?.toString()?.toLongOrNull()
  }

  fun getBoolean(key: String, defaultValue: Boolean): Boolean {
     val value = configMapRef.get()[key]
     return when (value) {
         is Boolean -> value
         is String -> value.toBooleanStrictOrNull() ?: defaultValue // toBooleanStrictOrNull 更安全
         else -> defaultValue
     }
  }

  fun getBooleanOrNull(key: String): Boolean? {
     val value = configMapRef.get()[key]
     return when (value) {
         is Boolean -> value
         is String -> value.toBooleanStrictOrNull()
         else -> null
     }
  }

  // 获取嵌套对象或列表
  fun getObject(keyPrefix: String): Map<String, Any>? {
     val map = configMapRef.get()
     val subMap = map.filterKeys { it.startsWith("$keyPrefix.") }
         .mapKeys { it.key.removePrefix("$keyPrefix.") }
     return if (subMap.isEmpty()) null else subMap
  }

  fun getStringList(key: String, defaultValue: List<String> = emptyList()): List<String> {
    return (configMapRef.get()[key] as? JsonArray)?.mapNotNull { it?.toString() } ?: defaultValue
  }

  fun getStringListOrNull(key: String): List<String>? {
     return (configMapRef.get()[key] as? JsonArray)?.mapNotNull { it?.toString() }
  }

  fun getConfigMap(): Map<String, Any> {
    return configMapRef.get()
  }

  private suspend fun load(vertx: Vertx): ConfigRetriever {
    val sysStore = ConfigStoreOptions().setType("sys")
    val envStore = ConfigStoreOptions().setType("env").setConfig(JsonObject().put("raw-data", true))
    val bootstrapStore = ConfigStoreOptions().setType("file").setFormat("yaml")
      .setConfig(JsonObject().put("path", "bootstrap.yml"))

    val bootstrapOptions = ConfigRetrieverOptions()
      .addStore(bootstrapStore)
      .addStore(sysStore)
      .addStore(envStore)

    val bootstrapRetriever = ConfigRetriever.create(vertx, bootstrapOptions)
    val bootstrapConfig = bootstrapRetriever.config.coAwait()
    val useDir = bootstrapConfig.getString("user.dir")
    val environment = bootstrapConfig.getJsonObject("server").getString("active")

    // 创建资源目录配置存储
    val rDirectoryStore = createDirectoryStore("$useDir/src/main/resources/config")
    // 创建项目根目录配置存储
    val pDirectoryStore = createDirectoryStore(useDir)
    // 创建环境相关配置存储
    val directoryStore = createDirectoryStore("config${if (!environment.isNullOrBlank()) "/$environment" else ""}")

    // 后加载的配置会覆盖前面加载的相同的配置
    val options = ConfigRetrieverOptions()
      // 项目的resources目录下
      .addStore(rDirectoryStore)
      // 项目根目录下
      .addStore(pDirectoryStore)
      // 项目根目录下的config目录
      .addStore(directoryStore)
      // bootstrap.yml 文件
      .addStore(bootstrapStore)
    return ConfigRetriever.create(vertx, options)
  }

  private fun createDirectoryStore(path: String): ConfigStoreOptions {
    return ConfigStoreOptions()
      .setType("directory")
      .setConfig(
        JsonObject().put("path", path).put(
          "filesets",
          JsonArray()
            .add(JsonObject().put("pattern", "*.yml").put("format", "yaml"))
            .add(JsonObject().put("pattern", "*.yaml").put("format", "yaml"))
            .add(JsonObject().put("pattern", "*.properties").put("format", "properties"))
            .add(JsonObject().put("pattern", "*.json").put("format", "json"))
        )
      )
  }
}
