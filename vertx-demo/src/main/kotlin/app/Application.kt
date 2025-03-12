package app

import app.config.InjectConfig
import app.verticle.MainVerticle
import io.vertx.core.Vertx
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.aikrai.vertx.config.Config

object Application {
  private val logger = KotlinLogging.logger { }

  @JvmStatic
  fun main(args: Array<String>) {
    runBlocking {
      val vertx = Vertx.vertx()
      Config.init(vertx)
      val getIt = InjectConfig.configure(vertx)
      val mainVerticle = getIt.getInstance(MainVerticle::class.java)

      vertx.deployVerticle(mainVerticle).onComplete {
        if (it.failed()) {
          logger.error { "MainVerticle startup failed: ${it.cause()?.stackTraceToString()}" }
        } else {
          logger.info { "MainVerticle startup successfully" }
        }
      }
    }
  }
}
