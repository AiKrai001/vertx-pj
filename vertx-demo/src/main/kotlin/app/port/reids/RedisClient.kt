package app.port.reids

import com.google.inject.Inject
import com.google.inject.Singleton
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.redis.client.*
import mu.KotlinLogging
import org.aikrai.vertx.config.RedisConfig

@Singleton
class RedisClient @Inject constructor(
  vertx: Vertx,
  redisConfig: RedisConfig
) {
  private val logger = KotlinLogging.logger { }
  
  private var redisClient = Redis.createClient(
    vertx,
    RedisOptions()
      .setType(RedisClientType.STANDALONE)
      .addConnectionString("redis://${redisConfig.host}:${redisConfig.port}/${redisConfig.db}")
      .setPassword(redisConfig.pass ?: "")
      .setMaxPoolSize(redisConfig.poolSize)
      .setMaxPoolWaiting(redisConfig.maxPoolWaiting)
  )

  // EX秒，PX毫秒
  suspend fun set(key: String, value: String, expireSeconds: Int) {
    redisClient.send(Request.cmd(Command.SET, key, value, "EX", expireSeconds))
  }

  suspend fun get(key: String): String? {
    val res = redisClient.send(Request.cmd(Command.GET, key)).coAwait()
    return res?.toString()
  }

  suspend fun incr(key: String): Int {
    val res = redisClient.send(Request.cmd(Command.INCR, key)).coAwait()
    return res?.toInteger() ?: 0
  }

  fun expire(key: String, expire: String) {
    redisClient.send(Request.cmd(Command.EXPIRE, key, expire))
  }
}
