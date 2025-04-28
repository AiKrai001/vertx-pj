package app.verticle

import app.config.handler.JwtAuthHandler
import app.config.handler.ResponseHandler
import app.port.aipfox.ApifoxClient
import com.google.inject.Inject
import com.google.inject.Injector
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.CorsHandler
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.coAwait
import kotlinx.coroutines.CoroutineScope
import mu.KotlinLogging
import org.aikrai.vertx.config.ServerConfig
import org.aikrai.vertx.context.RouterBuilder
import org.aikrai.vertx.http.GlobalErrorHandler
import org.aikrai.vertx.http.RequestLogHandler

class WebVerticle @Inject constructor(
  private val getIt: Injector,
  private val serverConfig: ServerConfig,
  private val coroutineScope: CoroutineScope,
  private val jwtAuthHandler: JwtAuthHandler,
  private val requestLogHandler: RequestLogHandler,
  private val responseHandler: ResponseHandler,
  private val globalErrorHandler: GlobalErrorHandler,
  private val apiFoxClient: ApifoxClient,
  ) : CoroutineVerticle() {
  private val logger = KotlinLogging.logger { }

  override suspend fun start() {
    val rootRouter = Router.router(vertx)
    val router = Router.router(vertx)
    setupRouter(rootRouter, router)
    val options = HttpServerOptions()
      .setMaxFormAttributeSize(1024 * 1024)
    val server = vertx.createHttpServer(options)
      .requestHandler(rootRouter)
      .listen(serverConfig.port)
      .coAwait()
    // 生成ApiFox接口
    apiFoxClient.importOpenapi()
    logger.info { "HTTP服务启动 - http://127.0.0.1:${server.actualPort()}${serverConfig.context}" }
  }

  override suspend fun stop() {
  }

  private fun setupRouter(rootRouter: Router, router: Router) {
    rootRouter.route("${serverConfig.context}*").subRouter(router)

    router.route()
      .handler(corsHandler)
      .handler(BodyHandler.create())
      .handler(jwtAuthHandler)
      .handler(requestLogHandler)
      .failureHandler(globalErrorHandler)

    val routerBuilder = RouterBuilder(coroutineScope, router, serverConfig.scanPackage, responseHandler)
      .build{ getIt.getInstance(it) }

    jwtAuthHandler.anonymous.addAll(routerBuilder.anonymousPaths)
  }

  private val corsHandler = CorsHandler.create()
    .addOrigin("*")
    .allowedMethod(HttpMethod.GET)
    .allowedMethod(HttpMethod.POST)
    .allowedMethod(HttpMethod.PUT)
    .allowedMethod(HttpMethod.DELETE)
    .allowedMethod(HttpMethod.OPTIONS)
}
