package org.aikrai.vertx.openapi

import cn.hutool.core.util.StrUtil
import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.servers.Server
import mu.KotlinLogging
import org.aikrai.vertx.context.Controller
import org.aikrai.vertx.context.CustomizeRequest
import org.aikrai.vertx.context.D
import org.aikrai.vertx.utlis.ClassUtil
import org.reflections.Reflections
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.sql.Date
import java.sql.Time
import java.sql.Timestamp
import java.time.*
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.kotlinFunction

/**
 * OpenAPI 规范生成器，用于生成 Vertx 路由的 OpenAPI 文档
 */
class OpenApiSpecGenerator {
  private val logger = KotlinLogging.logger { }

  // 用于跟踪已处理的类型
  private val processedTypes = mutableSetOf<Class<*>>()

  companion object {
    /**
     * 基础类型到 OpenAPI 类型的映射关系
     */
    private val PRIMITIVE_TYPE_MAPPING = mapOf(
      // java.lang classes
      String::class.java to "string",
      Int::class.java to "integer",
      Integer::class.java to "integer",
      Long::class.java to "number",
      java.lang.Long::class.java to "number",
      Double::class.java to "number",
      java.lang.Double::class.java to "number",
      Boolean::class.java to "boolean",
      java.lang.Boolean::class.java to "boolean",
      List::class.java to "array",
      Array::class.java to "array",
      Collection::class.java to "array",
      // java.time classes
      Instant::class.java to "number",
      LocalDate::class.java to "string",
      LocalTime::class.java to "string",
      LocalDateTime::class.java to "string",
      ZonedDateTime::class.java to "number",
      OffsetDateTime::class.java to "number",
      Duration::class.java to "number",
      Period::class.java to "string",
      // java.sql classes
      Date::class.java to "string",
      Time::class.java to "number",
      Timestamp::class.java to "number",
    )
  }

  /**
   * 生成 OpenAPI 规范的 JSON 字符串
   *
   * @param title API 文档标题
   * @param version API 版本号
   * @param serverUrl 服务器 URL
   * @return 格式化后的 OpenAPI 规范 JSON 字符串
   */
  fun genOpenApiSpecStr(title: String, version: String, serverUrl: String): String {
    return Json.pretty(generateOpenApiSpec(title, version, serverUrl))
  }

  /**
   * 生成 OpenAPI 规范对象
   *
   * @param title API 文档标题
   * @param version API 版本号
   * @param serverUrl 服务器 URL
   * @return OpenAPI 规范对象
   */
  private fun generateOpenApiSpec(title: String, version: String, serverUrl: String): OpenAPI {
    logger.info("Generating OpenAPI Specification for Vertx routes")
    return OpenAPI().apply {
      info = Info().apply {
        this.title = title
        this.version = version
      }
      servers = listOf(Server().apply { url = serverUrl })
      paths = generatePaths()
    }
  }

  /**
   * 生成 API 路径信息
   *
   * @return Paths 对象，包含所有 API 路径的定义
   */
  private fun generatePaths(): Paths {
    val paths = Paths()
    // 获取所有带有 @Controller 注解的类
    val packageName = ClassUtil.getMainClass()?.packageName
    val controllerClassSet = Reflections(packageName).getTypesAnnotatedWith(Controller::class.java)
    ClassUtil.getPublicMethods(controllerClassSet).forEach { (controllerClass, methods) ->
      val controllerInfo = extractControllerInfo(controllerClass)
      methods.forEach { method ->
        val pathInfo = generatePathInfo(method, controllerInfo)
        paths.addPathItem(pathInfo.path, pathInfo.pathItem)
      }
    }
    return paths
  }

  /**
   * 从控制器类中提取控制器相关信息
   *
   * @param controllerClass 控制器类
   * @return 包含控制器名称、前缀和标签的 ControllerInfo 对象
   */
  private fun extractControllerInfo(controllerClass: Class<*>): ControllerInfo {
    val controllerAnnotation = controllerClass.getAnnotation(Controller::class.java)
    val dAnnotation = controllerClass.getAnnotation(D::class.java)
    val controllerName = controllerClass.simpleName.removeSuffix("Controller")

    return ControllerInfo(
      name = controllerName,
      prefix = controllerAnnotation?.prefix?.takeIf { it.isNotBlank() } ?: controllerName,
      tag = "${dAnnotation?.name ?: ""}  $controllerName"
    )
  }

