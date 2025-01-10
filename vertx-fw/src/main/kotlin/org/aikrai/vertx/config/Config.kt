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
  private var configMap = emptyMap<String, Any>()

  suspend fun init(vertx: Vertx) {
    if (retriever.get() != null) return
    val configRetriever = load(vertx)
    val cas = retriever.compareAndSet(null, configRetriever)
    if (cas) {
      val configObj = configRetriever.config.coAwait()
      configMap = FlattenUtil.flattenJsonObject(configObj)
    }
  }

  fun getKey(key: String): Any? {
    if (retriever.get() == null) throw IllegalStateException("Config not initialized")
    // 检查 configMap 中是否存在指定的 key
    return if (configMap.containsKey(key)) {
      configMap[key]
    } else {
      // 找到所有以 key 开头的条目
      configMap.filterKeys { it.startsWith(key) }
    }
  }

  fun getConfigMap(): Map<String, Any> {
    return configMap
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
