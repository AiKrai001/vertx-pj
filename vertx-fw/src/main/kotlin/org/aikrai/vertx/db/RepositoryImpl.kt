package org.aikrai.vertx.db

import cn.hutool.core.util.StrUtil
import com.fasterxml.jackson.core.type.TypeReference
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.sqlclient.*
import io.vertx.sqlclient.templates.SqlTemplate
import jakarta.persistence.Column
import jakarta.persistence.Id
import jakarta.persistence.Table
import mu.KotlinLogging
import org.aikrai.vertx.db.tx.TxCtx
import org.aikrai.vertx.jackson.JsonUtil
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.sql.Timestamp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.coroutines.coroutineContext

open class RepositoryImpl<TId, TEntity : Any>(
  private val sqlClient: SqlClient
) : Repository<TId, TEntity> {
  private val clazz: Class<TEntity> = (this::class.java.genericSuperclass as ParameterizedType)
    .actualTypeArguments[1] as Class<TEntity>
  private val logger = KotlinLogging.logger {}
  private val sqlTemplateMap: Map<Pair<String, String>, String> = mutableMapOf()
  private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSSXXX")

  override suspend fun <R> execute(sql: String): R {
    return if (sql.trimStart().startsWith("SELECT", true)) {
      val list = SqlTemplate.forQuery(getConnection(), sql).execute(mapOf())
        .coAwait().map { it.toJson() }
      val jsonObject = JsonUtil.toJsonObject(list)
      val typeReference = object : TypeReference<R>() {}
      JsonUtil.parseObject(jsonObject, typeReference, true)
    } else {
      val rowCount = SqlTemplate.forUpdate(getConnection(), sql).execute(mapOf())
        .coAwait().rowCount()
      rowCount as R
    }
  }

  override suspend fun create(t: TEntity): Int {
    val tableName = getTableName()
    val sqlTemplate = sqlTemplateMap[Pair(tableName, "create")] ?: run {
      val idColumnName = getIdColumnName()
      val columnsMap = getColumnMappings()
      // Exclude 'id' field if it's auto-generated
      val fields = clazz.declaredFields.filter { it.name != idColumnName }
      val columns = fields.map { columnsMap[it.name] }
      val parameters = fields.map { it.name }
      val sql =
        "INSERT INTO $tableName (${columns.joinToString(", ")}) VALUES (${parameters.joinToString(", ") { "#{$it}" }})"
      sqlTemplateMap.plus(Pair(tableName, "create")) to sql
      sql
    }
    val params = getNonNullFields(t)
    logger.info { "SQL: $sqlTemplate,  PARAMS: $params" }
    return SqlTemplate.forUpdate(getConnection(), sqlTemplate)
      .execute(params)
      .coAwait()
      .rowCount()
  }

  override suspend fun delete(id: TId): Int {
    val tableName = getTableName()
    val sqlTemplate = sqlTemplateMap[Pair(tableName, "delete")] ?: run {
      val idColumnName = getIdColumnName()
      val sql = "DELETE FROM $tableName WHERE $idColumnName = #{id}"
      sqlTemplateMap.plus(Pair(tableName, "delete")) to sql
      sql
    }
    val params = mapOf("id" to id)
    logger.debug { "SQL: $sqlTemplate,  PARAMS: $params" }
    return SqlTemplate.forUpdate(getConnection(), sqlTemplate)
      .execute(params)
      .coAwait()
      .rowCount()
  }

  override suspend fun update(t: TEntity): Int {
    val tableName = getTableName()
    val sqlTemplate = sqlTemplateMap[Pair(tableName, "update")] ?: run {
      val idColumnName = getIdColumnName()
      val columnsMap = getColumnMappings()
      // Exclude 'id' from update fields
      val fields = clazz.declaredFields.filter { it.name != idColumnName }
      val setClause = fields.joinToString(", ") { "${columnsMap[it.name]} = #{${it.name}}" }
      val sql = "UPDATE $tableName SET $setClause WHERE $idColumnName = #{id}"
      sqlTemplateMap.plus(Pair(tableName, "update")) to sql
      sql
    }
    // Get id value
    val idColumnName = getIdColumnName()
    val idField = clazz.declaredFields.find { it.name == idColumnName }
      ?: throw IllegalArgumentException("Class ${clazz.simpleName} must have an 'id' field for update operation.")
    idField.isAccessible = true
    val idValue = idField.get(t)
    // Prepare parameters
    val params = getNonNullFields(t) + mapOf("id" to idValue)
    logger.debug { "SQL: $sqlTemplate,  PARAMS: $params" }
    return SqlTemplate.forUpdate(getConnection(), sqlTemplate)
      .execute(params)
      .coAwait()
      .rowCount()
  }

  override suspend fun update(id: TId, parameters: Map<String, Any?>): Int {
    val tableName = getTableName()
    val sqlTemplate = sqlTemplateMap[Pair(tableName, "update")] ?: run {
      val idColumnName = getIdColumnName()
      val columnsMap = getColumnMappings()
      val setClause = parameters.keys.joinToString(", ") { "${columnsMap[it]} = #{$it}" }
      val sql = "UPDATE $tableName SET $setClause WHERE $idColumnName = #{id}"
      sqlTemplateMap.plus(Pair(tableName, "update")) to sql
      sql
    }
    val params = parameters + mapOf("id" to id)
    logger.debug { "SQL: $sqlTemplate,  PARAMS: $params" }
    return SqlTemplate.forUpdate(getConnection(), sqlTemplate)
      .execute(params)
      .coAwait()
      .rowCount()
  }

  override suspend fun get(id: TId): TEntity? {
    val tableName = getTableName()
    val sqlTemplate = sqlTemplateMap[Pair(tableName, "get")] ?: run {
      val idColumnName = getIdColumnName()
      val columnsMap = getColumnMappings()
      val columns = columnsMap.values.joinToString(", ")
      val sql = "SELECT $columns FROM $tableName WHERE $idColumnName = #{id}"
      (sqlTemplateMap as MutableMap)[Pair(tableName, "get")] = sql
      sql
    }
    val params = mapOf("id" to id)
    logger.debug { "SQL: $sqlTemplate,  PARAMS: $params" }
    val rows = SqlTemplate
      .forQuery(getConnection(), sqlTemplate)
      .mapTo(Row::toJson)
      .execute(params)
      .coAwait()
      .firstOrNull()
    return rows?.let { JsonUtil.parseObject(it.toString(), clazz, true) }
  }

  override suspend fun queryBuilder(): QueryWrapper<TEntity> {
    return QueryWrapperImpl(clazz, getConnection())
  }

  override suspend fun queryBuilder(clazz: Class<*>): QueryWrapper<*> {
    return QueryWrapperImpl(clazz, getConnection())
  }

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

  // 其他工具方法
  override suspend fun createBatch(list: List<TEntity>): Int {
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
  }

  // 工具方法：获取表名
  private fun getTableName(): String {
    return clazz.getAnnotation(Table::class.java)?.name?.takeIf { it.isNotBlank() }
      ?: StrUtil.toUnderlineCase(clazz.simpleName)
  }

  // 添加获取ID字段名称的方法
  private fun getIdColumnName(): String {
    val idField = clazz.declaredFields.find { it.isAnnotationPresent(Id::class.java) }
      ?: throw IllegalArgumentException("No @Id field found in ${clazz.simpleName}")
    return idField.getAnnotation(Column::class.java)?.name?.takeIf { it.isNotBlank() }
      ?: StrUtil.toUnderlineCase(idField.name)
  }

  private fun getColumnMappings(): Map<String, String> {
    return clazz.declaredFields.associate { field ->
      val columnAnnotation = field.getAnnotation(Column::class.java)
      val columnName = columnAnnotation?.name?.takeIf { it.isNotBlank() }
        ?: StrUtil.toUnderlineCase(field.name)
      field.name to columnName
    }
  }

  // 工具方法：获取非空字段及其值
  private fun getNonNullFields(t: TEntity): Map<String, Any> {
    return clazz.declaredFields
      .filter { field ->
        field.isAccessible = true
        // 排除被 @Transient 注解标记的字段
        !field.isAnnotationPresent(Transient::class.java) &&
          field.get(t) != null
      }
      .associate { field ->
        field.name to field.get(t)
      }
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
      is Timestamp -> // 时间戳类型，格式化为指定的日期时间字符串
        "'${formatter.format(value.toInstant().atZone(ZoneId.of("Asia/Shanghai")))}'"

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
}
