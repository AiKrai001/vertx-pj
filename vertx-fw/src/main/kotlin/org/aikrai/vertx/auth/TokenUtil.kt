package org.aikrai.vertx.auth

import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.JWTOptions
import io.vertx.ext.auth.User
import io.vertx.ext.auth.authentication.TokenCredentials
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.kotlin.coroutines.coAwait

class TokenUtil {
  companion object {
    fun genToken(jwtAuth: JWTAuth, info: Map<String, Any>): String {
      val jwtOptions = JWTOptions().setExpiresInSeconds(60 * 60 * 24 * 7)
      return jwtAuth.generateToken(JsonObject(info), jwtOptions)
    }

    suspend fun authenticate(jwtAuth: JWTAuth, token: String): User? {
      val tokenCredentials = TokenCredentials(token)
      return jwtAuth.authenticate(tokenCredentials).coAwait() ?: return null
    }
  }
}
