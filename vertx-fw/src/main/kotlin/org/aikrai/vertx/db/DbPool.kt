package org.aikrai.vertx.db

import com.google.inject.Inject
import com.google.inject.name.Named
import io.vertx.core.Vertx
import io.vertx.mysqlclient.MySQLBuilder
import io.vertx.mysqlclient.MySQLConnectOptions
import io.vertx.pgclient.PgBuilder
import io.vertx.pgclient.PgConnectOptions
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.PoolOptions
import io.vertx.sqlclient.SqlClient

class DbPool @Inject constructor(
  vertx: Vertx,
  @Named("databases.type") private val type: String,
  @Named("databases.name") private val name: String,
  @Named("databases.host") private val host: String,
  @Named("databases.port") private val port: String,
  @Named("databases.username") private val user: String,
  @Named("databases.password") private val password: String
) {
  private var pool: Pool

  init {
    val poolOptions = PoolOptions().setMaxSize(10)
    pool = when (type.lowercase()) {
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
  }

  fun getClient(): SqlClient {
    return pool
  }

  fun getPool(): Pool {
    return pool
  }
}
