package org.aikrai.vertx.db

import io.vertx.kotlin.coroutines.coAwait
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.SqlClient
import io.vertx.sqlclient.templates.SqlTemplate
import mu.KotlinLogging
import org.aikrai.vertx.jackson.JsonUtil
import org.aikrai.vertx.utlis.Meta
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KProperty1

class QueryWrapperImpl<T : Any>(
  private val clazz: Class<T>,
  private val tableName: String,
  private val fieldMappings: Map<String, String>,
) : QueryWrapper<T> {
  var sqlClient: SqlClient? = null

  private val logger = KotlinLogging.logger { }
  private val conditions = CopyOnWriteArrayList<QueryCondition>()
  private val sqlMap = ConcurrentHashMap<String, String>()

  override fun select(vararg columns: String): QueryWrapper<T> {
    conditions.add(
      QueryCondition(
        type = QueryType.SELECT,
        column = columns.joinToString(",")
      )
    )
    return this
  }

  override fun select(vararg columns: KProperty1<T, *>): QueryWrapper<T> {
    columns.forEach {
      conditions.add(
        QueryCondition(
          type = QueryType.SELECT,
          column = fieldMappings[it.name] ?: it.name
        )
      )
    }
    return this
  }

  override fun eq(column: String, value: Any): QueryWrapper<T> {
    return eq(true, column, value)
  }

  override fun eq(column: KProperty1<T, *>, value: Any): QueryWrapper<T> {
    return eq(true, column, value)
  }

  override fun eq(condition: Boolean, column: String, value: Any?): QueryWrapper<T> {
    if (condition && value != null && value.toString().isNotBlank()) {
      conditions.add(
        QueryCondition(
          type = QueryType.WHERE,
          column = column,
          operator = "=",
          value = value
        )
      )
    }
    return this
  }

  override fun eq(condition: Boolean, column: KProperty1<T, *>, value: Any?): QueryWrapper<T> {
    if (condition && value != null && value.toString().isNotBlank()) {
      conditions.add(
        QueryCondition(
          type = QueryType.WHERE,
          column = fieldMappings[column.name] ?: column.name,
          operator = "=",
          value = value
        )
      )
    }
    return this
  }

  override fun from(table: String): QueryWrapper<T> {
    conditions.add(
      QueryCondition(
        type = QueryType.FROM,
        column = table
      )
    )
    return this
  }

  override fun like(column: String, value: String): QueryWrapper<T> {
    conditions.add(
      QueryCondition(
        type = QueryType.LIKE,
        column = column,
        operator = "LIKE",
        value = "%$value%"
      )
    )
    return this
  }

  override fun like(column: KProperty1<T, *>, value: String): QueryWrapper<T> {
    conditions.add(QueryCondition(QueryType.LIKE, column.name, "LIKE", "%$value%"))
    return this
  }

  override fun likeLeft(column: KProperty1<T, *>, value: String): QueryWrapper<T> {
    conditions.add(
      QueryCondition(
        type = QueryType.WHERE,
        column = column.name,
        operator = "LIKE",
        value = "%$value"
      )
    )
    return this
  }

  override fun likeRight(column: KProperty1<T, *>, value: String): QueryWrapper<T> {
    conditions.add(
      QueryCondition(
        type = QueryType.WHERE,
        column = column.name,
        operator = "LIKE",
        value = "$value%"
      )
    )
    return this
  }

  override fun `in`(column: KProperty1<T, *>, values: Collection<*>): QueryWrapper<T> {
    conditions.add(
      QueryCondition(
        type = QueryType.WHERE,
        column = column.name,
        operator = "IN",
        value = values
      )
    )
    return this
  }

  override fun notIn(column: KProperty1<T, *>, values: Collection<*>): QueryWrapper<T> {
    conditions.add(
      QueryCondition(
        type = QueryType.WHERE,
        column = column.name,
        operator = "NOT IN",
        value = values
      )
    )
    return this
  }

  override fun groupBy(vararg columns: KProperty1<T, *>): QueryWrapper<T> {
    conditions.add(
      QueryCondition(
        type = QueryType.GROUP_BY,
        column = columns.joinToString(",") { it.name }
      )
    )
    return this
  }

  override fun having(condition: String): QueryWrapper<T> {
    conditions.add(
      QueryCondition(
        type = QueryType.HAVING,
        column = condition
      )
    )
    return this
  }

  override fun orderByAsc(vararg columns: KProperty1<T, *>): QueryWrapper<T> {
    conditions.add(
      QueryCondition(
        type = QueryType.ORDER_BY,
        column = columns.joinToString(",") { it.name },
        additional = mapOf("direction" to "ASC")
      )
    )
    return this
  }

  override fun orderByDesc(vararg columns: KProperty1<T, *>): QueryWrapper<T> {
    conditions.add(
      QueryCondition(
        type = QueryType.ORDER_BY,
        column = columns.joinToString(",") { it.name },
        additional = mapOf("direction" to "DESC")
      )
    )
    return this
  }

  private fun buildSql(): String {
    try {
      val sqlBuilder = StringBuilder()
      // SELECT 子句
      sqlBuilder.append("SELECT ")
      val selectCondition = conditions.find { it.type == QueryType.SELECT }
      if (selectCondition != null) {
        sqlBuilder.append(selectCondition.column)
      } else {
        fieldMappings.values.joinToString(",").let {
          sqlBuilder.append(it)
        }
      }

      // FROM 子句
      val from = conditions.filter { it.type == QueryType.FROM }
      if (from.isNotEmpty()) {
        sqlBuilder.append(" FROM ${from.first().column}")
      } else {
        sqlBuilder.append(" FROM $tableName")
      }

      // WHERE 子句
      val whereConditions = conditions.filter { it.type == QueryType.WHERE }
      if (whereConditions.isNotEmpty()) {
        sqlBuilder.append(" WHERE ")
        sqlBuilder.append(
          whereConditions.joinToString(" AND ") {
            "${it.column} ${it.operator} #{${it.column}}"
          }
        )
      }

      // GROUP BY 子句
      conditions.find { it.type == QueryType.GROUP_BY }?.let {
        sqlBuilder.append(" GROUP BY ${it.column}")
      }

      // HAVING 子句
      conditions.find { it.type == QueryType.HAVING }?.let {
        sqlBuilder.append(" HAVING ${it.column}")
      }

      // ORDER BY 子句
      val orderByConditions = conditions.filter { it.type == QueryType.ORDER_BY }
      if (orderByConditions.isNotEmpty()) {
        sqlBuilder.append(" ORDER BY ")
        sqlBuilder.append(
          orderByConditions.joinToString(", ") {
            "${it.column} ${it.additional["direction"]}"
          }
        )
      }
      return sqlBuilder.toString()
    } catch (e: Exception) {
      throw Meta.repository(e.javaClass.simpleName, e.message + "SQL 构建失败")
    }
  }

  private fun buildParams(): Map<String, String> {
    val params = mutableMapOf<String, String>()
    conditions.filter { it.type == QueryType.WHERE }.forEach {
      when (it.operator) {
        "IN", "NOT IN" -> {
          params[it.column] = "(${(it.value as Collection<*>).joinToString(",")})"
        }

        else -> {
          params[it.column] = it.value.toString()
        }
      }
    }
    return params
  }

  override fun genSql(): Pair<String, Map<String, String>> {
    return (buildSql() to buildParams()).also { conditions.clear() }
  }

  override suspend fun getList(): List<T> {
    if (sqlClient == null) {
      throw Meta.repository("SqlClientError", "SqlClient 未初始化")
    }
    try {
      val cacheKey = generateCacheKey(tableName, conditions)
      val sql = sqlMap.getOrPut(cacheKey) {
        buildSql()
      }
      val params = buildParams()
      logger.debug { "SQL: $sql ,PARAMS: $params" }
      val objs = SqlTemplate
        .forQuery(sqlClient, sql)
        .mapTo(Row::toJson)
        .execute(params)
        .coAwait()
        .toList()
      return objs.map { JsonUtil.parseObject(it, clazz, true) }.also { conditions.clear() }
    } catch (e: Exception) {
      conditions.clear()
      throw Meta.repository(e.javaClass.simpleName, e.message)
    }
  }

  override suspend fun getOne(): T? {
    if (sqlClient == null) {
      throw Meta.repository("SqlClientError", "SqlClient 未初始化")
    }
    try {
      val cacheKey = generateCacheKey(tableName, conditions)
      val sql = sqlMap.getOrPut(cacheKey) { buildSql() }
      val params = buildParams()
      logger.debug { "SQL: $sql ,PARAMS: $params" }
      val resultSet = SqlTemplate.forQuery(sqlClient, sql).execute(params).coAwait()
      val list = resultSet.map { it.toJson() }
      return when (list.size) {
        0 -> null
        1 -> JsonUtil.parseObject(list[0], clazz, true)
        else -> throw IllegalStateException("Expected single result but got ${list.size}")
      }
    } catch (e: Exception) {
      conditions.clear()
      throw Meta.repository(e.javaClass.simpleName, e.message)
    }
  }

  private fun generateCacheKey(tableName: String, conditions: List<QueryCondition>): String {
    val keyBuilder = StringBuilder(tableName)
    conditions.forEach { condition ->
      keyBuilder.append("|${condition.type}|${condition.column}|${condition.operator}")
    }
    return keyBuilder.toString()
  }
}

enum class QueryType {
  SELECT,
  WHERE,
  FROM,
  GROUP_BY,
  HAVING,
  ORDER_BY,
  LIKE
}

data class QueryCondition(
  val type: QueryType,
  val column: String,
  val operator: String? = null,
  val value: Any? = null,
  val additional: Map<String, Any> = emptyMap()
)
