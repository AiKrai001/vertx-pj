package app.config

import app.config.auth.JWTAuthProvider
import app.config.db.DbPoolProvider
import cn.hutool.core.lang.Snowflake
import cn.hutool.core.util.IdUtil
import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.Singleton
import io.vertx.core.Vertx
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.SqlClient
import kotlinx.coroutines.CoroutineScope
import org.aikrai.vertx.config.DefaultScope
import org.aikrai.vertx.config.FrameworkConfigModule

object InjectConfig {
  fun configure(vertx: Vertx): Injector {
    return Guice.createInjector(InjectorModule(vertx))
  }
}

class InjectorModule(
  private val vertx: Vertx,
) : AbstractModule() {
  override fun configure() {
    // 1. 安装框架提供的配置模块
    install(FrameworkConfigModule())

    // 2. 绑定 Vertx 实例和 CoroutineScope
    bind(Vertx::class.java).toInstance(vertx)
    bind(CoroutineScope::class.java).toInstance(DefaultScope(vertx))

    // 3. 绑定 Snowflake
    bind(Snowflake::class.java).toInstance(IdUtil.getSnowflake())

    // 4. 绑定数据库连接池 (使用 Provider 来延迟创建)
    bind(Pool::class.java).toProvider(DbPoolProvider::class.java).`in`(Singleton::class.java)
    bind(SqlClient::class.java).to(Pool::class.java) // 绑定 SqlClient 到 Pool

    // 5. 绑定 JWTAuth
    bind(JWTAuth::class.java).toProvider(JWTAuthProvider::class.java).`in`(Singleton::class.java)
  }
}