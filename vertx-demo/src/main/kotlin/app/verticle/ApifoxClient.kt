package app.verticle

import com.google.inject.Inject
import com.google.inject.name.Named
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.kotlin.coroutines.CoroutineVerticle
import mu.KotlinLogging
import org.aikrai.vertx.openapi.OpenApiSpecGenerator

class ApifoxClient @Inject constructor(
  private val vertx: Vertx,
  @Named("apifox.token") private val token: String,
  @Named("apifox.projectId") private val projectId: String,
  @Named("apifox.folderId") private val folderId: String,
  @Named("server.name") private val serverName: String,
  @Named("server.port") private val port: String
) : CoroutineVerticle() {
  private val logger = KotlinLogging.logger { }

  override suspend fun start() {
    importOpenapi()
  }

  private fun importOpenapi() {
    val openApiJsonStr = OpenApiSpecGenerator().genOpenApiSpecStr(serverName, "1.0", "http://127.0.0.1:$port/api")
    val options = WebClientOptions().setDefaultPort(443).setDefaultHost("api.apifox.com").setSsl(true)
    val client = WebClient.create(vertx, options)
    // 参数参考 https://apifox-openapi.apifox.cn/api-173409873
    val reqOptions = JsonObject()
      .put("targetEndpointFolderId", folderId)
      .put("endpointOverwriteBehavior", "OVERWRITE_EXISTING")
      .put("schemaOverwriteBehavior", "OVERWRITE_EXISTING")
      .put("updateFolderOfChangedEndpoints", false)
      .put("prependBasePath", false)
    val requestBody = JsonObject().put("input", openApiJsonStr).put("options", reqOptions)

    client.request(HttpMethod.POST, "/v1/projects/$projectId/import-openapi")
      .putHeader("X-Apifox-Version", "2024-01-20")
      .putHeader("Authorization", "Bearer $token")
      .sendJsonObject(requestBody) { ar ->
        if (ar.succeeded()) {
          val response = ar.result()
          logger.info("Received response with status code: ${response.statusCode()}")
          logger.info("Response body: ${response.bodyAsString()}")
        } else {
          logger.warn("Request failed: ${ar.cause().message}")
        }
      }
  }
}
