package org.aikrai.vertx.context

import cn.hutool.core.util.StrUtil
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.User
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.aikrai.vertx.auth.*
import org.aikrai.vertx.config.resp.DefaultResponseHandler
import org.aikrai.vertx.config.resp.ResponseHandlerInterface
import org.aikrai.vertx.db.annotation.EnumValue
import org.aikrai.vertx.jackson.JsonUtil
import org.aikrai.vertx.utlis.ClassUtil
import org.aikrai.vertx.utlis.Meta
import org.reflections.Reflections
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import kotlin.collections.ArrayList
import kotlin.coroutines.Continuation
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.javaType

class RouterBuilder(
  private val coroutineScope: CoroutineScope,
  private val router: Router,
  private val scanPath: String? = null,
  private val responseHandler: ResponseHandlerInterface = DefaultResponseHandler()
) {
  var anonymousPaths = ArrayList<String>()

  fun build(getIt: (clazz: Class<*>) -> Any): RouterBuilder {
    // 缓存路由信息
    val routeInfoCache = mutableMapOf<Pair<String, HttpMethod>, RouteInfo>()
    // 获取所有 Controller 类中的公共方法
    val packagePath = scanPath ?: ClassUtil.getMainClass().packageName
    val controllerClassSet = Reflections(packagePath).getTypesAnnotatedWith(Controller::class.java)
    val controllerMethods = ClassUtil.getPublicMethods(controllerClassSet)
    for ((classType, methods) in controllerMethods) {
      val controllerAnnotation = classType.getDeclaredAnnotationsByType(Controller::class.java).firstOrNull()
      val prefixPath = controllerAnnotation?.prefix ?: ""
      val classAllowAnonymous = classType.getAnnotation(AllowAnonymous::class.java) != null
      for (method in methods) {
        val reqPath = getReqPath(prefixPath, classType, method)
        val httpMethod = getHttpMethod(method)
        val allowAnonymous = method.getAnnotation(AllowAnonymous::class.java) != null
        if (classAllowAnonymous || allowAnonymous) anonymousPaths.add(reqPath)
        val customizeResp = method.getAnnotation(CustomizeResponse::class.java) != null
        val role = method.getAnnotation(CheckRole::class.java)
        val permissions = method.getAnnotation(CheckPermission::class.java)
        val kFunction = classType.kotlin.declaredFunctions.find { it.name == method.name }
        if (kFunction != null) {
          val parameterInfo = kFunction.parameters.mapNotNull { parameter ->
            val javaType = parameter.type.javaType
            // 跳过协程的 Continuation 参数
            if (javaType is Class<*> && Continuation::class.java.isAssignableFrom(javaType)) {
              return@mapNotNull null
            }
            parameter.name ?: return@mapNotNull null
            val annotation = parameter.annotations.find { it is D } as? D
            val paramName = annotation?.name?.takeIf { it.isNotBlank() } ?: parameter.name ?: ""
            val typeClass = when (javaType) {
              is Class<*> -> javaType
              is ParameterizedType -> javaType.rawType as? Class<*>
              else -> null
            }
            ParameterInfo(
              name = paramName,
              type = typeClass ?: parameter.type.javaType as Class<*>,
              isNullable = parameter.type.isMarkedNullable,
              isList = parameter.type.classifier == List::class,
              isComplex = !parameter.type.classifier.toString().startsWith("class kotlin.") &&
                  !parameter.type.classifier.toString().startsWith("class io.vertx") &&
                  !(parameter.type.javaType as Class<*>).isEnum &&
                  !parameter.type.javaType.javaClass.isEnum &&
                  parameter.type.javaType is Class<*>,
              isEnum = typeClass?.isEnum ?: (parameter.type.javaType as Class<*>).isEnum
            )
          }
          routeInfoCache[reqPath to httpMethod] =
            RouteInfo(classType, method, kFunction, parameterInfo, customizeResp, role, permissions)
        }
      }
    }

    // 注册路由处理器
    routeInfoCache.forEach { (path, routeInfo) ->
      router.route(routeInfo.httpMethod, path.first).handler { ctx ->
        if (ctx.user() != null) {
          val user = ctx.user() as AuthUser
          if (!user.validateAuth(routeInfo)) {
            ctx.fail(403, Meta.unauthorized("unauthorized"))
            return@handler
          }
        }
        val instance = getIt(routeInfo.classType)
        buildLambda(ctx, instance, routeInfo)
      }
    }
    return this
  }

  private fun buildLambda(ctx: RoutingContext, instance: Any, routeInfo: RouteInfo) {
    coroutineScope.launch {
      try {
        val params = getParamsInstance(ctx, routeInfo.parameterInfo)
        val resObj = if (routeInfo.kFunction.isSuspend) {
          routeInfo.kFunction.callSuspend(instance, *params)
        } else {
          routeInfo.kFunction.call(instance, *params)
        }
        responseHandler.normal(ctx, resObj, routeInfo.customizeResp)
      } catch (e: Throwable) {
        responseHandler.exception(ctx, e)
      }
    }
  }

  companion object {
    private val objectMapper = jacksonObjectMapper()

    private fun AuthUser.validateAuth(routeInfo: RouteInfo): Boolean {
      // 如果没有权限要求，直接返回true
      if (routeInfo.role == null && routeInfo.permissions == null) return true
      // 验证角色
      val hasValidRole = routeInfo.role?.let { role ->
        val roleSet = attributes().getJsonArray("role").toSet() as Set<String>
        if (roleSet.isEmpty()) {
          false
        } else {
          val reqRoleSet = (role.value + role.type).filter { it.isNotBlank() }.toSet()
          validateSet(reqRoleSet, roleSet, role.mode)
        }
      } ?: true

      // 验证权限
      val hasValidPermission = routeInfo.permissions?.let { permissions ->
        val permissionSet = attributes().getJsonArray("permissions").toSet() as Set<String>
        val roleSet = attributes().getJsonArray("role").toSet() as Set<String>
        if (permissionSet.isEmpty() && roleSet.isEmpty()) {
          false
        } else {
          if (permissions.orRole.isNotEmpty()) {
            val roleBoolean = validateSet(permissions.orRole.toSet(), roleSet, Mode.AND)
            if (roleBoolean) return true
          }
          val reqPermissionSet = (permissions.value + permissions.type).filter { it.isNotBlank() }.toSet()
          validateSet(reqPermissionSet, permissionSet, permissions.mode)
        }
      } ?: true
      return hasValidRole && hasValidPermission
    }

    private fun validateSet(
      required: Set<String>,
      actual: Set<String>,
      mode: Mode
    ): Boolean {
      if (required.isEmpty()) return true
      return when (mode) {
        Mode.AND -> required == actual
        Mode.OR -> required.any { it in actual }
      }
    }

    private fun getReqPath(prefix: String, clazz: Class<*>, method: Method): String {
      var classPath = if (prefix.isNotBlank()) {
        StrUtil.toCamelCase(StrUtil.toUnderlineCase(prefix))
      } else {
        StrUtil.toCamelCase(StrUtil.toUnderlineCase(clazz.simpleName.removeSuffix("Controller")))
      }
      if (classPath == "/") classPath = ""
      val methodName = StrUtil.toCamelCase(StrUtil.toUnderlineCase(method.name))
      return "/$classPath/$methodName".replace("//", "/")
    }

    private fun getParamsInstance(ctx: RoutingContext, paramsInfo: List<ParameterInfo>): Array<Any?> {
      val params = mutableListOf<Any?>()
      val formAttributes = ctx.request().formAttributes().associate { it.key to it.value }
      val queryParams = ctx.queryParams().entries().associate { it.key to it.value }
      val combinedParams = formAttributes + queryParams
      // 解析Body
      val bodyObj = if (!ctx.body().isEmpty) ctx.body().asJsonObject() else null
      val bodyMap = bodyObj?.map ?: emptyMap()

      paramsInfo.forEach { param ->
        if (param.isList) {
          var value = ctx.queryParams().getAll(param.name)
          if (value.isEmpty() && bodyMap[param.name] != null) {
            value = (bodyMap[param.name] as Collection<*>).map { it.toString() }.toMutableList()
          }
          if (value.isEmpty() && !param.isNullable) {
            throw IllegalArgumentException("Missing required parameter: ${param.name}")
          }
          params.add(value.ifEmpty { null })
          return@forEach
        }

        if (param.isEnum) {
          val value = sequenceOf(
            combinedParams[param.name],
            bodyMap[param.name]
          ).filterNotNull().map { it.toString() }.firstOrNull()

          val enumValueMethod = param.type.methods.find { method ->
            method.isAnnotationPresent(EnumValue::class.java)
          }
          val enumValue = param.type.enumConstants.firstOrNull { enumConstant ->
            if (enumValueMethod != null) {
              enumValueMethod.invoke(enumConstant).toString() == value
            } else {
              (enumConstant as Enum<*>).name == value
            }
          }
          if (enumValue != null) params.add(enumValue)
          return@forEach
        }

        if (param.isComplex) {
          try {
            val value = sequenceOf(
              if (paramsInfo.size == 1) bodyObj else null,
              bodyMap[param.name]?.let { JsonUtil.toJsonObject(it) },
              combinedParams[param.name]?.let { JsonObject(it) },
              bodyObj
            ).filterNotNull().firstOrNull { !it.isEmpty }
            if (value?.isEmpty == true && !param.isNullable) {
              throw IllegalArgumentException("Missing required parameter: ${param.name}")
            }
            params.add(if (value == null || value.isEmpty) null else JsonUtil.parseObject(value, param.type))
            return@forEach
          } catch (e: Exception) {
            throw IllegalArgumentException(e.message, e)
          }
        }

        params.add(
          when (param.type) {
            RoutingContext::class.java -> ctx
            User::class.java -> ctx.user()
            else -> {
              val bodyValue = bodyMap[param.name]
              val paramValue = bodyValue?.toString() ?: combinedParams[param.name]
              when {
                paramValue == null -> {
                  if (!param.isNullable) throw IllegalArgumentException("Missing required parameter: ${param.name}") else null
                }

                else -> {
                  val value = getParamValue(paramValue.toString(), param.type)
                  if (!param.isNullable && value == null) {
                    throw IllegalArgumentException("Missing required parameter: ${param.name}")
                  } else {
                    value
                  }
                }
              }
            }
          }
        )
      }

      return params.toTypedArray()
    }

    /**
     * 将字符串参数值映射到目标类型。
     *
     * @param paramValue 参数的字符串值。
     * @param type 目标 [Class] 类型。
     * @return 转换为目标类型的参数值，如果转换失败则返回 `null`。
     */
    private fun getParamValue(paramValue: String, type: Class<*>): Any? {
      return when (type) {
        String::class.java -> paramValue
        Int::class.java, Integer::class.java -> paramValue.toIntOrNull()
        Long::class.java, Long::class.java -> paramValue.toLongOrNull()
        Double::class.java, Double::class.java -> paramValue.toDoubleOrNull()
        Boolean::class.java, Boolean::class.java -> paramValue.toBoolean()
        else -> paramValue
      }
    }

    /**
     * 根据 [CustomizeRequest] 注解确定给定 REST 方法的 HTTP 方法。
     *
     * @param method 目标方法。
     * @return 对应的 [HttpMethod]。
     */
    fun getHttpMethod(method: Method): HttpMethod {
      val api = method.getAnnotation(CustomizeRequest::class.java)
      return if (api != null) {
        when (api.method.uppercase()) {
          "GET" -> HttpMethod.GET
          "PUT" -> HttpMethod.PUT
          "DELETE" -> HttpMethod.DELETE
          "PATCH" -> HttpMethod.PATCH
          else -> HttpMethod.POST
        }
      } else {
        HttpMethod.POST
      }
    }

    /**
     * 将对象序列化为 JSON 表示。
     *
     * @param obj 要序列化的对象。
     * @return JSON 字符串。
     */
    private fun serializeToJson(obj: Any?): String {
      return objectMapper.writeValueAsString(obj)
    }

    private fun getEnumValue(enumValue: Any?): Any? {
      if (enumValue == null || !enumValue::class.java.isEnum) {
        return null // 不是枚举或为空，直接返回 null
      }
      val enumClass = enumValue::class.java
      val methods = enumClass.declaredMethods
      for (method in methods) {
        if (method.isAnnotationPresent(EnumValue::class.java)) {
          method.isAccessible = true // 如果方法是私有的，设置为可访问
          return method.invoke(enumValue) // 调用带有 @EnumValue 注解的方法
        }
      }
      return null // 没有找到带有 @EnumValue 注解的方法
    }
  }

  private data class RouteInfo(
    val classType: Class<*>,
    val method: Method,
    val kFunction: KFunction<*>,
    val parameterInfo: List<ParameterInfo>,
    val customizeResp: Boolean,
    val role: CheckRole? = null,
    val permissions: CheckPermission? = null,
    val httpMethod: HttpMethod = getHttpMethod(method)
  )

  private data class ParameterInfo(
    val name: String,
    val type: Class<*>,
    val isNullable: Boolean,
    val isList: Boolean,
    val isComplex: Boolean,
    val isEnum: Boolean
  )
}
