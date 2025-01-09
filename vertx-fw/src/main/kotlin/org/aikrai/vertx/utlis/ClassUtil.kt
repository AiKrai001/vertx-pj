package org.aikrai.vertx.utlis

import cn.hutool.core.util.ReflectUtil
import java.io.File
import java.lang.reflect.Method
import java.util.*
import java.util.jar.JarFile
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.javaSetter

object ClassUtil {

  /**
   * 获取应用程序的主类
   *
   * @return 主类的 Class 对象，如果未找到则返回 null
   */
  fun getMainClass(): Class<*>? {
    val classLoader = ServiceLoader.load(ClassLoader::class.java).firstOrNull()
      ?: Thread.currentThread().contextClassLoader
    val mainCommand = System.getProperty("sun.java.command")
    if (mainCommand != null) {
      val mainClassName: String? = if (mainCommand.endsWith(".jar")) {
        try {
          val jarFilePath = mainCommand.split(" ").first()
          val jarFile = File(jarFilePath)
          if (jarFile.exists()) {
            JarFile(jarFile).use { jar ->
              jar.manifest.mainAttributes.getValue("Main-Class")
            }
          } else {
            null
          }
        } catch (e: Exception) {
          e.printStackTrace()
          null
        }
      } else {
        // mainCommand 是类名
        mainCommand.split(" ").first()
      }

      if (!mainClassName.isNullOrEmpty()) {
        return try {
          classLoader.loadClass(mainClassName)
        } catch (e: ClassNotFoundException) {
          e.printStackTrace()
          null
        }
      }
    }
    return null
  }

  /**
   * 获取类中的所有公共方法
   *
   * @return 包含控制器类和其方法数组的列表
   */
  fun getPublicMethods(classSet: Set<Class<*>>): List<Pair<Class<*>, Array<Method>>> {
    return classSet.map { clazz ->
      val kClass = clazz.kotlin
      // 获取所有属性的 getter 方法
      val getters: Set<Method> = kClass.memberProperties.mapNotNull { it.javaGetter }.toSet()
      // 获取所有可变属性的 setter 方法
      val setters: Set<Method> = kClass.memberProperties
        // 只处理可变属性
        .filterIsInstance<KMutableProperty1<*, *>>()
        .mapNotNull { it.javaSetter }.toSet()
      // 合并 getter 和 setter 方法
      val propertyAccessors: Set<Method> = getters + setters
      // 获取所有公共方法
      val allPublicMethods = ReflectUtil.getPublicMethods(clazz)
      // 过滤掉不需要的方法
      val filteredMethods = allPublicMethods.filter { method ->
        // 1. 排除合成方法 2. 仅包括在当前类中声明的方法
        !method.isSynthetic && method.declaringClass == clazz && !propertyAccessors.contains(method)
      }.toTypedArray()
      Pair(clazz, filteredMethods)
    }
  }
}
