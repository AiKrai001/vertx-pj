package app.verticle

import app.config.auth.AuthHandler
import app.config.auth.JwtAuthenticationHandler
import com.google.inject.Inject
import com.google.inject.Injector
import com.google.inject.name.Named
import io.vertx.core.Handler
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.CorsHandler
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.coAwait
import kotlinx.coroutines.CoroutineScope
import mu.KotlinLogging
import org.aikrai.vertx.config.FailureParser
import org.aikrai.vertx.context.RouterBuilder
import org.aikrai.vertx.jackson.JsonUtil

class WebVerticle @Inject constructor(
  private val getIt: Injector,
  private val coroutineScope: CoroutineScope,
  private val authHandler: AuthHandler,
  @Named("server.name") private val serverName: String,
  @Named("server.port") private val port: String,
  @Named("server.context") private val context: String
) : CoroutineVerticle() {
  private val logger = KotlinLogging.logger { }

  override suspend fun start() {
    val rootRouter = Router.router(vertx)
    val router = Router.router(vertx)
    setupRouter(rootRouter, router)

    val options = HttpServerOptions().setMaxFormAttributeSize(1024 * 1024)
    val server = vertx.createHttpServer(options)
      .requestHandler(rootRouter)
      .listen(port.toInt())
      .coAwait()

    logger.info { "http server start - http://127.0.0.1:${server.actualPort()}" }
  }

  override suspend fun stop() {
  }

  private fun setupRouter(rootRouter: Router, router: Router) {
    rootRouter.route("/api" + "*").subRouter(router)
    router.route()
      .handler(corsHandler)
      .failureHandler(errorHandler)
      .handler(BodyHandler.create())

    val authHandler = JwtAuthenticationHandler(coroutineScope, authHandler, context)
    router.route("/*").handler(authHandler)

    val routerBuilder = RouterBuilder(coroutineScope, router).build { service ->
      getIt.getInstance(service)
    }
    authHandler.exclude.addAll(routerBuilder.anonymousPaths)
    // 生成 openapi.json
    /*val openApiJsonStr = OpenApiSpecGenerator().genOpenApiSpecStr(serverName, "1.0", "http://127.0.0.1:$port/api")
    val resourcesPath = "${System.getProperty("user.dir")}/src/main/resources"
    val timestamp = System.currentTimeMillis()
    vertx.fileSystem()
      .writeFile(
        "$resourcesPath/openapi/openapi-$timestamp.json",
        Buffer.buffer(openApiJsonStr)
      ) { writeFileAsyncResult ->
        if (!writeFileAsyncResult.succeeded()) writeFileAsyncResult.cause().printStackTrace()
      }*/
  }

  private val corsHandler = CorsHandler.create()
    .addOrigin("*")
    .allowedMethod(HttpMethod.GET)
    .allowedMethod(HttpMethod.POST)
    .allowedMethod(HttpMethod.PUT)
    .allowedMethod(HttpMethod.DELETE)
    .allowedMethod(HttpMethod.OPTIONS)

  private val errorHandler = Handler<RoutingContext> { ctx ->
    val failure = ctx.failure()
    if (failure != null) {
      logger.error { "${ctx.request().uri()}: ${failure.stackTraceToString()}" }

      val parsedFailure = FailureParser.parse(ctx.statusCode(), failure)
      val response = ctx.response()

      response.putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
      response.statusCode = if (ctx.statusCode() != 200) ctx.statusCode() else 500
      response.end(JsonUtil.toJsonStr(parsedFailure.response))
    } else {
      logger.error("${ctx.request().uri()}: 未知错误")
      val response = ctx.response()
      response.putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
      response.statusCode = 500
      response.end()
    }
  }
}
