package app.config.db

import com.google.inject.Inject
import com.google.inject.Provider
import com.google.inject.Singleton
import io.vertx.core.Vertx
import io.vertx.pgclient.PgBuilder
import io.vertx.pgclient.PgConnectOptions
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.PoolOptions
import org.aikrai.vertx.config.DatabaseConfig
import org.aikrai.vertx.db.tx.TxMgrHolder

/**
 * 数据库连接池提供者
 */
@Singleton
class DbPoolProvider @Inject constructor(
  private val vertx: Vertx,
  private val dbConfig: DatabaseConfig
) : Provider<Pool> {
  override fun get(): Pool {
    val poolOptions = PoolOptions().setMaxSize(dbConfig.maxPoolSize)
    val clientOptions = PgConnectOptions()
      .setHost(dbConfig.host)
      .setPort(dbConfig.port)
      .setDatabase(dbConfig.name)
      .setUser(dbConfig.username)
      .setPassword(dbConfig.password)
      .setTcpKeepAlive(true)

    val pool = PgBuilder.pool()
      .connectingTo(clientOptions)
      .with(poolOptions)
      .using(vertx)
      .build()

    // 初始化事务管理器
    TxMgrHolder.initTxMgr(pool)

    return pool
  }
}