  /**
   * 生成路径信息
   *
   * @param method 方法对象
   * @param controllerInfo 控制器信息
   * @return 包含路径和路径项的 PathInfo 对象
   */
  private fun generatePathInfo(method: Method, controllerInfo: ControllerInfo): PathInfo {
    val methodDAnnotation = method.getAnnotation(D::class.java)
    val path = buildPath(controllerInfo.prefix, method.name)

    val operation = Operation().apply {
      operationId = method.name
      summary = "${methodDAnnotation?.name ?: ""}  $path"
      description = methodDAnnotation?.caption ?: summary

      // 分离请求体参数和普通参数
      val allParameters = getParameters(method)
      val (bodyParams, queryParams) = allParameters.partition { it.`in` == "body" }
      // 设置查询参数
      parameters = queryParams
      // 设置请求体
      if (bodyParams.isNotEmpty()) {
        requestBody = RequestBody().apply {
          content = bodyParams.first().content
          required = bodyParams.first().required
          description = bodyParams.first().description
        }
      }
      responses = generateResponsesFromReturnType(method)
      deprecated = false
      security = listOf()
      tags = listOf(controllerInfo.tag)
    }

    val pathItem = PathItem().apply {
      operation(getHttpMethod(method), operation)
    }

    return PathInfo(path, pathItem)
  }

  /**
   * 构建 API 路径
   *
   * @param controllerPrefix 控制器前缀
   * @param methodName 方法名
   * @return 格式化后的 API 路径
   */
  private fun buildPath(controllerPrefix: String, methodName: String): String {
    return (
      "/${StrUtil.lowerFirst(StrUtil.toCamelCase(controllerPrefix))}/" +
        StrUtil.lowerFirst(StrUtil.toCamelCase(methodName))
      ).replace("//", "/")
  }

  /**
   * 根据方法返回值类型生成响应对象
   *
   * @param method 方法对象
   * @return ApiResponses 对象
   */
  private fun generateResponsesFromReturnType(method: Method): ApiResponses {
    val returnType = method.kotlinFunction?.returnType?.javaType
    val schema = when (returnType) {
      // 处理泛型返回类型
      is ParameterizedType -> {
        val rawType = returnType.rawType as Class<*>
        val typeArguments = returnType.actualTypeArguments
        when {
          // 处理集合类型
          Collection::class.java.isAssignableFrom(rawType) -> {
            Schema<Any>().apply {
              type = "array"
              items = generateSchema(
                typeArguments[0].let {
                  when (it) {
                    is Class<*> -> it
                    is ParameterizedType -> it.rawType as Class<*>
                    else -> Any::class.java
                  }
                }
              )
            }
          }
          // 可以添加其他泛型类型的处理
          else -> generateSchema(rawType)
        }
      }
      // 处理普通类型
      is Class<*> -> generateSchema(returnType)
      else -> Schema<Any>().type("object")
    }

    return ApiResponses().addApiResponse(
      "200",
      ApiResponse().apply {
        description = "OK"
        content = Content().addMediaType(
          "application/json",
          MediaType().schema(schema)
        )
        headers = mapOf()
      }
    )
  }

  /**
   * 获取方法的参数列表
   *
   * @param method 方法对象
   * @return OpenAPI 参数列表
   */
  private fun getParameters(method: Method): List<Parameter> {
    return method.kotlinFunction?.parameters
      ?.mapNotNull { parameter -> generateParameter(parameter) }
      ?: emptyList()
  }

