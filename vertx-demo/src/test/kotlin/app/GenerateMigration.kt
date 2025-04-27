package app

import org.aikrai.vertx.db.annotation.TableField
import org.aikrai.vertx.db.annotation.TableId
import org.aikrai.vertx.db.annotation.TableName
import org.aikrai.vertx.db.annotation.EnumValue
import org.aikrai.vertx.db.annotation.TableFieldComment
import org.aikrai.vertx.db.migration.AnnotationMapping
import org.aikrai.vertx.db.migration.ColumnMapping
import org.aikrai.vertx.db.migration.DbMigration
import org.aikrai.vertx.db.migration.SqlAnnotationMapper

/**
 * PostgreSQL数据库迁移生成工具
 */
object GenerateMigration {
  /**
   * 生成数据库迁移脚本
   */
  @JvmStatic
  fun main(args: Array<String>) {
    try {
      // 创建SQL注解映射器
      val mapper = createSqlAnnotationMapper()

      // 设置迁移生成器
      val dbMigration = DbMigration.create()
      dbMigration.setPathToResources("vertx-demo/src/main/resources")
      dbMigration.setEntityPackage("app.data.domain") // 指定实体类包路径
      dbMigration.setSqlAnnotationMapper(mapper)
      dbMigration.setGenerateDropStatements(false) // 不生成删除语句

      // 生成迁移
      val migrationVersion = dbMigration.generateMigration()
      println("生成的迁移版本: $migrationVersion")
    } catch (e: Exception) {
      println("生成迁移失败: ${e.message}")
      e.printStackTrace()
    }
  }

  /**
   * 创建SQL注解映射器
   * 根据项目中使用的注解配置映射关系
   */
  private fun createSqlAnnotationMapper(): SqlAnnotationMapper {
    val mapper = SqlAnnotationMapper()

    // 设置实体类注解映射
    mapper.entityMapping = AnnotationMapping(
      annotationClass = TableName::class,
    )
    
    // 设置表名映射
    mapper.tableName = AnnotationMapping(
      annotationClass = TableName::class,
      propertyName = "value"
    )

    // 设置列映射
    mapper.addColumnMapping(
      ColumnMapping(
        nameMapping = AnnotationMapping(
          annotationClass = TableField::class,
          propertyName = "value"
        ),
        typeMapping = AnnotationMapping(
          annotationClass = TableField::class,
          propertyName = "type"
        ),
        nullableMapping = AnnotationMapping(
          annotationClass = TableField::class,
          propertyName = "nullable"
        ),
        defaultValueMapping = AnnotationMapping(
          annotationClass = TableField::class,
          propertyName = "default"
        ),
        lengthMapping = AnnotationMapping(
          annotationClass = TableField::class,
          propertyName = "length"
        ),
        uniqueMapping = AnnotationMapping(
          annotationClass = TableField::class,
          propertyName = "unique"
        ),
        commentMapping = AnnotationMapping(
          annotationClass = TableFieldComment::class,
          propertyName = "value"
        )
      )
    )

    // 设置主键映射
    mapper.primaryKeyMapping = AnnotationMapping(
      annotationClass = TableId::class,
    )
    
    // 设置索引映射
//    mapper.indexMapping = AnnotationMapping(
//      annotationClass = TableIndex::class,
//      propertyName = "name"
//    )
    
    // 设置枚举值映射
    mapper.enumValueMapping = AnnotationMapping(
      annotationClass = EnumValue::class,
      propertyName = "value"
    )

    return mapper
  }
}