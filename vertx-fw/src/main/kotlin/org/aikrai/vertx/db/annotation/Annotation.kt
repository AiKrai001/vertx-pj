package org.aikrai.vertx.db.annotation

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.RetentionPolicy

@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.ANNOTATION_CLASS)
annotation class TableName(
  val value: String = "",
//  val schema: String = "",
//  val keepGlobalPrefix: Boolean = false,
//  val resultMap: String = "",
//  val autoResultMap: Boolean = false,
//  val excludeProperty: Array<String> = []
)

@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.ANNOTATION_CLASS)
annotation class TableId(val value: String = "", val type: IdType = IdType.NONE)

@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.ANNOTATION_CLASS)
annotation class TableField(
  val value: String = "",
//  val exist: Boolean = true,
//  val condition: String = "",
//  val update: String = "",
  val fill: FieldFill = FieldFill.DEFAULT,
//  val select: Boolean = true,
//  val keepGlobalFormat: Boolean = false,
//  val property: String = "",
//  val numericScale: String = ""
)

@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
annotation class EnumValue

/**
 * IdType
 * @property key Int
 * @constructor
 * @property AUTO IdType 数据库ID自增
 * @property NONE IdType 无状态
 * @property INPUT IdType 手动输入ID
 * @property ASSIGN_ID IdType 默认 全局唯一ID (数字类型)
 * @property ASSIGN_UUID IdType 全局唯一ID (字符串类型)
 */
enum class IdType(val key: Int) {
  AUTO(0),
  NONE(1),
  INPUT(2),
  ASSIGN_ID(3),
  ASSIGN_UUID(4)
}

enum class FieldFill {
  DEFAULT,
  INSERT,
  UPDATE,
  INSERT_UPDATE
}