  /**
   * 生成 OpenAPI 参数对象
   *
   * @param parameter Kotlin 参数对象
   * @return OpenAPI 参数对象，如果无法生成则返回 null
   */
  private fun generateParameter(parameter: KParameter): Parameter? {
    if (parameter.kind == KParameter.Kind.INSTANCE) return null
    val type =
      (parameter.type.javaType as? Class<*>) ?: (parameter.type.javaType as? ParameterizedType)?.rawType as? Class<*>
        ?: return null

    val paramName = parameter.name ?: return null
    val annotation = parameter.annotations.filterIsInstance<D>().firstOrNull()

    // 检查是否为自定义数据类
    if (!parameter.type.classifier.toString().startsWith("class kotlin.") &&
      !(parameter.type.javaType as Class<*>).isEnum &&
      parameter.type.javaType is Class<*>
    ) {
      // 创建请求体参数
      return Parameter().apply {
        name = annotation?.name?.takeIf { it.isNotBlank() } ?: paramName
        required = !parameter.type.isMarkedNullable
        description = annotation?.caption?.takeIf { it.isNotBlank() } ?: name
        `in` = "body"
        schema = generateSchema(type)
        content = Content().addMediaType(
          "application/json",
          MediaType().schema(schema)
        )
      }
    }

    // 处理普通参数
    return Parameter().apply {
      name = annotation?.name?.takeIf { it.isNotBlank() } ?: paramName
      required = !parameter.type.isMarkedNullable
      description = annotation?.caption?.takeIf { it.isNotBlank() } ?: name
      allowEmptyValue = parameter.type.isMarkedNullable
      `in` = "query"
      schema = generateSchema(type)
    }
  }

  /**
   * 生成 OpenAPI Schema 对象
   *
   * @param type 参数类型
   * @return OpenAPI Schema 对象
   */
  private fun generateSchema(type: Class<*>): Schema<Any> {
    // 如果该类型已经处理过，则返回一个空的 Schema，避免循环引用
    if (processedTypes.contains(type)) {
      return Schema<Any>().apply {
        this.type = "object"
        description = "Circular reference detected"
      }
    }
    // 将当前类型添加到已处理集合中
    processedTypes.add(type)
    return when {
      // 处理基础类型
      PRIMITIVE_TYPE_MAPPING.containsKey(type) -> Schema<Any>().apply {
        this.type = PRIMITIVE_TYPE_MAPPING[type]
        deprecated = false
      }
      // 处理枚举类型
      type.isEnum -> Schema<Any>().apply {
        this.type = "string"
        enum = type.enumConstants?.map { it.toString() }
      }
      type.name.startsWith("java.lang") || type.name.startsWith("java.time") || type.name.startsWith("java.sql") -> Schema<Any>().apply {
        this.type = type.simpleName.lowercase()
        deprecated = false
      }
      type.name.startsWith("java") -> Schema<Any>().apply {
        this.type = type.simpleName.lowercase()
        deprecated = false
      }
      // 处理自定义对象
      else -> Schema<Any>().apply {
        this.type = "object"
        properties = type.declaredFields
          .filter { !it.isSynthetic }
          .associate { field ->
            field.isAccessible = true
            field.name to generateSchema(field.type)
          }
      }
    }.also {
      // 处理完后，从已处理集合中移除当前类型
      processedTypes.remove(type)
    }
  }

  /**
   * 生成默认的 API 响应对象
   *
   * @return 包含默认响应的 ApiResponses 对象
   */
  private fun generateDefaultResponses(): ApiResponses {
    return ApiResponses().addApiResponse(
      "200",
      ApiResponse().apply {
        description = "OK"
        content = Content().addMediaType(
          "*/*",
          MediaType().schema(Schema<Any>().type("object"))
        )
        headers = mapOf()
      }
    )
  }

  /**
   * 获取方法对应的 HTTP 方法
   *
   * @param method 方法对象
   * @return PathItem.HttpMethod 枚举值
   */
  private fun getHttpMethod(method: Method): PathItem.HttpMethod {
    val api = method.getAnnotation(CustomizeRequest::class.java)
    return if (api != null) {
      when (api.method.uppercase()) {
        "GET" -> PathItem.HttpMethod.GET
        "PUT" -> PathItem.HttpMethod.PUT
        "DELETE" -> PathItem.HttpMethod.DELETE
        "PATCH" -> PathItem.HttpMethod.PATCH
        else -> PathItem.HttpMethod.POST
      }
    } else {
      PathItem.HttpMethod.POST
    }
  }

  /**
   * 控制器信息数据类
   *
   * @property name 控制器名称
   * @property prefix 控制器路径前缀
   * @property tag API 文档标签
   */
  private data class ControllerInfo(
    val name: String,
    val prefix: String,
    val tag: String
  )

  /**
   * 路径信息数据类
   *
   * @property path API 路径
   * @property pathItem OpenAPI 路径项对象
   */
  private data class PathInfo(
    val path: String,
    val pathItem: PathItem
  )
}
