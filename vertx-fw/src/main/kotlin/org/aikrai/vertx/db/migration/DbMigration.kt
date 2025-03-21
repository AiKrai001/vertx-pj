package org.aikrai.vertx.db.migration

import java.io.File

/**
 * 生成PostgreSQL DDL迁移脚本，基于实体类及其注解的变更。
 *
 * <p>
 * 通常在开发人员对模型进行了一组更改后，作为测试阶段的主要方法运行。
 * </p>
 *
 * <h3>示例: 运行生成PostgreSQL迁移脚本</h3>
 *
 * <pre>{@code
 *
 *    val migration = DbMigration.create()
 *
 *    // 可选：指定版本和名称
 *    migration.setName("添加用户表索引")
 *
 *    // 设置实体包路径
 *    migration.setEntityPackage("org.aikrai.vertx.entity")
 *
 *    // 设置SQL注解映射器
 *    migration.setSqlAnnotationMapper(createMapper())
 *
 *    // 生成迁移
 *    migration.generateMigration()
 *
 * }</pre>
 */
interface DbMigration {

    companion object {
        /**
         * 创建DbMigration实现实例
         */
        fun create(): DbMigration {
            return DefaultDbMigration()
        }
    }

    /**
     * 设置实体类所在的包路径
     */
    fun setEntityPackage(packagePath: String)

    /**
     * 设置SQL注解映射器
     */
    fun setSqlAnnotationMapper(mapper: SqlAnnotationMapper)

    /**
     * 设置资源文件路径
     * <p>
     * 默认为Maven风格的'src/main/resources'
     */
    fun setPathToResources(pathToResources: String)

    /**
     * 设置迁移文件生成的路径（默认为"dbmigration"）
     */
    fun setMigrationPath(migrationPath: String)

    /**
     * 设置模型文件生成的路径（默认为"model"）
     */
    fun setModelPath(modelPath: String)

    /**
     * 设置模型文件后缀（默认为".model.xml"）
     */
    fun setModelSuffix(modelSuffix: String)

    /**
     * 设置迁移的版本号
     */
    fun setVersion(version: String)

    /**
     * 设置迁移的名称
     */
    fun setName(name: String)

    /**
     * 设置是否输出日志到控制台（默认为true）
     */
    fun setLogToSystemOut(logToSystemOut: Boolean)
    
    /**
     * 设置是否生成删除语句（默认为false）
     * <p>
     * 如果设置为false，生成的SQL中将不包含任何DROP语句。
     * 但.model.xml文件中仍会记录删除表和字段的信息。
     * </p>
     */
    fun setGenerateDropStatements(generateDropStatements: Boolean)

    /**
     * 生成下一次迁移SQL脚本和相关模型XML
     * <p>
     * 不会实际运行迁移或DDL脚本，只是生成它们。
     * </p>
     *
     * @return 生成的迁移版本或null（如果没有变更）
     */
    fun generateMigration(): String?

    /**
     * 生成包含所有变更的"初始"迁移
     * <p>
     * "初始"迁移只能在尚未对其运行任何先前迁移的数据库上执行和使用。
     * </p>
     *
     * @return 生成的迁移版本
     */
    fun generateInitMigration(): String?

    /**
     * 返回迁移主目录
     */
    fun migrationDirectory(): File
}