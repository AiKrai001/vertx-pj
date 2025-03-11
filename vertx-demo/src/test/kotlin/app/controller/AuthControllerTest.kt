package app.controller

import app.config.InjectConfig
import app.domain.account.LoginDTO
import app.verticle.MainVerticle
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import kotlinx.coroutines.runBlocking
import org.aikrai.vertx.config.Config
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * AuthControllerTest
 */
@ExtendWith(VertxExtension::class)
class AuthControllerTest {
  private var port = 8080
  private var basePath = "/api"

  /**
   * Test case for doSign
   */
  @Test
  fun doSign(vertx: Vertx, testContext: VertxTestContext) {
    val client = WebClient.create(vertx)
    val loginDTO = LoginDTO("运若汐", "123456")
    client.post(port, "127.0.0.1", "$basePath/auth/doSign")
      .sendJson(loginDTO)
      .onSuccess { response ->
        val body = JsonObject(response.body())
        assertEquals("Success", body.getString("message"))
        testContext.completeNow()
      }
      .onFailure { error ->
        testContext.failNow(error)
      }
  }

  /**
   * Test case for doLogin
   */
  @Test
  fun doLogin(vertx: Vertx, testContext: VertxTestContext) {
    val client = WebClient.create(vertx)
    val loginDTO = LoginDTO("运若汐", "123456")
    client.post(port, "127.0.0.1", "$basePath/auth/doLogin")
      .sendJson(loginDTO)
      .onSuccess { response ->
        val body = JsonObject(response.body())
        assertEquals("Success", body.getString("message"))
        testContext.completeNow()
      }
      .onFailure { error ->
        testContext.failNow(error)
      }
  }

  @BeforeEach
  fun startServer(vertx: Vertx, testContext: VertxTestContext) {
    runBlocking { Config.init(vertx) }
    val getIt = InjectConfig.configure(vertx)
    val mainVerticle = getIt.getInstance(MainVerticle::class.java)
    vertx.deployVerticle(mainVerticle).onComplete { ar ->
      if (ar.succeeded()) {
        Config.getKey("server.port")?.let {
          port = it.toString().toInt()
        }
        Config.getKey("server.context")?.let {
          basePath = "/$it".replace("//", "/")
        }
        vertx.setTimer(5000) { testContext.completeNow() }
      } else {
        testContext.failNow(ar.cause())
      }
    }
  }
}
