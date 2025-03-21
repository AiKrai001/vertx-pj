package org.aikrai.vertx.db.migration

import kotlin.reflect.KClass

/**
 * SQL注解映射中间类
 * 用于记录从哪些注解获取SQL生成所需的信息
 */
class SqlAnnotationMapper {
    /**
     * 实体类注解映射
     * 用于标识哪个类是实体类
     */
    var entityMapping: AnnotationMapping? = null
    
    /**
     * 表名映射信息
     */
    var tableName: AnnotationMapping? = null
    
    /**
     * 列名映射信息列表
     */
    var columnMappings: MutableList<ColumnMapping> = mutableListOf()
    
    /**
     * 主键映射信息
     */
    var primaryKeyMapping: AnnotationMapping? = null
    
    /**
     * 索引映射信息
     */
    var indexMapping: AnnotationMapping? = null
    
    /**
     * 枚举值映射信息
     */
    var enumValueMapping: AnnotationMapping? = null
    
    /**
     * 其他自定义映射
     */
    var customMappings: MutableMap<String, AnnotationMapping> = mutableMapOf()
    
    /**
     * 添加一个列映射
     */
    fun addColumnMapping(columnMapping: ColumnMapping) {
        columnMappings.add(columnMapping)
    }
    
    /**
     * 添加一个自定义映射
     */
    fun addCustomMapping(key: String, mapping: AnnotationMapping) {
        customMappings[key] = mapping
    }
}

/**
 * 注解映射类
 * 记录从哪个注解的哪个属性获取信息
 */
data class AnnotationMapping(
    /** 注解类 */
    val annotationClass: KClass<out Annotation>,
    /** 注解属性名 */
    val propertyName: String = ""
)

/**
 * 列映射信息
 */
data class ColumnMapping(
    /** 字段名称映射 */
    val nameMapping: AnnotationMapping,
    /** 字段类型映射，可选 */
    val typeMapping: AnnotationMapping? = null,
    /** 是否可为空映射，可选 */
    val nullableMapping: AnnotationMapping? = null,
    /** 默认值映射，可选 */
    val defaultValueMapping: AnnotationMapping? = null,
    /** 字段长度映射，可选 */
    val lengthMapping: AnnotationMapping? = null,
    /** 是否唯一映射，可选 */
    val uniqueMapping: AnnotationMapping? = null,
    /** 字段注释映射，可选 */
    val commentMapping: AnnotationMapping? = null
)