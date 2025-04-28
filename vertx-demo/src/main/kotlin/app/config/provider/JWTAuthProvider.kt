package app.config.provider

import com.google.inject.Inject
import com.google.inject.Provider
import io.vertx.core.Vertx
import io.vertx.ext.auth.PubSecKeyOptions
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.auth.jwt.JWTAuthOptions
import org.aikrai.vertx.config.JwtConfig

class JWTAuthProvider @Inject constructor(
  private val vertx: Vertx,
  private val jwtConfig: JwtConfig
) : Provider<JWTAuth> {
  override fun get(): JWTAuth {
    val options = JWTAuthOptions()
      .addPubSecKey(
        PubSecKeyOptions()
          .setAlgorithm(jwtConfig.algorithm)
          .setBuffer(jwtConfig.key)
      )
    return JWTAuth.create(vertx, options)
  }
}