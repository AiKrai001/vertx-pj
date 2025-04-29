package org.aikrai.vertx.context

import cn.hutool.core.util.StrUtil
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.User
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.aikrai.vertx.auth.*
import org.aikrai.vertx.auth.AuthUser.Companion.validateAuth
import org.aikrai.vertx.db.annotation.EnumValue
import org.aikrai.vertx.jackson.JsonUtil
import org.aikrai.vertx.resp.DefaultResponseHandler
import org.aikrai.vertx.resp.ResponseHandler
import org.aikrai.vertx.utlis.ClassUtil
import org.aikrai.vertx.utlis.Meta
import org.reflections.Reflections
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import kotlin.coroutines.Continuation
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.javaType

/**
 * RouterBuilder - 基于注解控制器构建Vert.x路由的实用工具
 *
 * 该类扫描控制器类，分析其方法，并将其注册为具有适当参数绑定和授权规则的HTTP端点
 */
class RouterBuilder(
  private val coroutineScope: CoroutineScope,
  private val router: Router,
  private val scanPath: String? = null,
  private val responseHandler: ResponseHandler = DefaultResponseHandler()
) {
  // 不需要认证的路径集合
  val anonymousPaths = mutableListOf<String>()

  /**
   * 基于注解控制器类构建路由
   *
   * @param getIt 解析控制器实例的函数
   * @return 当前RouterBuilder实例（用于链式调用）
   */
  fun build(getIt: (clazz: Class<*>) -> Any): RouterBuilder {
    // 扫描并缓存控制器路由信息
    val routeInfoCache = scanControllerRoutes()

    // 注册路由处理器
    registerRouteHandlers(routeInfoCache, getIt)

    return this
  }

  /**
   * 扫描控制器类并提取路由信息
   */
  private fun scanControllerRoutes(): Map<Pair<String, HttpMethod>, RouteInfo> {
    val routeInfoCache = mutableMapOf<Pair<String, HttpMethod>, RouteInfo>()
    val packagePath = scanPath ?: ClassUtil.getMainClass().packageName
    val controllerClassSet = Reflections(packagePath).getTypesAnnotatedWith(Controller::class.java)
    val controllerMethods = ClassUtil.getPublicMethods(controllerClassSet)

    for ((classType, methods) in controllerMethods) {
      processControllerClass(classType, methods.toList(), routeInfoCache)
    }

    return routeInfoCache
  }

  /**
   * 处理控制器类及其方法
   */
  private fun processControllerClass(
    classType: Class<*>,
    methods: List<Method>,
    routeInfoCache: MutableMap<Pair<String, HttpMethod>, RouteInfo>
  ) {
    val controllerAnnotation = classType.getDeclaredAnnotationsByType(Controller::class.java).firstOrNull()
    val prefixPath = controllerAnnotation?.prefix ?: ""
    val classAllowAnonymous = classType.getAnnotation(AllowAnonymous::class.java) != null

    for (method in methods) {
      val reqPath = getReqPath(prefixPath, classType, method)
      val httpMethod = getHttpMethod(method)

      // 处理匿名访问
      if (classAllowAnonymous || method.getAnnotation(AllowAnonymous::class.java) != null) {
        anonymousPaths.add(reqPath)
      }

      // 提取方法元数据
      val customizeResp = method.getAnnotation(CustomizeResponse::class.java) != null
      val role = method.getAnnotation(CheckRole::class.java)
      val permissions = method.getAnnotation(CheckPermission::class.java)

      // 查找对应的Kotlin函数
      classType.kotlin.declaredFunctions.find { it.name == method.name }?.let { kFunction ->
        val parameterInfo = extractParameterInfo(kFunction)
        routeInfoCache[reqPath to httpMethod] = RouteInfo(
          classType, method, kFunction, parameterInfo,
          customizeResp, role, permissions, httpMethod
        )
      }
    }
  }

  /**
   * 从Kotlin函数中提取参数信息
   */
  private fun extractParameterInfo(kFunction: KFunction<*>): List<ParameterInfo> {
    return kFunction.parameters.mapNotNull { parameter ->
      val javaType = parameter.type.javaType

      // 跳过协程的Continuation参数
      if (javaType is Class<*> && Continuation::class.java.isAssignableFrom(javaType)) {
        return@mapNotNull null
      }

      // 参数必须具有名称
      val paramName = parameter.name ?: return@mapNotNull null

      // 从D注解获取自定义参数名
      val annotation = parameter.annotations.find { it is D } as? D
      val finalParamName = annotation?.name?.takeIf { it.isNotBlank() } ?: paramName

      // 确定参数类型
      val typeClass = when (javaType) {
        is Class<*> -> javaType
        is ParameterizedType -> javaType.rawType as? Class<*>
        else -> null
      } ?: parameter.type.javaType as Class<*>

      // 处理枚举类型参数
      val isEnum = typeClass.isEnum || parameter.type.javaType.javaClass.isEnum
      val enumValueMethod = if (isEnum) {
        typeClass.methods.find { it.isAnnotationPresent(EnumValue::class.java) }
      } else null

      val enumConstants = if (isEnum) {
        typeClass.enumConstants?.associateBy({
          enumValueMethod?.invoke(it)?.toString() ?: (it as Enum<*>).name
        }, { it })
      } else emptyMap()

      // 检查是否是复杂类型
      val isComplex = !parameter.type.classifier.toString().startsWith("class kotlin.") &&
          !parameter.type.classifier.toString().startsWith("class io.vertx") &&
          !typeClass.isEnum &&
          !parameter.type.javaType.javaClass.isEnum &&
          parameter.type.javaType is Class<*>

      ParameterInfo(
        name = finalParamName,
        type = typeClass,
        isNullable = parameter.type.isMarkedNullable,
        isList = parameter.type.classifier == List::class,
        isComplex = isComplex,
        isEnum = isEnum,
        enumValueMethod = enumValueMethod,
        enumConstants = enumConstants
      )
    }
  }

  /**
   * 为缓存的路由注册路由处理程序
   */
  private fun registerRouteHandlers(
    routeInfoCache: Map<Pair<String, HttpMethod>, RouteInfo>,
    getIt: (clazz: Class<*>) -> Any
  ) {
    routeInfoCache.forEach { (pathMethod, routeInfo) ->
      val (path, _) = pathMethod
      router.route(routeInfo.httpMethod, path).handler { ctx ->
        handleRequest(ctx, getIt, routeInfo)
      }
    }
  }

  /**
   * 处理传入的HTTP请求
   */
  private fun handleRequest(ctx: RoutingContext, getIt: (clazz: Class<*>) -> Any, routeInfo: RouteInfo) {
    // 如果存在用户，检查授权
    ctx.user()?.let { user ->
      if (user is AuthUser) {
        try {
          user.validateAuth(routeInfo.role, routeInfo.permissions)
        } catch (e: Throwable) {
          ctx.fail(403, Meta.unauthorized("未授权"))
          return
        }
      }
    }

    // 获取控制器实例并执行方法
    val instance = getIt(routeInfo.classType)
    executeControllerMethod(ctx, instance, routeInfo)
  }

  /**
   * 在协程中执行控制器方法
   */
  private fun executeControllerMethod(ctx: RoutingContext, instance: Any, routeInfo: RouteInfo) {
    coroutineScope.launch {
      try {
        val params = resolveMethodParameters(ctx, routeInfo.parameterInfo)
        val result = if (routeInfo.kFunction.isSuspend) {
          routeInfo.kFunction.callSuspend(instance, *params)
        } else {
          routeInfo.kFunction.call(instance, *params)
        }
        responseHandler.handle(ctx, result, routeInfo.customizeResp)
      } catch (e: Throwable) {
        // 异常冒泡到全局错误处理器
        ctx.fail(e)
      }
    }
  }

  /**
   * 从请求中解析方法参数
   */
  private fun resolveMethodParameters(ctx: RoutingContext, paramsInfo: List<ParameterInfo>): Array<Any?> {
    val params = mutableListOf<Any?>()

    // 从不同来源收集参数
    val formAttributes = ctx.request().formAttributes().associate { it.key to it.value }
    val queryParams = ctx.queryParams().entries().associate { it.key to it.value }
    val combinedParams = formAttributes + queryParams

    // 解析请求体
    val bodyObj = ctx.body().takeUnless { it.isEmpty }?.asJsonObject()
    val bodyMap = bodyObj?.map ?: emptyMap()

    // 处理每个参数
    paramsInfo.forEach { param ->
      when {
        // 处理List类型参数
        param.isList -> {
          var value = ctx.queryParams().getAll(param.name)
          if (value.isEmpty() && bodyMap[param.name] != null) {
            value = (bodyMap[param.name] as? Collection<*>)?.map { it.toString() }?.toMutableList() ?: mutableListOf()
          }
          if (value.isEmpty() && !param.isNullable) {
            throw IllegalArgumentException("缺少必要参数: ${param.name}")
          }
          params.add(value.ifEmpty { null })
        }

        // 处理枚举类型参数
        param.isEnum -> {
          val value = combinedParams[param.name] ?: bodyMap[param.name]?.toString()
          val enumValue = param.enumConstants?.get(value)
          params.add(enumValue)
        }

        // 处理复杂对象参数
        param.isComplex -> {
          try {
            val value = sequenceOf(
              if (paramsInfo.size == 1) bodyObj else null,
              bodyMap[param.name]?.let { JsonUtil.toJsonObject(it) },
              combinedParams[param.name]?.let { JsonObject(it) },
              bodyObj
            ).filterNotNull().firstOrNull { !it.isEmpty }

            if (value?.isEmpty == true && !param.isNullable) {
              throw IllegalArgumentException("缺少必要参数: ${param.name}")
            }

            params.add(if (value == null || value.isEmpty) null else JsonUtil.parseObject(value, param.type))
          } catch (e: Exception) {
            throw IllegalArgumentException(e.message, e)
          }
        }

        // 处理特殊或基本类型参数
        else -> {
          params.add(when (param.type) {
            RoutingContext::class.java -> ctx
            User::class.java -> ctx.user()
            else -> {
              val bodyValue = bodyMap[param.name]
              val paramValue = bodyValue?.toString() ?: combinedParams[param.name]

              when {
                paramValue == null -> {
                  if (!param.isNullable) {
                    throw IllegalArgumentException("缺少必要参数: ${param.name}")
                  } else null
                }
                else -> {
                  val value = convertStringToType(paramValue.toString(), param.type)
                  if (!param.isNullable && value == null) {
                    throw IllegalArgumentException("缺少必要参数: ${param.name}")
                  } else value
                }
              }
            }
          })
        }
      }
    }

    return params.toTypedArray()
  }

  companion object {
    /**
     * 根据类和方法信息构造请求路径
     */
    private fun getReqPath(prefix: String, clazz: Class<*>, method: Method): String {
      val classPath = if (prefix.isNotBlank()) {
        StrUtil.toCamelCase(StrUtil.toUnderlineCase(prefix))
      } else {
        StrUtil.toCamelCase(StrUtil.toUnderlineCase(clazz.simpleName.removeSuffix("Controller")))
      }.let { if (it == "/") "" else it }

      val methodName = StrUtil.toCamelCase(StrUtil.toUnderlineCase(method.name))
      return "/$classPath/$methodName".replace("//", "/")
    }

    /**
     * 将字符串值转换为目标类型
     */
    private fun convertStringToType(paramValue: String, type: Class<*>): Any? {
      return when (type) {
        String::class.java -> paramValue
        Int::class.java, Integer::class.java -> paramValue.toIntOrNull()
        Long::class.java, java.lang.Long::class.java -> paramValue.toLongOrNull()
        Double::class.java, java.lang.Double::class.java -> paramValue.toDoubleOrNull()
        Boolean::class.java, java.lang.Boolean::class.java -> paramValue.toBoolean()
        else -> paramValue
      }
    }

    /**
     * 确定控制器方法的HTTP方法
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
  }

  /**
   * 路由元数据数据类
   */
  private data class RouteInfo(
    val classType: Class<*>,
    val method: Method,
    val kFunction: KFunction<*>,
    val parameterInfo: List<ParameterInfo>,
    val customizeResp: Boolean,
    val role: CheckRole? = null,
    val permissions: CheckPermission? = null,
    val httpMethod: HttpMethod
  )

  /**
   * 参数元数据数据类
   */
  private data class ParameterInfo(
    val name: String,
    val type: Class<*>,
    val isNullable: Boolean,
    val isList: Boolean,
    val isComplex: Boolean,
    val isEnum: Boolean,
    val enumValueMethod: Method? = null,
    val enumConstants: Map<String, Any>? = null
  )
}
