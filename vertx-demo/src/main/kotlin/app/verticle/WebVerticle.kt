package app.verticle

import app.config.RespBean
import app.config.auth.JwtAuthenticationHandler
import app.config.auth.ResponseHandler
import app.config.auth.TokenService
import app.data.domain.account.Account
import app.port.aipfox.ApifoxClient
import cn.hutool.core.lang.Snowflake
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
import org.aikrai.vertx.auth.AuthUser
import org.aikrai.vertx.config.Config
import org.aikrai.vertx.context.RouterBuilder
import org.aikrai.vertx.jackson.JsonUtil
import org.aikrai.vertx.utlis.LangUtil.toStringMap
import org.aikrai.vertx.utlis.Meta

class WebVerticle @Inject constructor(
  private val getIt: Injector,
  private val coroutineScope: CoroutineScope,
  private val tokenService: TokenService,
  private val apifoxClient: ApifoxClient,
  private val snowflake: Snowflake,
  private val responseHandler: ResponseHandler,
  @Named("server.port") private val port: Int,
  @Named("server.context") private val context: String,
) : CoroutineVerticle() {
  private val logger = KotlinLogging.logger { }

  override suspend fun start() {
    val rootRouter = Router.router(vertx)
    val router = Router.router(vertx)
    setupRouter(rootRouter, router)
    val options = HttpServerOptions().setMaxFormAttributeSize(1024 * 1024)
    val server = vertx.createHttpServer(options)
      .requestHandler(rootRouter)
      .listen(port)
      .coAwait()

    apifoxClient.importOpenapi()

    logger.info { "http server start - http://127.0.0.1:${server.actualPort()}/$context" }
  }

  override suspend fun stop() {
  }

  private fun setupRouter(rootRouter: Router, router: Router) {
    rootRouter.route("/api" + "*").subRouter(router)
    router.route()
      .handler(corsHandler)
      .handler(BodyHandler.create())
      .handler(logHandler)
      .failureHandler(errorHandler)

    val authHandler = JwtAuthenticationHandler(coroutineScope, tokenService, context, snowflake)
    router.route("/*").handler(authHandler)

    val scanPath = Config.getKeyAsString("server.package")
    val routerBuilder = RouterBuilder(coroutineScope, router, scanPath, responseHandler).build { service ->
      getIt.getInstance(service)
    }
    authHandler.anonymous.addAll(routerBuilder.anonymousPaths)
  }

  private val corsHandler = CorsHandler.create()
    .addOrigin("*")
    .allowedMethod(HttpMethod.GET)
    .allowedMethod(HttpMethod.POST)
    .allowedMethod(HttpMethod.PUT)
    .allowedMethod(HttpMethod.DELETE)
    .allowedMethod(HttpMethod.OPTIONS)

  // 非业务异常处理
  private val errorHandler = Handler<RoutingContext> { ctx ->
    val failure = ctx.failure()
    if (failure != null) {
      logger.error { "${ctx.request().uri()}: ${failure.stackTraceToString()}" }
      val resObj = when (failure) {
        is Meta -> RespBean.failure(ctx.statusCode(), "${failure.name}:${failure.message}", failure.data)
        else -> RespBean.failure("${failure.javaClass.simpleName}${if (failure.message != null) ":${failure.message}" else ""}")
      }
      val resStr = JsonUtil.toJsonStr(resObj)
      ctx.put("responseData", resStr)
      ctx.response()
        .setStatusCode(ctx.statusCode())
        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
        .end(resStr)
    } else {
      logger.error("${ctx.request().uri()}: 未知错误")
      val resObj = RespBean.failure("未知错误")
      val resStr = JsonUtil.toJsonStr(resObj)
      ctx.put("responseData", resStr)
      ctx.response()
        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
        .setStatusCode(500)
        .end(resStr)
    }
  }

  private val logHandler = Handler<RoutingContext> { ctx ->
    val start = System.currentTimeMillis()
    ctx.response().endHandler {
      val end = System.currentTimeMillis()
      val timeCost = "${end - start}ms".let {
        when (end - start) {
          in 0..500 -> it
          in 501..2000 -> "$it⚠️"
          else -> "$it❌"
        }
      }
      val authUser = ctx.user() as? AuthUser
      val logContent = if (authUser != null) {
        val user = JsonUtil.parseObject(authUser.user, Account::class.java)
        """
        |
        |>>>>>请求ID:[${ctx.get<String>("requestId")}]
        |>>>>>请求URL:[${ctx.request().path()}](${ctx.request().method()})
        |>>>>>请求IP:[${ctx.request().remoteAddress().host()}]
        |>>>>>用户名:[${user.userName}]
        |>>>>>用户ID:[${user.userId}]
        |>>>>>角色:[${authUser.roles}]
        |>>>>>请求参数:[${JsonUtil.toJsonStr(ctx.request().params().toStringMap())}]
        |>>>>>请求体:[${JsonUtil.toJsonStr(ctx.body().asString())}]
        |>>>>>响应结果:[${ctx.get<String>("responseData")}]
        |>>>>>耗时:[$timeCost]
        """.trimMargin()
      } else {
        """
        |
        |>>>>>请求ID:[${ctx.get<String>("requestId")}]
        |>>>>>请求URL:["${ctx.request().uri()}"](${ctx.request().method()})
        |>>>>>请求IP:[${ctx.request().remoteAddress().host()}]
        |>>>>>身份:[未验证]
        |>>>>>请求参数:[${JsonUtil.toJsonStr(ctx.request().params().toStringMap())}]
        |>>>>>请求体:[${JsonUtil.toJsonStr(ctx.body().asString())}]
        |>>>>>响应结果:[${ctx.get<String>("responseData")}]
        |>>>>>耗时:[$timeCost]
        """.trimMargin()
      }
      logger.info(logContent)
    }
    ctx.next()
  }
}
