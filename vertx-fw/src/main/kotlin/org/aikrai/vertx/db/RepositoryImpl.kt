package org.aikrai.vertx.db

import cn.hutool.core.util.IdUtil
import cn.hutool.core.util.StrUtil
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.type.TypeFactory
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.sqlclient.*
import io.vertx.sqlclient.templates.SqlTemplate
import mu.KotlinLogging
import org.aikrai.vertx.db.annotation.*
import org.aikrai.vertx.db.tx.TxCtx
import org.aikrai.vertx.jackson.JsonUtil
import org.aikrai.vertx.utlis.Meta
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlin.reflect.KProperty1

open class RepositoryImpl<TId, TEntity : Any>(
  private val sqlClient: SqlClient
) : Repository<TId, TEntity> {
  companion object {
    // classInfoCache
    private val fieldsCache = ConcurrentHashMap<String, List<Field>>()
    private val fieldMappingCache = ConcurrentHashMap<String, Map<String, String>>()
    private val idFieldCache = ConcurrentHashMap<String, Field>()
    private val idFieldNameCache = ConcurrentHashMap<String, String>()
    private val tableNameCache = ConcurrentHashMap<String, String>()
    // sqlCache
    private val baseSqlCache = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()
    private val queryClientCache = ConcurrentHashMap<String, Any>()
  }

  private val logger = KotlinLogging.logger {}
  private val clazz: Class<TEntity> = (this::class.java.genericSuperclass as ParameterizedType)
    .actualTypeArguments[1] as Class<TEntity>

  // 缓存字段和映射
  private val fields: List<Field> by lazy {
    fieldsCache.getOrPut(clazz.simpleName) {
      clazz.declaredFields.filter {
        !Modifier.isStatic(it.modifiers) &&
            !it.isSynthetic &&
            !it.isAnnotationPresent(Transient::class.java)
      }.onEach { it.isAccessible = true }
    }
  }

  private val fieldMappings: Map<String, String> by lazy {
    fieldMappingCache.getOrPut(clazz.simpleName) {
      fields.associate { field ->
        val fieldAnnotation = field.getAnnotation(TableField::class.java)
        val fieldName = fieldAnnotation?.value?.takeIf { it.isNotBlank() }
          ?: StrUtil.toUnderlineCase(field.name)
        field.name to fieldName
      }
    }
  }

  private val idField: Field by lazy {
    idFieldCache.getOrPut(clazz.simpleName) {
      clazz.declaredFields.find { it.isAnnotationPresent(TableId::class.java) }
        ?.also { it.isAccessible = true }
        ?: throw IllegalArgumentException("No @Id field in ${clazz.simpleName}")
    }
  }

  private val idFieldName: String by lazy {
    idFieldNameCache.getOrPut(clazz.simpleName) {
      idField.getAnnotation(TableField::class.java)?.value?.takeIf { it.isNotBlank() }
        ?: StrUtil.toUnderlineCase(idField.name)
    }
  }

  private val tableName: String by lazy {
    tableNameCache.getOrPut(clazz.simpleName) {
      clazz.getAnnotation(TableName::class.java)?.value?.takeIf { it.isNotBlank() }
        ?: StrUtil.toUnderlineCase(clazz.simpleName)
    }
  }

  override suspend fun create(t: TEntity): Int {
    try {
      val idAnnotation = idField.getAnnotation(TableId::class.java)
      val idValue = idField.get(t)
      val excludeId = idAnnotation != null && (idValue == null || idValue == 0L || idValue == -1L) &&
        (idAnnotation.type == IdType.AUTO)
      val sqlKey = if (excludeId) "createExcludeId" else "createIncludeId"

      val sqlTemplate = getOrCreateSql(tableName, sqlKey) {
        val columns = if (excludeId) {
          fields.filter { it.name != idField.name }.map { fieldMappings[it.name] }
        } else {
          fields.map { fieldMappings[it.name] }
        }.joinToString(", ")
        val parameters = if (excludeId) {
          fields.filter { it.name != idField.name }.joinToString(", ") { "#{" + it.name + "}" }
        } else {
          fields.joinToString(", ") { "#{" + it.name + "}" }
        }
        val returning = if (excludeId) " RETURNING $idFieldName" else ""
        "INSERT INTO $tableName ($columns) VALUES ($parameters)$returning"
      }

      val params = getNonNullFields(t).let {
        if (excludeId) it.filterKeys { key -> key != idField.name } else it
      }.toMutableMap()

      // 填充ID
      when (idAnnotation.type) {
        IdType.INPUT -> {
          if (idValue == 0L || idValue == -1L) throw Meta.repository("CreateError", "must provide ID value")
        }
        IdType.ASSIGN_ID -> params[idField.name] = IdUtil.getSnowflakeNextId()
        IdType.ASSIGN_UUID -> params[idField.name] = IdUtil.simpleUUID()
        else -> {}
      }
      // 处理TableField注解
      fields.forEach { field ->
        val tableField = field.getAnnotation(TableField::class.java)
        if (tableField != null && tableField.fill != FieldFill.DEFAULT) {
          // fill属性不为DEFAULT时，根据fill属性填充字段
          val value = when (tableField.fill) {
            FieldFill.INSERT, FieldFill.UPDATE, FieldFill.INSERT_UPDATE -> {
              when (field.type) {
                LocalDateTime::class.java -> LocalDateTime.now()
                Timestamp::class.java -> OffsetDateTime.ofInstant(Instant.now(), ZoneId.systemDefault())
                else -> null
              }
            }
            else -> null
          }
          if (value != null) params[field.name] = value
        }
      }

      return if (excludeId) {
        logger.debug { "SQL: $sqlTemplate ,PARAMS: $params" }
        // 执行查询以获取生成的ID
        val result = SqlTemplate.forQuery(getConnection(), sqlTemplate)
          .execute(params)
          .coAwait()
        val rows = result.toList()
        if (rows.isEmpty()) throw IllegalStateException("Insert failed")
        // 提取生成的ID并回填
        val generatedId = rows.first().getValue(idFieldName)
        idField.set(t, generatedId)
        1
      } else {
        execute(sqlTemplate, params)
      }
    } catch (e: Exception) {
      logger.error(e) { "Error creating entity: $t" }
      throw Meta.repository(e.javaClass.simpleName, e.message)
    }
  }

  override suspend fun delete(id: TId): Int {
    try {
      val sqlKey = "delete"
      val sqlTemplate = getOrCreateSql(tableName, sqlKey) {
        "DELETE FROM $tableName WHERE $idFieldName = #{id}"
      }
      val params = mapOf("id" to id)
      if (logger.isDebugEnabled) {
        logger.debug { "SQL: $sqlTemplate, PARAMS: $params" }
      }
      return execute(sqlTemplate, params)
    } catch (e: Exception) {
      logger.error(e) { "Error deleting entity with id: $id" }
      throw Meta.repository(e.javaClass.simpleName, e.message)
    }
  }

  override suspend fun update(t: TEntity): Int {
    try {
      val sqlKey = "update"
      val sqlTemplate = getOrCreateSql(tableName, sqlKey) {
        val fields = clazz.declaredFields.filter { it.name != idFieldName }
        val setClause = fields.joinToString(", ") { "${fieldMappings[it.name]} = #{${it.name}}" }
        "UPDATE $tableName SET $setClause WHERE $idFieldName = #{id}"
      }
      val params = getNonNullFields(t) + mapOf("id" to idField.get(t))
      logger.debug { "SQL: $sqlTemplate,  PARAMS: $params" }
      return execute(sqlTemplate, params)
    } catch (e: Exception) {
      logger.error(e) { "Error updating entity: $t" }
      throw Meta.repository(e.javaClass.simpleName, e.message)
    }
  }

  override suspend fun update(id: TId, parameters: Map<String, Any?>): Int {
    try {
      val sqlKey = "update_$parameters"
      val sqlTemplate = getOrCreateSql(tableName, sqlKey) {
        val setClause = parameters.keys.joinToString(", ") { "${fieldMappings[it]} = #{$it}" }
        "UPDATE $tableName SET $setClause WHERE $idFieldName = #{id}"
      }
      val params = parameters + mapOf("id" to id)
      logger.debug { "SQL: $sqlTemplate,  PARAMS: $params" }
      return execute(sqlTemplate, params)
    } catch (e: Exception) {
      logger.error(e) { "Error updating entity with id: $id" }
      throw Meta.repository(e.javaClass.simpleName, e.message)
    }
  }

  override suspend fun get(id: TId): TEntity? {
    try {
      val sqlKey = "get"
      val sqlTemplate = getOrCreateSql(tableName, sqlKey) {
        val columns = fieldMappings.values.joinToString(", ")
        "SELECT $columns FROM $tableName WHERE $idFieldName = #{id}"
      }
      return get(sqlTemplate, mapOf("id" to id), clazz)
    } catch (e: Exception) {
      logger.error(e) { "Error getting entity with id: $id" }
      throw Meta.repository(e.javaClass.simpleName, e.message)
    }
  }

  override suspend fun getByField(field: String, value: Any): TEntity? {
    try {
      val sqlKey = "getByField_$field"
      val sqlTemplate = getOrCreateSql(tableName, sqlKey) {
        val columns = fieldMappings.values.joinToString(", ")
        "SELECT $columns FROM $tableName WHERE $field = #{value}"
      }
      val params = mapOf("value" to value)
      logger.debug { "SQL: $sqlTemplate,  PARAMS: $params" }
      return get(sqlTemplate, params, clazz)
    } catch (e: Exception) {
      logger.error(e) { "Error getting entity by field: $field = $value" }
      throw Meta.repository(e.javaClass.simpleName, e.message)
    }
  }

  override suspend fun getByField(field: KProperty1<TEntity, *>, value: Any): TEntity? {
    try {
      val sqlKey = "getByField_${field.name}"
      val sql = getOrCreateSql(tableName, sqlKey) {
        val columns = fieldMappings.values.joinToString(", ")
        "SELECT $columns FROM $tableName WHERE ${fieldMappings[field.name]} = #{value}"
      }
      val params = mapOf("value" to value)
      logger.debug { "SQL: $sql,  PARAMS: $params" }
      return get(sql, params, clazz)
    } catch (e: Exception) {
      logger.error(e) { "Error getting entity by field: ${field.name} = $value" }
      throw Meta.repository(e.javaClass.simpleName, e.message)
    }
  }

  override suspend fun createBatch(list: List<TEntity>): Int {
    try {
      if (list.isEmpty()) return 0
      var rowCount = 0
      list.chunked(1000).forEach {
        val sql = genBatchInsertSql(it)
        rowCount += SqlTemplate.forUpdate(sqlClient, sql)
          .execute(emptyMap())
          .coAwait()
          .rowCount()
      }
      return rowCount
    } catch (e: Exception) {
      logger.error(e) { "Error creating batch entities: $list" }
      throw Meta.repository(e.javaClass.simpleName, e.message)
    }
  }

  // base方法
  suspend fun <R> get(sql: String, params: Map<String, Any?>, clazz: Class<*>): R? {
    logger.debug { "SQL: $sql,  PARAMS: $params" }
    val resultSet = SqlTemplate.forQuery(getConnection(), sql).execute(params).coAwait()
    val list = resultSet.map { it.toJson() }
    return when (list.size) {
      0 -> null
      1 -> JsonUtil.parseObject(list[0], clazz, true) as R
      else -> throw IllegalStateException("Expected single result but got ${list.size}")
    }
  }

  suspend fun <R> find(sql: String, params: Map<String, Any?>, clazz: Class<*>): R? {
    logger.debug { "SQL: $sql,  PARAMS: $params" }
    val resultSet = SqlTemplate.forQuery(getConnection(), sql).execute(params).coAwait()
    val list = resultSet.map { it.toJson() }
    val listType = TypeFactory.defaultInstance().constructCollectionType(List::class.java, clazz)
    val listTypeReference = object : TypeReference<List<Any>>() {
      override fun getType() = listType
    }
    return JsonUtil.parseArray(JsonUtil.toJsonArray(list), listTypeReference, true) as R
  }

  suspend fun execute(sql: String, params: Map<String, Any?> = emptyMap()): Int {
    logger.debug { "SQL: $sql ,PARAMS: $params" }
    return try {
      SqlTemplate.forUpdate(getConnection(), sql)
        .execute(params)
        .coAwait()
        .rowCount()
    } catch (e: Exception) {
      logger.error(e) { "Error executing SQL: $sql, PARAMS: $params" }
      throw Meta.repository(e.javaClass.simpleName, e.message)
    }
  }

  suspend fun queryBuilder(qClazz: Class<Any>? = null): QueryWrapper<TEntity> {
    val qClass = qClazz ?: clazz
    val connection = getConnection()
    val queryWrapper = queryClientCache.getOrPut(qClass.simpleName) {
      QueryWrapperImpl(qClass, tableName, fieldMappings)
    } as QueryWrapperImpl<TEntity>
    queryWrapper.sqlClient = connection
    return queryWrapper
  }

  // 其他工具方法
  private suspend fun getConnection(): SqlClient {
    return if (TxCtx.isTransactionActive(coroutineContext)) {
      TxCtx.currentSqlConnection(coroutineContext) ?: run {
        logger.error("TransactionContextElement.sqlConnection is null")
        return sqlClient
      }
    } else {
      sqlClient
    }
  }

  // 通用获取或创建 SQL 模板的方法
  private fun getOrCreateSql(tableName: String, key: String, sqlProvider: () -> String): String {
    val tableSqlMap = baseSqlCache.getOrPut(tableName) { ConcurrentHashMap() }
    return tableSqlMap.getOrPut(key, sqlProvider)
  }

  // 获取非空字段及其值
  private fun getNonNullFields(t: TEntity): Map<String, Any> {
    return fields.filter { !it.isAnnotationPresent(Transient::class.java) && it.get(t) != null }
      .associate { it.name to it.get(t) }
  }

  /**
   * 生成批量 INSERT SQL 语句的函数
   * @param objects 要插入的对象列表
   * @return 生成的 SQL 语句字符串
   */
  private fun <TEntity> genBatchInsertSql(objects: List<TEntity>): String {
    // 如果对象列表为空，直接返回空字符串
    if (objects.isEmpty()) return ""
    // 将类名转换为下划线命名的表名，例如：UserInfo -> user_info
    val tableName = StrUtil.toUnderlineCase(clazz.simpleName)
    // 获取类的所有字段，包括私有字段
    val fields = clazz.declaredFields.filter {
      // 过滤掉静态字段和合成字段
      !Modifier.isStatic(it.modifiers) && !it.isSynthetic
    }
    // 确保所有字段可访问
    fields.forEach { it.isAccessible = true }
    // 将字段名转换为下划线命名的列名，并用逗号隔开
    val columnNames = fields.joinToString(", ") { StrUtil.toUnderlineCase(it.name) }

    // SQL 转义函数
    fun escapeSql(value: String): String = value.replace("'", "''")

    // 格式化属性值为 SQL 字符串
    fun formatValue(value: Any?): String = when (value) {
      null -> "NULL" // 如果值为 null，返回字符串 "NULL"
      is String -> "'${escapeSql(value)}'" // 字符串类型，加单引号并进行转义
      is Enum<*> -> "'${value.name}'" // 枚举类型，使用枚举名，添加单引号
      is Number, is Boolean -> value.toString() // 数字和布尔类型，直接转换为字符串
      is Timestamp -> // 时间戳类型
        "'${OffsetDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault())}'"
      is Array<*> -> // 数组类型处理
        if (value.isEmpty()) "'{}'" else "'{${value.joinToString(",") { escapeSql(it?.toString() ?: "NULL") }}}'"
      is Collection<*> -> // 集合类型处理
        if (value.isEmpty()) "'{}'" else "'{${value.joinToString(",") { escapeSql(it?.toString() ?: "NULL") }}}'"
      else -> "'${escapeSql(value.toString())}'" // 其他类型，调用 toString() 后转义并加单引号
    }
    // 构建 VALUES 部分，每个对象对应一组值
    val valuesList = objects.map { instance ->
      fields.joinToString(", ", "(", ")") { field ->
        // 获取属性值，并格式化为 SQL 字符串
        formatValue(field.get(instance))
      }
    }
    return "INSERT INTO $tableName ($columnNames) VALUES ${valuesList.joinToString(", ")};"
  }

  fun isCollectionType(type: Type): Boolean {
    return when (type) {
      is Class<*> -> type.isArray || Collection::class.java.isAssignableFrom(type)
      is ParameterizedType -> Collection::class.java.isAssignableFrom((type.rawType as Class<*>))
      else -> false
    }
  }
}
