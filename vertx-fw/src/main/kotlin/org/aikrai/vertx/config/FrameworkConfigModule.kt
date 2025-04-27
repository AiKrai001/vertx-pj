package org.aikrai.vertx.config

import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.Singleton

/**
 * 框架配置模块
 * 
 * 负责使用增强后的Config对象读取配置，并将其实例化为数据类进行绑定
 */
class FrameworkConfigModule : AbstractModule() {

    override fun configure() {
        // 这里不需要bind(Config::class.java)，因为Config是object
    }

    @Provides
    @Singleton
    fun provideDatabaseConfig(): DatabaseConfig {
        return DatabaseConfig(
            name = Config.getString("databases.name", "default_db"),
            host = Config.getString("databases.host", "localhost"),
            port = Config.getInt("databases.port", 5432),
            username = Config.getString("databases.username", "user"),
            password = Config.getString("databases.password", "password"),
            maxPoolSize = Config.getInt("databases.maxPoolSize", 10)
        )
    }

    @Provides
    @Singleton
    fun provideRedisConfig(): RedisConfig {
        return RedisConfig(
            host = Config.getString("redis.host", "localhost"),
            port = Config.getInt("redis.port", 6379),
            db = Config.getInt("redis.database", 0),
            pass = Config.getStringOrNull("redis.password"),
            poolSize = Config.getInt("redis.maxPoolSize", 8),
            maxPoolWaiting = Config.getInt("redis.maxPoolWaiting", 32)
        )
    }

    @Provides
    @Singleton
    fun provideJwtConfig(): JwtConfig {
        val key = Config.getStringOrNull("jwt.key")
            ?: throw IllegalStateException("缺少必要配置: jwt.key")
        return JwtConfig(
            key = key,
            algorithm = Config.getString("jwt.algorithm", "HS256"),
            expiresInSeconds = Config.getInt("jwt.expiresInSeconds", 60 * 60 * 24 * 7)
        )
    }

    @Provides
    @Singleton
    fun provideServerConfig(): ServerConfig {
        val scanPackage = Config.getStringOrNull("server.package")
            ?: throw IllegalStateException("缺少必要配置: server.package")
        return ServerConfig(
            port = Config.getInt("server.port", 8080),
            context = Config.getString("server.context", "/api"),
            scanPackage = scanPackage
        )
    }

    @Provides
    @Singleton
    fun provideFrameworkConfiguration(
        server: ServerConfig,
        database: DatabaseConfig,
        redis: RedisConfig,
        jwt: JwtConfig
    ): FrameworkConfiguration {
        return FrameworkConfiguration(server, database, redis, jwt)
    }
} 