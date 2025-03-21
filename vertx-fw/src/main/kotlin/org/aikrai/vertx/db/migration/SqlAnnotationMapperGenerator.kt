package org.aikrai.vertx.db.migration

import cn.hutool.core.util.StrUtil
import java.time.LocalDateTime
import kotlin.reflect.KClass

/**
 * SQL注解映射生成器
 * 用于生成和使用SQL注解映射中间类
 */
class SqlAnnotationMapperGenerator {

  companion object {
    // 是否输出日志到控制台
    private var LOG_TO_SYSTEM_OUT = true
    
    /**
     * 设置是否输出日志到控制台
     */
    fun setLogToSystemOut(log: Boolean) {
      LOG_TO_SYSTEM_OUT = log
    }
    
    /**
     * 记录日志信息
     */
    private fun log(message: String) {
      if (LOG_TO_SYSTEM_OUT) {
        println("DbMigration> $message")
      }
    }
    
    /**
     * 从实体类获取SQL信息
     * @param entityClass 实体类
     * @param mapper 注解映射中间类
     * @return SQL信息
     */
    fun extractSqlInfo(entityClass: KClass<*>, mapper: SqlAnnotationMapper): SqlInfo {
      val sqlInfo = SqlInfo()

      // 获取表名 - 优先从表名注解中获取，如果没有则使用类名转换
      try {
        if (mapper.tableName != null) {
          val tableNameMapping = mapper.tableName!!
          val annotation = entityClass.annotations.find {
            it.annotationClass.qualifiedName == tableNameMapping.annotationClass.qualifiedName
          }

          if (annotation != null) {
            try {
              val method = annotation.javaClass.getMethod(tableNameMapping.propertyName)
              val tableName = method.invoke(annotation) as String

              if (tableName.isNotBlank()) {
                sqlInfo.tableName = tableName
              } else {
                // 使用类名转蛇形命名作为表名
                sqlInfo.tableName = StrUtil.toUnderlineCase(entityClass.simpleName ?: "")
              }
            } catch (e: Exception) {
              // 使用类名转蛇形命名作为表名
              sqlInfo.tableName = StrUtil.toUnderlineCase(entityClass.simpleName ?: "")
            }
          } else {
            // 使用类名转蛇形命名作为表名
            sqlInfo.tableName = StrUtil.toUnderlineCase(entityClass.simpleName ?: "")
          }
        } else {
          // 使用类名转蛇形命名作为表名
          sqlInfo.tableName = StrUtil.toUnderlineCase(entityClass.simpleName ?: "")
        }
      } catch (e: Exception) {
        throw IllegalArgumentException("处理实体类 ${entityClass.simpleName} 的表名时出错: ${e.message}", e)
      }

      // 获取实体类的所有字段
      val fields = entityClass.java.declaredFields
      if (fields.isEmpty()) {
        throw IllegalArgumentException("实体类 ${entityClass.simpleName} 没有声明任何字段")
      }

      // 创建已处理字段名集合，用于记录已经处理过的字段
      val processedFields = mutableSetOf<String>()

      // 查找实体类中的@Transient注解类
      val transientAnnotationClasses = listOf(
        "kotlin.jvm.Transient",
        "javax.persistence.Transient",
        "jakarta.persistence.Transient",
        "java.beans.Transient",
        "org.aikrai.vertx.db.annotation.Transient"
      )

      fields.forEach { field ->
        try {
          // 检查字段是否有@Transient注解，如果有则跳过
          val isTransient = field.annotations.any { annotation ->
            transientAnnotationClasses.any { className ->
              annotation.annotationClass.qualifiedName == className
            }
          }

          if (isTransient) {
            return@forEach
          }

          // 获取列信息
          val columnInfo = ColumnInfo()
          var foundColumnMapping = false

          // 处理每个列映射 - 这部分处理带有特定注解的字段
          mapper.columnMappings.forEach { columnMapping ->
            val nameAnnotation = field.annotations.find {
              it.annotationClass.qualifiedName == columnMapping.nameMapping.annotationClass.qualifiedName
            }


            foundColumnMapping = true
            try {
              val nameMethod = nameAnnotation?.javaClass?.getMethod(columnMapping.nameMapping.propertyName)
              val columnName = nameMethod?.invoke(nameAnnotation) as? String
              columnInfo.name = if (columnName.isNullOrEmpty()) StrUtil.toUnderlineCase(field.name) else columnName

              // 处理类型映射
              columnMapping.typeMapping?.let { typeMapping ->
                val typeAnnotation = field.annotations.find {
                  it.annotationClass.qualifiedName == typeMapping.annotationClass.qualifiedName
                }
                if (typeAnnotation != null) {
                  try {
                    val typeMethod = typeAnnotation.javaClass.getMethod(typeMapping.propertyName)
                    val typeName = typeMethod.invoke(typeAnnotation) as String
                    columnInfo.type = if (typeName.isEmpty()) {
                      inferSqlType(field.type, columnInfo, mapper)
                    } else {
                      typeName
                    }
                  } catch (e: Exception) {
                    throw IllegalArgumentException("处理字段 ${field.name} 的类型映射时出错: ${e.message}", e)
                  }
                } else {
                  columnInfo.type = inferSqlType(field.type, columnInfo, mapper)
                }
              } ?: {
                columnInfo.type = inferSqlType(field.type, columnInfo, mapper)
              }

              // 检查字段是否为枚举类型，并且有默认初始值
              if (field.type.isEnum) {
                try {
                  // 使字段可访问
                  field.isAccessible = true

                  // 获取声明类的实例（暂时创建一个实例）
                  val declaringClass = field.declaringClass
                  val instance = try {
                    declaringClass.getDeclaredConstructor().newInstance()
                  } catch (e: Exception) {
                    null
                  }

                  if (instance != null) {
                    // 获取默认枚举值
                    val defaultEnumValue = field.get(instance)
                    if (defaultEnumValue != null) {
                      // 如果有EnumValue注解的方法，使用它获取枚举值
                      if (mapper.enumValueMapping != null) {
                        val enumValueMethod = field.type.methods.find { method ->
                          method.annotations.any {
                            it.annotationClass.qualifiedName == mapper.enumValueMapping!!.annotationClass.qualifiedName
                          }
                        }

                        if (enumValueMethod != null) {
                          // 获取枚举值并设置为默认值
                          val enumValue = enumValueMethod.invoke(defaultEnumValue)
                          if (enumValue != null) {
                            columnInfo.defaultValue = enumValue.toString()
                          }
                        }
                      }
                    }
                  }
                } catch (e: Exception) {
                  // 忽略获取默认值失败的情况
                  log("获取枚举默认值失败: ${e.message}")
                }
              }

              // 处理可空映射
              columnMapping.nullableMapping?.let { nullableMapping ->
                val nullableAnnotation = field.annotations.find {
                  it.annotationClass.qualifiedName == nullableMapping.annotationClass.qualifiedName
                }
                if (nullableAnnotation != null) {
                  try {
                    val nullableMethod = nullableAnnotation.javaClass.getMethod(nullableMapping.propertyName)
                    columnInfo.nullable = nullableMethod.invoke(nullableAnnotation) as Boolean
                  } catch (e: Exception) {
                    throw IllegalArgumentException("处理字段 ${field.name} 的可空性映射时出错: ${e.message}", e)
                  }
                }
              }

              // 处理长度映射
              columnMapping.lengthMapping?.let { lengthMapping ->
                val lengthAnnotation = field.annotations.find {
                  it.annotationClass.qualifiedName == lengthMapping.annotationClass.qualifiedName
                }
                if (lengthAnnotation != null) {
                  try {
                    val lengthMethod = lengthAnnotation.javaClass.getMethod(lengthMapping.propertyName)
                    val lengthValue = lengthMethod.invoke(lengthAnnotation)
                    if (lengthValue is Int) {
                      columnInfo.length = lengthValue
                      // 如果是VARCHAR类型，更新类型定义中的长度
                      if (columnInfo.type.startsWith("VARCHAR")) {
                        columnInfo.type = "VARCHAR(${columnInfo.length})"
                      }
                    }
                  } catch (e: Exception) {
                    throw IllegalArgumentException("处理字段 ${field.name} 的长度映射时出错: ${e.message}", e)
                  }
                }
              }

              // 处理唯一性映射
              columnMapping.uniqueMapping?.let { uniqueMapping ->
                val uniqueAnnotation = field.annotations.find {
                  it.annotationClass.qualifiedName == uniqueMapping.annotationClass.qualifiedName
                }
                if (uniqueAnnotation != null) {
                  try {
                    val uniqueMethod = uniqueAnnotation.javaClass.getMethod(uniqueMapping.propertyName)
                    val uniqueValue = uniqueMethod.invoke(uniqueAnnotation)
                    if (uniqueValue is Boolean) {
                      columnInfo.unique = uniqueValue
                    }
                  } catch (e: Exception) {
                    throw IllegalArgumentException("处理字段 ${field.name} 的唯一性映射时出错: ${e.message}", e)
                  }
                }
              }

              // 处理注释映射
              columnMapping.commentMapping?.let { commentMapping ->
                val commentAnnotation = field.annotations.find {
                  it.annotationClass.qualifiedName == commentMapping.annotationClass.qualifiedName
                }
                if (commentAnnotation != null) {
                  try {
                    val commentMethod = commentAnnotation.javaClass.getMethod(commentMapping.propertyName)
                    val commentValue = commentMethod.invoke(commentAnnotation)
                    if (commentValue is String) {
                      columnInfo.comment = commentValue
                    }
                  } catch (e: Exception) {
                    throw IllegalArgumentException("处理字段 ${field.name} 的注释映射时出错: ${e.message}", e)
                  }
                }
              }

              // 处理默认值映射 - 只有在字段不是枚举或枚举但没有初始值时才处理注解中的默认值
              if (columnInfo.defaultValue.isEmpty()) {
                columnMapping.defaultValueMapping?.let { defaultValueMapping ->
                  val defaultValueAnnotation = field.annotations.find {
                    it.annotationClass.qualifiedName == defaultValueMapping.annotationClass.qualifiedName
                  }
                  if (defaultValueAnnotation != null) {
                    try {
                      val defaultValueMethod =
                        defaultValueAnnotation.javaClass.getMethod(defaultValueMapping.propertyName)
                      columnInfo.defaultValue = defaultValueMethod.invoke(defaultValueAnnotation) as String
                    } catch (e: Exception) {
                      throw IllegalArgumentException("处理字段 ${field.name} 的默认值映射时出错: ${e.message}", e)
                    }
                  }
                }
              }

              sqlInfo.columns.add(columnInfo)
              processedFields.add(field.name) // 记录已处理的字段
            } catch (e: Exception) {
              throw IllegalArgumentException("处理字段 ${field.name} 时出错: ${e.message}", e)
            }

          }

          // 处理主键
          if (mapper.primaryKeyMapping != null) {
            val pkMapping = mapper.primaryKeyMapping!!
            val pkAnnotation = field.annotations.find {
              it.annotationClass.qualifiedName == pkMapping.annotationClass.qualifiedName
            }
            // 只要字段有@TableId注解，不管属性值如何，都视为主键
            if (pkAnnotation != null) {
              // 如果字段还未处理，创建默认列信息
              if (!processedFields.contains(field.name)) {
                columnInfo.name = StrUtil.toUnderlineCase(field.name)
                columnInfo.type = inferSqlType(field.type, columnInfo, mapper)
                columnInfo.isPrimaryKey = true
                // 主键不可为空
                columnInfo.nullable = false
                sqlInfo.columns.add(columnInfo)
                sqlInfo.primaryKeys.add(columnInfo.name)
                processedFields.add(field.name)
              } else {
                // 如果已处理，找到对应列并标记为主键
                val column =
                  sqlInfo.columns.find { it.name == StrUtil.toUnderlineCase(field.name) || it.name == field.name }
                if (column != null) {
                  column.isPrimaryKey = true
                  // 主键不可为空
                  column.nullable = false
                  if (!sqlInfo.primaryKeys.contains(column.name)) {
                    sqlInfo.primaryKeys.add(column.name)
                  }
                }
              }
            }
          }

          // 如果字段未被处理，并且不是static或transient，添加默认处理
          if (!processedFields.contains(field.name) &&
            !java.lang.reflect.Modifier.isStatic(field.modifiers) &&
            !java.lang.reflect.Modifier.isTransient(field.modifiers)
          ) {

            // 检查字段类型是否可空
            val isNullable = isNullableType(field)

            // 创建默认列信息
            val defaultColumnInfo = ColumnInfo(
              name = StrUtil.toUnderlineCase(field.name),
              type = "",  // 先不设置类型
              nullable = isNullable,
              defaultValue = "",
              isPrimaryKey = false
            )

            // 设置类型并处理枚举值
            defaultColumnInfo.type = inferSqlType(field.type, defaultColumnInfo, mapper)

            sqlInfo.columns.add(defaultColumnInfo)
            processedFields.add(field.name)
          }
        } catch (e: Exception) {
          throw IllegalArgumentException(
            "处理实体类 ${entityClass.simpleName} 的字段 ${field.name} 时出错: ${e.message}",
            e
          )
        }
      }

      // 验证结果
      if (sqlInfo.tableName.isEmpty()) {
        throw IllegalArgumentException("实体类 ${entityClass.simpleName} 的表名为空，请检查表名注解")
      }

      if (sqlInfo.columns.isEmpty()) {
        throw IllegalArgumentException("实体类 ${entityClass.simpleName} 没有可用的列信息，请检查列注解")
      }

      // 处理表级别的索引注解
      processTableIndexes(entityClass, sqlInfo, mapper)

      return sqlInfo
    }

    /**
     * 处理表级别的索引注解
     */
    private fun processTableIndexes(entityClass: KClass<*>, sqlInfo: SqlInfo, mapper: SqlAnnotationMapper) {
      // 只有当有indexMapping配置时才处理
      if (mapper.indexMapping == null) {
        return
      }

      try {
        val indexMapping = mapper.indexMapping!!
        // 查找类上的所有TableIndex注解
        val tableIndexAnnotations = entityClass.annotations.filter {
          it.annotationClass.qualifiedName == indexMapping.annotationClass.qualifiedName
        }

        // 处理每个TableIndex注解
        tableIndexAnnotations.forEach { annotation ->
          try {
            // 提取索引信息
            val indexInfo = IndexInfo()

            // 获取索引名称
            val nameMethod = annotation.javaClass.getMethod("name")
            val indexName = nameMethod.invoke(annotation) as String
            indexInfo.name =
              if (indexName.isNotEmpty()) indexName else "idx_${sqlInfo.tableName}_${System.currentTimeMillis()}"

            // 获取唯一性
            val uniqueMethod = annotation.javaClass.getMethod("unique")
            indexInfo.unique = uniqueMethod.invoke(annotation) as Boolean

            // 获取并发创建选项
            val concurrentMethod = annotation.javaClass.getMethod("concurrent")
            indexInfo.concurrent = concurrentMethod.invoke(annotation) as Boolean

            // 获取列名列表
            val columnNamesMethod = annotation.javaClass.getMethod("columnNames")
            val columnNames = columnNamesMethod.invoke(annotation) as Array<*>
            indexInfo.columnNames = columnNames.map { it.toString() }

            // 获取自定义定义
            val definitionMethod = annotation.javaClass.getMethod("definition")
            val definition = definitionMethod.invoke(annotation) as String
            indexInfo.definition = definition

            // 只有当至少有一个列名或自定义定义时才添加索引
            if (indexInfo.columnNames.isNotEmpty() || indexInfo.definition.isNotEmpty()) {
              sqlInfo.indexes.add(indexInfo)
            }
          } catch (e: Exception) {
            // 处理单个索引注解失败时记录错误但继续处理其他索引
            log("处理索引注解时出错: ${e.message}")
          }
        }
      } catch (e: Exception) {
        // 处理索引注解整体失败时记录错误
        log("处理表 ${sqlInfo.tableName} 的索引注解时出错: ${e.message}")
      }
    }

    /**
     * 根据Java类型推断SQL类型
     */
    private fun inferSqlType(
      javaType: Class<*>,
      columnInfo: ColumnInfo? = null,
      mapper: SqlAnnotationMapper? = null
    ): String {
      val sqlType = when {
        javaType == String::class.java -> {
          if (columnInfo != null) {
            "VARCHAR(${columnInfo.length})"
          } else {
            "VARCHAR(255)"
          }
        }

        javaType == Int::class.java || javaType == Integer::class.java -> "INTEGER"
        javaType == Long::class.java || javaType == java.lang.Long::class.java -> "BIGINT"
        javaType == Double::class.java || javaType == java.lang.Double::class.java -> "DOUBLE PRECISION"
        javaType == Float::class.java || javaType == java.lang.Float::class.java -> "REAL"
        javaType == Boolean::class.java || javaType == java.lang.Boolean::class.java -> "BOOLEAN"
        javaType == Char::class.java || javaType == Character::class.java -> "CHAR(1)"
        javaType == java.util.Date::class.java || javaType == java.sql.Date::class.java -> "DATE"
        javaType == java.sql.Timestamp::class.java -> "TIMESTAMPTZ"
        javaType == LocalDateTime::class.java -> "TIMESTAMPTZ"
        javaType == ByteArray::class.java -> "BYTEA"
        javaType.name.contains("Map") || javaType.name.contains("HashMap") -> "JSONB"
        javaType.name.contains("List") || javaType.name.contains("ArrayList") -> "JSONB"
        javaType.name.contains("Set") || javaType.name.contains("HashSet") -> "JSONB"
        javaType.name.endsWith("DTO") || javaType.name.endsWith("Dto") -> "JSONB"
        javaType.name.contains("Json") || javaType.name.contains("JSON") -> "JSONB"
        javaType.isEnum -> {
          // 处理枚举类型，提取枚举值并保存到columnInfo中
          if (columnInfo != null) {
            try {
              // 获取枚举类中的所有枚举常量
              val enumConstants = javaType.enumConstants
              if (enumConstants != null && enumConstants.isNotEmpty()) {
                // 查找带有EnumValue注解的方法
                val enumValues = if (mapper?.enumValueMapping != null) {
                  val enumValueMethod = javaType.methods.find { method ->
                    method.annotations.any {
                      it.annotationClass.qualifiedName == mapper.enumValueMapping!!.annotationClass.qualifiedName
                    }
                  }

                  if (enumValueMethod != null) {
                    // 使用EnumValue标注的方法获取枚举值
                    enumConstants.map { enumValueMethod.invoke(it).toString() }
                  } else {
                    // 如果没有找到EnumValue注解的方法，使用枚举名称
                    enumConstants.map { (it as Enum<*>).name }
                  }
                } else {
                  // 默认使用枚举的toString()
                  enumConstants.map { it.toString() }
                }
                columnInfo.enumValues = enumValues

                // 如果字段有默认值，确保默认值是通过带EnumValue的方法获取的
                val defaultEnumValue = javaType.enumConstants.find { (it as Enum<*>).name == columnInfo.defaultValue }
                if (defaultEnumValue != null && mapper?.enumValueMapping != null) {
                  val enumValueMethod = javaType.methods.find { method ->
                    method.annotations.any {
                      it.annotationClass.qualifiedName == mapper.enumValueMapping!!.annotationClass.qualifiedName
                    }
                  }
                  if (enumValueMethod != null) {
                    // 更新默认值为EnumValue方法的返回值
                    columnInfo.defaultValue = enumValueMethod.invoke(defaultEnumValue).toString()
                  }
                }
              }
            } catch (e: Exception) {
              // 忽略枚举值提取失败的情况
              log("提取枚举值失败: ${e.message}")
            }
          }
          
          // 根据枚举值的类型决定SQL类型
          if (columnInfo != null && columnInfo.enumValues?.isNotEmpty() == true) {
            val firstValue = columnInfo.enumValues!!.first()
            val enumType = when {
              // 尝试将值转换为数字，判断是否是数值型枚举
              firstValue.toIntOrNull() != null -> "INTEGER"
              firstValue.toLongOrNull() != null -> "BIGINT"
              firstValue.toDoubleOrNull() != null -> "DOUBLE PRECISION"
              // 如果值很短，使用CHAR
              firstValue.length <= 1 -> "CHAR(1)"
              // 找出最长的枚举值，并基于此设置VARCHAR长度
              else -> {
                val maxLength = columnInfo.enumValues!!.maxBy { it.length }.length
                val safeLength = maxLength + 10 // 增加一些余量
                "VARCHAR($safeLength)"
              }
            }
            enumType
          } else {
            "VARCHAR(50)" // 默认回退
          }
        }

        else -> "VARCHAR(255)"
      }

      return sqlType
    }

    /**
     * 判断字段类型是否可空
     */
    private fun isNullableType(field: java.lang.reflect.Field): Boolean {
      // 检查字段类型名称中是否包含Nullable标记
      val typeName = field.genericType.typeName

      // 1. 先检查字段类型名是否包含"?"，这是Kotlin可空类型的标志
      if (typeName.contains("?")) {
        return true
      }

      // 2. 检查字段是否为Java原始类型，这些类型不可为空
      if (field.type.isPrimitive) {
        return false
      }

      // 3. 通过Java反射获取字段的声明可空性
      try {
        // 检查是否有@Nullable相关注解
        val hasNullableAnnotation = field.annotations.any {
          val name = it.annotationClass.qualifiedName ?: ""
          name.contains("Nullable") || name.contains("nullable")
        }

        if (hasNullableAnnotation) {
          return true
        }
      } catch (e: Exception) {
        // 忽略注解检查错误
      }

      // 4. 检查字段的类型并判断其可空性
      // Kotlin的String类型不可为空，而Java的String类型可为空
      if (field.type == String::class.java) {
        // 尝试通过字段的初始值判断
        try {
          field.isAccessible = true
          // 如果是非静态字段且初始值为null，则可能为可空类型
          if (!java.lang.reflect.Modifier.isStatic(field.modifiers)) {
            // 对于具有初始值的非静态字段，如果初始值为非null字符串，则认为是非可空类型
            // 如果字段名以OrNull或Optional结尾，认为是可空类型
            if (field.name.endsWith("OrNull") || field.name.endsWith("Optional")) {
              return true
            }

            // 对于Kotlin中的非空String类型，如果有初始值""，则不可为空
            // 检查是否为Kotlin类型
            val isKotlinType = typeName.startsWith("kotlin.")
            if (isKotlinType) {
              return false // Kotlin中的String类型不可为空
            }
          }
        } catch (e: Exception) {
          // 忽略访问字段值的错误
        }
      }

      // 5. 检查字段是否为其他Kotlin基本类型且非可空
      if (field.type == Int::class.java ||
        field.type == Long::class.java ||
        field.type == Boolean::class.java ||
        field.type == Float::class.java ||
        field.type == Double::class.java ||
        field.type == Char::class.java ||
        field.type == Byte::class.java ||
        field.type == Short::class.java
      ) {
        // Kotlin基本类型不带?就不可为空
        return false
      }

      // 6. 默认情况: 引用类型默认认为是可空的
      return true
    }
  }
} 