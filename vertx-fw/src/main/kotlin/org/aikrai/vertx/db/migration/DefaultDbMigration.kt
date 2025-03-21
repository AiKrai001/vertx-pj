package org.aikrai.vertx.db.migration

import java.io.File

/**
 * PostgreSQL数据库迁移工具的默认实现
 */
class DefaultDbMigration : DbMigration {
    private var entityPackage: String = ""
    private var sqlAnnotationMapper: SqlAnnotationMapper? = null
    private var pathToResources: String = "src/main/resources"
    private var migrationPath: String = "dbmigration"
    private var modelPath: String = "model"
    private var modelSuffix: String = ".model.xml"
    private var version: String? = null
    private var name: String? = null
    private var logToSystemOut: Boolean = true
    private var generateDropStatements: Boolean = false

    override fun setEntityPackage(packagePath: String) {
        this.entityPackage = packagePath
    }

    override fun setSqlAnnotationMapper(mapper: SqlAnnotationMapper) {
        this.sqlAnnotationMapper = mapper
    }

    override fun setPathToResources(pathToResources: String) {
        this.pathToResources = pathToResources
    }

    override fun setMigrationPath(migrationPath: String) {
        this.migrationPath = migrationPath
    }

    override fun setModelPath(modelPath: String) {
        this.modelPath = modelPath
    }

    override fun setModelSuffix(modelSuffix: String) {
        this.modelSuffix = modelSuffix
    }

    override fun setVersion(version: String) {
        this.version = version
    }

    override fun setName(name: String) {
        this.name = name
    }

    override fun setLogToSystemOut(logToSystemOut: Boolean) {
        this.logToSystemOut = logToSystemOut
    }
    
    override fun setGenerateDropStatements(generateDropStatements: Boolean) {
        this.generateDropStatements = generateDropStatements
    }

    override fun generateMigration(): String? {
        validateConfiguration()
        
        configureMigrationGenerator()
        
        // 将版本和名称设置到系统属性中，以便SqlMigrationGenerator能够读取
        if (version != null) {
            System.setProperty("ddl.migration.version", version!!)
        }
        if (name != null) {
            System.setProperty("ddl.migration.name", name!!)
        }
        
        // 设置是否生成删除语句
        System.setProperty("ddl.migration.generateDropStatements", generateDropStatements.toString())
        
        try {
            SqlMigrationGenerator.generateMigrations(entityPackage, sqlAnnotationMapper!!)
            return version
        } catch (e: Exception) {
            if (logToSystemOut) {
                println("生成迁移失败: ${e.message}")
                e.printStackTrace()
            }
            throw e
        } finally {
            // 清理系统属性
            if (version != null) {
                System.clearProperty("ddl.migration.version")
            }
            if (name != null) {
                System.clearProperty("ddl.migration.name")
            }
            System.clearProperty("ddl.migration.generateDropStatements")
        }
    }

    override fun generateInitMigration(): String? {
        validateConfiguration()
        
        configureMigrationGenerator()
        
        // 将版本和名称设置到系统属性中，以便SqlMigrationGenerator能够读取
        if (version != null) {
            System.setProperty("ddl.migration.version", version!!)
        }
        if (name != null) {
            System.setProperty("ddl.migration.name", name!!)
        }
        
        // 设置是否生成删除语句
        System.setProperty("ddl.migration.generateDropStatements", generateDropStatements.toString())
        
        try {
            // 修改目录结构，强制生成初始迁移
            val modelDir = File("${pathToResources}/${migrationPath}/${modelPath}")
            if (modelDir.exists()) {
                // 备份原有文件
                val backupDir = File("${pathToResources}/${migrationPath}/${modelPath}_backup_${System.currentTimeMillis()}")
                modelDir.renameTo(backupDir)
                if (logToSystemOut) {
                    println("已将现有模型文件备份到: ${backupDir.absolutePath}")
                }
            }
            
            // 生成初始迁移
            SqlMigrationGenerator.generateMigrations(entityPackage, sqlAnnotationMapper!!)
            return version
        } catch (e: Exception) {
            if (logToSystemOut) {
                println("生成初始迁移失败: ${e.message}")
                e.printStackTrace()
            }
            throw e
        } finally {
            // 清理系统属性
            if (version != null) {
                System.clearProperty("ddl.migration.version")
            }
            if (name != null) {
                System.clearProperty("ddl.migration.name")
            }
            System.clearProperty("ddl.migration.generateDropStatements")
        }
    }

    override fun migrationDirectory(): File {
        return File("${pathToResources}/${migrationPath}")
    }
    
    /**
     * 验证配置，确保必要的配置项已经设置
     */
    private fun validateConfiguration() {
        if (entityPackage.isEmpty()) {
            throw IllegalStateException("实体包路径未设置，请调用setEntityPackage()")
        }
        
        if (sqlAnnotationMapper == null) {
            throw IllegalStateException("SQL注解映射器未设置，请调用setSqlAnnotationMapper()")
        }

        if (sqlAnnotationMapper?.entityMapping == null) {
            throw IllegalStateException("实体注解映射未设置，请配置entityMapping")
        }
    }
    
    /**
     * 配置迁移生成器
     */
    private fun configureMigrationGenerator() {
        // 设置静态字段，以便SqlMigrationGenerator能够使用配置的路径
        SqlMigrationGenerator.setResourcePath(pathToResources)
        SqlMigrationGenerator.setMigrationPath("${pathToResources}/${migrationPath}")
        SqlMigrationGenerator.setModelPath("${pathToResources}/${migrationPath}/${modelPath}")
        SqlMigrationGenerator.setModelSuffix(modelSuffix)
        SqlMigrationGenerator.setLogToSystemOut(logToSystemOut)
        
        // 同时设置SqlAnnotationMapperGenerator的日志配置
        SqlAnnotationMapperGenerator.setLogToSystemOut(logToSystemOut)
    }
} 