package org.aikrai.vertx.config

/**
 * 数据库配置
 */
data class DatabaseConfig(
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val maxPoolSize: Int = 10
)

/**
 * Redis配置
 */
data class RedisConfig(
    val host: String,
    val port: Int,
    val db: Int,
    val password: String?,
    val poolSize: Int = 8,
    val maxPoolWaiting: Int = 32
)

/**
 * JWT配置
 */
data class JwtConfig(
    val key: String,
    val algorithm: String = "HS256",
    val expiresInSeconds: Int = 60 * 60 * 24 * 7 // 7天
)

/**
 * 服务器配置
 */
data class ServerConfig(
    val port: Int,
    val context: String,
    val scanPackage: String
)

/**
 * 框架配置
 */
data class FrameworkConfiguration(
    val server: ServerConfig,
    val database: DatabaseConfig,
    val redis: RedisConfig,
    val jwt: JwtConfig
) 