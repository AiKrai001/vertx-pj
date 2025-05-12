package app.config.provider

import com.google.inject.Inject
import com.google.inject.Provider
import io.vertx.core.Vertx
import io.vertx.redis.client.Redis
import io.vertx.redis.client.RedisClientType
import io.vertx.redis.client.RedisOptions
import org.aikrai.vertx.config.RedisConfig

class RedisProvider @Inject constructor(
  private val vertx: Vertx,
  private val redisConfig: RedisConfig
) : Provider<Redis> {
  override fun get(): Redis {
    val options = RedisOptions()
      .setType(RedisClientType.STANDALONE)
      .addConnectionString("redis://${redisConfig.host}:${redisConfig.port}/${redisConfig.db}")
      .setMaxPoolSize(redisConfig.poolSize)
      .setMaxPoolWaiting(redisConfig.maxPoolWaiting)
    redisConfig.password?.let { options.setPassword(it) }
    return Redis.createClient(vertx, options)
  }
}