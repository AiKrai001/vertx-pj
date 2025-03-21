package org.aikrai.vertx.db.migration

import kotlin.reflect.KClass

/**
 * PostgreSQL SQL生成工具类
 */
class SqlGenerator {
    companion object {
        /**
         * 生成创建表SQL
         * @param entityClass 实体类
         * @param mapper 注解映射中间类
         * @return 创建表SQL语句
         */
        fun generateCreateTableSql(entityClass: KClass<*>, mapper: SqlAnnotationMapper): String {
            val sqlInfo = SqlAnnotationMapperGenerator.extractSqlInfo(entityClass, mapper)
            val tableName = sqlInfo.tableName
            val columns = sqlInfo.columns
            
            if (tableName.isEmpty() || columns.isEmpty()) {
                throw IllegalArgumentException("无法生成SQL，表名或列信息为空")
            }
            
            val sb = StringBuilder()
            sb.append("CREATE TABLE $tableName (\n")
            
            // 添加列定义 - 改为ANSI标准格式
            val columnDefinitions = columns.map { column ->
                // 特殊处理一些常见约定字段
                val specialFieldDefaults = getSpecialFieldDefaults(column.name, column.type)
                var defaultValue = ""
                
                // 处理默认值
                if (column.defaultValue.isNotEmpty()) {
                    // 对于时间戳类型字段，特殊处理NOW()函数作为默认值
                    if (column.type.contains("TIMESTAMP") && column.defaultValue.equals("now()", ignoreCase = true)) {
                        defaultValue = " DEFAULT 'now()'"
                    } else if (column.enumValues != null && column.enumValues!!.isNotEmpty()) {
                        // 对于枚举类型字段，直接使用默认值
                        // 如果是数字，就不带引号，否则加上引号
                        val isNumeric = column.defaultValue.matches(Regex("^[0-9]+$"))
                        if (isNumeric) {
                            defaultValue = " DEFAULT ${column.defaultValue}"
                        } else {
                            defaultValue = " DEFAULT '${column.defaultValue}'"
                        }
                    } else {
                        defaultValue = " DEFAULT ${column.defaultValue}"
                    }
                } else if (specialFieldDefaults.isNotEmpty()) {
                    defaultValue = specialFieldDefaults
                } else {
                    // 为一些常见类型提供合理的默认值
                    when {
                        column.type.contains("VARCHAR") -> defaultValue = " DEFAULT ''"
                        column.type == "INTEGER" || column.type == "BIGINT" -> defaultValue = " DEFAULT 0"
                        column.type == "BOOLEAN" -> defaultValue = " DEFAULT false"
                        column.type.contains("JSON") -> defaultValue = " DEFAULT '{}'"
                    }
                }
                
                val nullable = if (column.nullable) "" else " NOT NULL"
                // 移除内联注释，改用COMMENT ON语句
                "  ${column.name} ${column.type}$defaultValue$nullable"
            }
            
            sb.append(columnDefinitions.joinToString(",\n"))
            
            // 添加唯一约束
            val uniqueColumns = columns.filter { it.unique && !it.isPrimaryKey }
            for (column in uniqueColumns) {
                sb.append(",\n  CONSTRAINT uk_${tableName}_${column.name} UNIQUE (${column.name})")
            }
            
            // 添加枚举约束 - 识别枚举类型的列并添加CHECK约束
            val enumColumns = columns.filter { it.type.contains("VARCHAR") && it.enumValues != null && it.enumValues!!.isNotEmpty() }
            for (column in enumColumns) {
                if (column.enumValues != null && column.enumValues!!.isNotEmpty()) {
                    // 检查枚举值是否都是数字
                    val allNumeric = column.enumValues!!.all { it.matches(Regex("^[0-9]+$")) }
                    
                    sb.append(",\n  CONSTRAINT ck_${tableName}_${column.name} CHECK ( ${column.name} in (")
                    if (allNumeric) {
                        // 如果全是数字，不需要加引号
                        sb.append(column.enumValues!!.joinToString(","))
                    } else {
                        // 否则加上引号
                        sb.append(column.enumValues!!.joinToString(",") { "'$it'" })
                    }
                    sb.append("))")
                }
            }
            
            // 添加主键约束
            if (sqlInfo.primaryKeys.isNotEmpty()) {
                sb.append(",\n  CONSTRAINT pk_$tableName PRIMARY KEY (${sqlInfo.primaryKeys.joinToString(", ")})")
            }
            
            sb.append("\n);")
            
            // 添加字段注释 - 使用PostgreSQL的COMMENT ON语句
            val columnsWithComment = columns.filter { it.comment.isNotEmpty() }
            if (columnsWithComment.isNotEmpty()) {
                sb.append("\n\n-- 添加字段注释\n")
                for (column in columnsWithComment) {
                    sb.append("COMMENT ON COLUMN ${tableName}.${column.name} IS '${column.comment.replace("'", "''")}';")
                    sb.append("\n")
                }
            }
            
            // 添加索引创建语句
            val indexSql = generateCreateIndexSql(sqlInfo)
            if (indexSql.isNotEmpty()) {
                sb.append("\n\n")
                sb.append(indexSql)
            }
            
            return sb.toString()
        }
        
        /**
         * 生成创建索引的SQL语句
         * @param sqlInfo SQL信息
         * @return 创建索引SQL语句
         */
        private fun generateCreateIndexSql(sqlInfo: SqlInfo): String {
            val sb = StringBuilder()
            
            sqlInfo.indexes.forEach { index ->
                if (index.columnNames.isNotEmpty() || index.definition.isNotEmpty()) {
                    sb.append("CREATE ")
                    
                    if (index.unique) {
                        sb.append("UNIQUE ")
                    }
                    
                    sb.append("INDEX ")
                    
                    if (index.concurrent) {
                        sb.append("CONCURRENTLY ")
                    }
                    
                    sb.append("${index.name} ON ${sqlInfo.tableName}")
                    
                    if (index.definition.isNotEmpty()) {
                        // 使用自定义索引定义
                        sb.append(" ${index.definition}")
                    } else {
                        // 使用列名列表
                        sb.append(" (${index.columnNames.joinToString(", ")})")
                    }
                    
                    sb.append(";\n")
                }
            }
            
            return sb.toString()
        }
        
        /**
         * 获取特殊字段的默认值定义
         */
        private fun getSpecialFieldDefaults(fieldName: String, fieldType: String): String {
            return when {
                // 版本字段
                fieldName == "version" && (fieldType == "INTEGER" || fieldType == "BIGINT") -> 
                    " DEFAULT 0"
                // 创建时间字段
                fieldName == "created" || fieldName == "create_time" || fieldName == "creation_time" || fieldName == "created_at" -> {
                    if (fieldType.contains("TIMESTAMP")) " DEFAULT 'now()'" else ""
                }
                // 更新时间字段
                fieldName == "updated" || fieldName == "update_time" || fieldName == "last_update" || fieldName == "updated_at" -> {
                    if (fieldType.contains("TIMESTAMP")) " DEFAULT 'now()'" else ""
                }
                // 是否删除标记
                fieldName == "deleted" || fieldName == "is_deleted" || fieldName == "del_flag" -> {
                    if (fieldType == "BOOLEAN") " DEFAULT false" 
                    else if (fieldType.contains("INTEGER") || fieldType.contains("CHAR")) " DEFAULT 0" 
                    else ""
                }
                // 状态字段
                fieldName == "status" || fieldName == "state" -> {
                    if (fieldType.contains("VARCHAR")) {
                        // 默认为空值，实际值将通过字段的默认值处理
                        ""
                    } else {
                        // 对于数字类型状态默认为0
                        " DEFAULT 0" 
                    }
                }
                else -> ""
            }
        }
        
        /**
         * 生成插入SQL
         * @param entityClass 实体类
         * @param mapper 注解映射中间类
         * @return 插入SQL语句模板
         */
        fun generateInsertSql(entityClass: KClass<*>, mapper: SqlAnnotationMapper): String {
            val sqlInfo = SqlAnnotationMapperGenerator.extractSqlInfo(entityClass, mapper)
            val tableName = sqlInfo.tableName
            val columns = sqlInfo.columns
            
            if (tableName.isEmpty() || columns.isEmpty()) {
                throw IllegalArgumentException("无法生成SQL，表名或列信息为空")
            }
            
            val columnNames = columns.map { it.name }
            val placeholders = columns.mapIndexed { index, _ -> "$$${index + 1}" }
            
            return "INSERT INTO $tableName (${columnNames.joinToString(", ")}) VALUES (${placeholders.joinToString(", ")});"
        }
        
        /**
         * 生成更新SQL
         * @param entityClass 实体类
         * @param mapper 注解映射中间类
         * @return 更新SQL语句模板
         */
        fun generateUpdateSql(entityClass: KClass<*>, mapper: SqlAnnotationMapper): String {
            val sqlInfo = SqlAnnotationMapperGenerator.extractSqlInfo(entityClass, mapper)
            val tableName = sqlInfo.tableName
            val columns = sqlInfo.columns.filter { !it.isPrimaryKey }
            val primaryKeys = sqlInfo.columns.filter { it.isPrimaryKey }
            
            if (tableName.isEmpty() || columns.isEmpty() || primaryKeys.isEmpty()) {
                throw IllegalArgumentException("无法生成SQL，表名、列信息或主键为空")
            }
            
            val sb = StringBuilder()
            sb.append("UPDATE $tableName SET ")
            
            // 设置列
            val setStatements = columns.mapIndexed { index, column -> 
                "${column.name} = $$${index + 1}" 
            }
            sb.append(setStatements.joinToString(", "))
            
            // 添加条件
            sb.append(" WHERE ")
            val whereStatements = primaryKeys.mapIndexed { index, pk -> 
                "${pk.name} = $$${columns.size + index + 1}" 
            }
            sb.append(whereStatements.joinToString(" AND "))
            
            sb.append(";")
            return sb.toString()
        }
        
        /**
         * 生成删除SQL
         * @param entityClass 实体类
         * @param mapper 注解映射中间类
         * @return 删除SQL语句模板
         */
        fun generateDeleteSql(entityClass: KClass<*>, mapper: SqlAnnotationMapper): String {
            val sqlInfo = SqlAnnotationMapperGenerator.extractSqlInfo(entityClass, mapper)
            val tableName = sqlInfo.tableName
            val primaryKeys = sqlInfo.columns.filter { it.isPrimaryKey }
            
            if (tableName.isEmpty() || primaryKeys.isEmpty()) {
                throw IllegalArgumentException("无法生成SQL，表名或主键为空")
            }
            
            val sb = StringBuilder()
            sb.append("DELETE FROM $tableName WHERE ")
            
            // 添加条件
            val whereStatements = primaryKeys.mapIndexed { index, pk -> 
                "${pk.name} = $$${index + 1}" 
            }
            sb.append(whereStatements.joinToString(" AND "))
            
            sb.append(";")
            return sb.toString()
        }
        
        /**
         * 生成查询SQL
         * @param entityClass 实体类
         * @param mapper 注解映射中间类
         * @return 查询SQL语句
         */
        fun generateSelectSql(entityClass: KClass<*>, mapper: SqlAnnotationMapper): String {
            val sqlInfo = SqlAnnotationMapperGenerator.extractSqlInfo(entityClass, mapper)
            val tableName = sqlInfo.tableName
            val columns = sqlInfo.columns
            
            if (tableName.isEmpty() || columns.isEmpty()) {
                throw IllegalArgumentException("无法生成SQL，表名或列信息为空")
            }
            
            val columnNames = columns.map { it.name }
            
            return "SELECT ${columnNames.joinToString(", ")} FROM $tableName;"
        }
        
        /**
         * 生成根据主键查询SQL
         * @param entityClass 实体类
         * @param mapper 注解映射中间类
         * @return 根据主键查询SQL语句
         */
        fun generateSelectByPrimaryKeySql(entityClass: KClass<*>, mapper: SqlAnnotationMapper): String {
            val sqlInfo = SqlAnnotationMapperGenerator.extractSqlInfo(entityClass, mapper)
            val tableName = sqlInfo.tableName
            val columns = sqlInfo.columns
            val primaryKeys = sqlInfo.columns.filter { it.isPrimaryKey }
            
            if (tableName.isEmpty() || columns.isEmpty() || primaryKeys.isEmpty()) {
                throw IllegalArgumentException("无法生成SQL，表名、列信息或主键为空")
            }
            
            val columnNames = columns.map { it.name }
            
            val sb = StringBuilder()
            sb.append("SELECT ${columnNames.joinToString(", ")} FROM $tableName WHERE ")
            
            // 添加条件
            val whereStatements = primaryKeys.mapIndexed { index, pk -> 
                "${pk.name} = $$${index + 1}" 
            }
            sb.append(whereStatements.joinToString(" AND "))
            
            sb.append(";")
            return sb.toString()
        }
    }
} 