package org.aikrai.vertx.db

import cn.hutool.core.util.StrUtil
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.SqlClient
import io.vertx.sqlclient.templates.SqlTemplate
import jakarta.persistence.Column
import jakarta.persistence.Table
import org.aikrai.vertx.jackson.JsonUtil
import kotlin.reflect.KProperty1
import kotlin.reflect.jvm.javaField

class QueryWrapperImpl<T : Any>(
  private val entityClass: Class<T>,
  private val sqlClient: SqlClient,
) : QueryWrapper<T> {
  private val conditions = mutableListOf<QueryCondition>()

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
      val columnName = it.javaField?.getAnnotation(Column::class.java)?.name?.takeIf { it.isNotBlank() }
        ?: StrUtil.toUnderlineCase(it.name)
      conditions.add(
        QueryCondition(
          type = QueryType.SELECT,
          column = columnName
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

  override fun eq(condition: Boolean, column: String, value: Any): QueryWrapper<T> {
    if (condition) {
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

  override fun eq(condition: Boolean, column: KProperty1<T, *>, value: Any): QueryWrapper<T> {
    if (condition) {
      val columnName = column.javaField?.getAnnotation(Column::class.java)?.name?.takeIf { it.isNotBlank() }
        ?: StrUtil.toUnderlineCase(column.name)
      conditions.add(
        QueryCondition(
          type = QueryType.WHERE,
          column = columnName,
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
    val sqlBuilder = StringBuilder()

    // SELECT 子句
    sqlBuilder.append("SELECT ")
    val selectCondition = conditions.find { it.type == QueryType.SELECT }
    if (selectCondition != null) {
      sqlBuilder.append(selectCondition.column)
    } else {
      sqlBuilder.append("*")
    }

    // FROM 子句
    val from = conditions.filter { it.type == QueryType.FROM }
    if (from.isNotEmpty()) {
      sqlBuilder.append(" FROM ${from.first().column}")
    } else {
      entityClass.getAnnotation(Table::class.java)?.name?.let {
        sqlBuilder.append(" FROM $it")
      } ?: sqlBuilder.append(" FROM ${StrUtil.toUnderlineCase(entityClass.simpleName)}")
    }

    // WHERE 子句
    val whereConditions = conditions.filter { it.type == QueryType.WHERE }
    if (whereConditions.isNotEmpty()) {
      sqlBuilder.append(" WHERE ")
      sqlBuilder.append(
        whereConditions.joinToString(" AND ") {
          when (it.operator) {
            "IN", "NOT IN" -> "${it.column} ${it.operator} (${(it.value as Collection<*>).joinToString(",")})"
            "LIKE" -> "${it.column} ${it.operator} '${it.value}'"
            else -> "${it.column} ${it.operator} '${it.value}'"
          }
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
  }

  override fun genSql(): String {
    return buildSql()
  }

  override suspend fun getList(): List<T> {
    val sql = buildSql()
    val objs = SqlTemplate
      .forQuery(sqlClient, sql)
      .mapTo(Row::toJson)
      .execute(emptyMap())
      .coAwait()
      .toList()
    return objs.map { JsonUtil.parseObject(it.encode(), entityClass) }
  }

  override suspend fun getOne(): T? {
    val sql = buildSql()
    val obj = SqlTemplate
      .forQuery(sqlClient, sql)
      .mapTo(Row::toJson)
      .execute(emptyMap())
      .coAwait()
      .firstOrNull()
    return obj?.let { JsonUtil.parseObject(it.encode(), entityClass) }
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
