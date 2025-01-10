package app.config.auth

import com.google.inject.Inject
import com.google.inject.Provider
import com.google.inject.name.Named
import io.vertx.core.Vertx
import io.vertx.ext.auth.PubSecKeyOptions
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.auth.jwt.JWTAuthOptions

class JWTAuthProvider @Inject constructor(
  private val vertx: Vertx,
  @Named("jwt.key") private val key: String
) : Provider<JWTAuth> {
  override fun get(): JWTAuth {
    val options = JWTAuthOptions()
      .addPubSecKey(
        PubSecKeyOptions()
          .setAlgorithm("HS256")
          .setBuffer(key)
      )
    return JWTAuth.create(vertx, options)
  }
}
