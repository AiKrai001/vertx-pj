package app.config

import app.config.auth.JWTAuthProvider
import cn.hutool.core.lang.Snowflake
import cn.hutool.core.util.IdUtil
import com.google.inject.*
import com.google.inject.name.Names
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.mysqlclient.MySQLBuilder
import io.vertx.mysqlclient.MySQLConnectOptions
import io.vertx.pgclient.PgBuilder
import io.vertx.pgclient.PgConnectOptions
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.PoolOptions
import io.vertx.sqlclient.SqlClient
import kotlinx.coroutines.CoroutineScope
import org.aikrai.vertx.config.Config
import org.aikrai.vertx.config.DefaultScope
import org.aikrai.vertx.db.tx.TxMgrHolder.initTxMgr

object InjectConfig {
  suspend fun configure(vertx: Vertx): Injector {
    Config.init(vertx)
    return Guice.createInjector(InjectorModule(vertx))
  }
}

class InjectorModule(
  private val vertx: Vertx,
) : AbstractModule() {
  override fun configure() {
    val pool = getDbPool().also { initTxMgr(it) }
    val coroutineScope = DefaultScope(vertx)

    for ((key, value) in Config.getConfigMap()) {
      bind(String::class.java).annotatedWith(Names.named(key)).toInstance(value.toString())
    }
    bind(Vertx::class.java).toInstance(vertx)
    bind(CoroutineScope::class.java).toInstance(coroutineScope)
    bind(HttpServer::class.java).toInstance(vertx.createHttpServer(HttpServerOptions()))
    bind(Snowflake::class.java).toInstance(IdUtil.getSnowflake())
    bind(JWTAuth::class.java).toProvider(JWTAuthProvider::class.java).`in`(Singleton::class.java)

    // 绑定 DbPool 为单例
    bind(Pool::class.java).toInstance(pool)
    bind(SqlClient::class.java).toInstance(pool)
  }

  private fun getDbPool(): Pool {
//    val type = configMap["databases.type"].toString()
//    val name = configMap["databases.name"].toString()
//    val host = configMap["databases.host"].toString()
//    val port = configMap["databases.port"].toString()
//    val user = configMap["databases.username"].toString()
//    val password = configMap["databases.password"].toString()
//    val dbMap = Config.getKey("databases") as Map<String, String>
    val type = Config.getKey("databases.type").toString()
    val name = Config.getKey("databases.name").toString()
    val host = Config.getKey("databases.host").toString()
    val port = Config.getKey("databases.port").toString()
    val user = Config.getKey("databases.username").toString()
    val password = Config.getKey("databases.password").toString()

    val poolOptions = PoolOptions().setMaxSize(10)
    val pool = when (type.lowercase()) {
      "mysql" -> {
        val clientOptions = MySQLConnectOptions()
          .setHost(host)
          .setPort(port.toInt())
          .setDatabase(name)
          .setUser(user)
          .setPassword(password)
          .setTcpKeepAlive(true)
        MySQLBuilder.pool().connectingTo(clientOptions).with(poolOptions).using(vertx).build()
      }
      "postgre", "postgresql" -> {
        val clientOptions = PgConnectOptions()
          .setHost(host)
          .setPort(port.toInt())
          .setDatabase(name)
          .setUser(user)
          .setPassword(password)
          .setTcpKeepAlive(true)
        PgBuilder.pool().connectingTo(clientOptions).with(poolOptions).using(vertx).build()
      }
      else -> throw IllegalArgumentException("Unsupported database type: $type")
    }
    return pool
  }
}
