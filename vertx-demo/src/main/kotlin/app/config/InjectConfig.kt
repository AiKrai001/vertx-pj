package app.config

import app.config.provider.JWTAuthProvider
import app.config.provider.DbPoolProvider
import app.config.provider.RedisProvider
import cn.hutool.core.lang.Snowflake
import cn.hutool.core.util.IdUtil
import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.Singleton
import io.vertx.core.Vertx
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.redis.client.Redis
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.SqlClient
import kotlinx.coroutines.CoroutineScope
import org.aikrai.vertx.config.DefaultScope
import org.aikrai.vertx.config.FrameworkConfigModule
import org.aikrai.vertx.http.GlobalErrorHandler
import org.aikrai.vertx.http.RequestLogHandler

/**
 * 依赖注入配置
 */
object InjectConfig {
  fun configure(vertx: Vertx): Injector {
    return Guice.createInjector(InjectorModule(vertx))
  }
}

/**
 * Guice模块配置
 */
class InjectorModule(
  private val vertx: Vertx,
) : AbstractModule() {
  override fun configure() {
    install(FrameworkConfigModule())

    bind(Vertx::class.java).toInstance(vertx)
    bind(CoroutineScope::class.java).toInstance(DefaultScope(vertx))

    bind(Snowflake::class.java).toInstance(IdUtil.getSnowflake())

    bind(Redis::class.java).toProvider(RedisProvider::class.java).`in`(Singleton::class.java)
    bind(Pool::class.java).toProvider(DbPoolProvider::class.java).`in`(Singleton::class.java)
    bind(SqlClient::class.java).to(Pool::class.java)

    bind(JWTAuth::class.java).toProvider(JWTAuthProvider::class.java).`in`(Singleton::class.java)

    bind(RequestLogHandler::class.java).`in`(Singleton::class.java)
    bind(GlobalErrorHandler::class.java).`in`(Singleton::class.java)
  }
}