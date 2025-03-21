package org.aikrai.vertx.db.migration

/**
 * SQL信息类
 * 存储从实体类中提取的SQL相关信息
 */
data class SqlInfo(
    var tableName: String = "",
    val columns: MutableList<ColumnInfo> = mutableListOf(),
    val primaryKeys: MutableList<String> = mutableListOf(),
    val indexes: MutableList<IndexInfo> = mutableListOf()
)

/**
 * 列信息类
 */
data class ColumnInfo(
    var name: String = "",
    var type: String = "",
    var nullable: Boolean = true,
    var defaultValue: String = "",
    var isPrimaryKey: Boolean = false,
    var enumValues: List<String>? = null,
    var unique: Boolean = false,
    var length: Int = 255,
    var comment: String = ""
)

/**
 * 索引信息类
 */
data class IndexInfo(
    var name: String = "",
    var columnNames: List<String> = listOf(),
    var unique: Boolean = false,
    var concurrent: Boolean = false,
    var definition: String = ""
) 