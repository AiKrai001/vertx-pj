package org.aikrai.vertx.db.migration

import org.reflections.Reflections
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.reflect.KClass

/**
 * PostgreSQL 数据库迁移工具
 * 根据实体类及其注解生成数据库迁移脚本和模型文件
 */
class SqlMigrationGenerator {
    companion object {
        private var RESOURCE_PATH = "src/main/resources"
        private var MIGRATION_PATH = "$RESOURCE_PATH/dbmigration"
        private var MODEL_PATH = "$MIGRATION_PATH/model"
        private var MODEL_SUFFIX = ".model.xml"
        private const val INITIAL_VERSION = "1.0"
        private const val INITIAL_SUFFIX = "__initial"
        private var LOG_TO_SYSTEM_OUT = true
        
        /**
         * 设置资源目录路径
         */
        fun setResourcePath(path: String) {
            RESOURCE_PATH = path
            // 更新依赖于资源路径的其他路径
            if (!MIGRATION_PATH.startsWith(path)) {
                MIGRATION_PATH = "$path/dbmigration"
            }
            if (!MODEL_PATH.startsWith(MIGRATION_PATH)) {
                MODEL_PATH = "$MIGRATION_PATH/model"
            }
        }
        
        /**
         * 设置迁移目录路径
         */
        fun setMigrationPath(path: String) {
            MIGRATION_PATH = path
            // 更新依赖于迁移路径的模型路径
            if (!MODEL_PATH.startsWith(path)) {
                MODEL_PATH = "$path/model"
            }
        }
        
        /**
         * 设置模型目录路径
         */
        fun setModelPath(path: String) {
            MODEL_PATH = path
        }
        
        /**
         * 设置模型文件后缀
         */
        fun setModelSuffix(suffix: String) {
            MODEL_SUFFIX = suffix
        }
        
        /**
         * 设置是否输出日志到控制台
         */
        fun setLogToSystemOut(log: Boolean) {
            LOG_TO_SYSTEM_OUT = log
        }
        
        /**
         * 记录日志信息
         */
        private fun log(message: String) {
            if (LOG_TO_SYSTEM_OUT) {
                println("DbMigration> $message")
            }
        }
        
        /**
         * 生成数据库迁移脚本
         * @param entityClasses 实体类列表
         * @param mapper 注解映射中间类
         */
        fun generateMigrations(entityPackage: String, mapper: SqlAnnotationMapper) {
            // 确保日志路径存在
            createDirectories()
            
            log("开始扫描实体类...")
            val entityClasses = scanEntityClasses(mapper, entityPackage)
            
            if (entityClasses.isEmpty()) {
                throw IllegalArgumentException("未找到任何实体类，请检查包路径: $entityPackage")
            }
            
            log("找到 ${entityClasses.size} 个实体类，准备验证...")
            
            // 先验证所有实体类是否能够正确解析，如果有错误就立刻抛出异常
            validateEntityClasses(entityClasses, mapper)
            
            // 检查是否是初始迁移
            val isInitialMigration = isInitialMigration()
            
            if (isInitialMigration) {
                log("开始生成初始迁移...")
                generateInitialMigration(entityClasses, mapper)
            } else {
                log("开始生成差异迁移...")
                generateDiffMigration(entityClasses, mapper)
            }
        }
        
        /**
         * 验证所有实体类是否可以正确解析SQL信息
         */
        private fun validateEntityClasses(entityClasses: List<KClass<*>>, mapper: SqlAnnotationMapper) {
            val errorMessages = mutableListOf<String>()
            
            entityClasses.forEach { entityClass ->
                try {
                    val sqlInfo = SqlAnnotationMapperGenerator.extractSqlInfo(entityClass, mapper)
                    
                    // 验证表名
                    if (sqlInfo.tableName.isEmpty()) {
                        errorMessages.add("实体类 ${entityClass.simpleName} 的表名为空")
                    }
                    
                    // 验证是否有列
                    if (sqlInfo.columns.isEmpty()) {
                        errorMessages.add("实体类 ${entityClass.simpleName} 没有任何列信息")
                    }
                } catch (e: Exception) {
                    // 记录错误信息
                    errorMessages.add("验证实体类 ${entityClass.simpleName} 失败: ${e.message}")
                }
            }
            
            // 如果有任何错误，抛出异常并停止生成
            if (errorMessages.isNotEmpty()) {
                val errorMessage = "验证实体类时发现以下错误:\n" + errorMessages.joinToString("\n")
                throw IllegalArgumentException(errorMessage)
            }
            
            log("所有实体类验证通过，可以生成迁移")
        }
        
        /**
         * 扫描包路径下标记了@TableName注解的实体类
         */
        private fun scanEntityClasses(mapper: SqlAnnotationMapper, packagePath: String): List<KClass<*>> {
            val entityAnnotationClass = mapper.entityMapping?.annotationClass?.java
                ?: throw IllegalStateException("实体类注解映射未设置，请配置SqlAnnotationMapper.entityMapping")
            val reflections = Reflections(packagePath)
            val entityClasses = reflections.getTypesAnnotatedWith(entityAnnotationClass)
            return entityClasses.map { it.kotlin }
        }
        
        /**
         * 创建必要的目录
         */
        private fun createDirectories() {
            Files.createDirectories(Paths.get(MIGRATION_PATH))
            Files.createDirectories(Paths.get(MODEL_PATH))
        }
        
        /**
         * 检查是否是初始迁移
         */
        private fun isInitialMigration(): Boolean {
            val modelDir = File(MODEL_PATH)
            return modelDir.listFiles()?.isEmpty() ?: true
        }
        
        /**
         * 生成初始迁移
         */
        private fun generateInitialMigration(entityClasses: List<KClass<*>>, mapper: SqlAnnotationMapper) {
            val version = "$INITIAL_VERSION$INITIAL_SUFFIX"
            val sqlBuilder = StringBuilder()
            sqlBuilder.append("-- apply changes\n")
            
            // 创建XML模型文档
            val modelDocument = createModelDocument()
            val changeSetElement = modelDocument.createElement("changeSet")
            changeSetElement.setAttribute("type", "apply")
            modelDocument.documentElement.appendChild(changeSetElement)
            
            // 为每个实体类生成创建表SQL
            entityClasses.forEach { entityClass ->
                try {
                    val sqlInfo = SqlAnnotationMapperGenerator.extractSqlInfo(entityClass, mapper)
                    val createTableSql = SqlGenerator.generateCreateTableSql(entityClass, mapper)
                    
                    // 添加到SQL文件
                    sqlBuilder.append("$createTableSql\n\n")
                    
                    // 添加到模型文件
                    addCreateTableToModel(modelDocument, changeSetElement, sqlInfo)
                    
                    log("已生成 ${entityClass.simpleName} 的创建表SQL")
                } catch (e: Exception) {
                    // 由于前面已经验证过所有实体类，这里不应该再有错误
                    // 但为了健壮性，还是添加错误处理
                    log("警告: 处理实体类 ${entityClass.simpleName} 时出错: ${e.message}")
                    sqlBuilder.append("-- 处理实体类 ${entityClass.simpleName} 时出错: ${e.message}\n\n")
                }
            }
            
            // 写入SQL文件
            val sqlFileName = "$MIGRATION_PATH/$version.sql"
            File(sqlFileName).writeText(sqlBuilder.toString())
            log("生成SQL文件: $sqlFileName")
            
            // 写入模型文件
            val modelFileName = "$MODEL_PATH/$version$MODEL_SUFFIX"
            writeModelToFile(modelDocument, modelFileName)
            log("生成模型文件: $modelFileName")
            
            log("初始迁移生成完成: $version")
        }
        
        /**
         * 生成差异迁移
         */
        private fun generateDiffMigration(entityClasses: List<KClass<*>>, mapper: SqlAnnotationMapper) {
            // 获取最新的模型文件
            val latestModelFile = findLatestModelFile()
            if (latestModelFile == null) {
                log("未找到现有模型文件，将生成初始迁移")
                generateInitialMigration(entityClasses, mapper)
                return
            }
            
            log("找到最新模型文件: ${latestModelFile.name}")
            
            // 解析最新的模型文件并构建完整模型
            val allTables = buildFullModel(latestModelFile)
            
            // 获取当前版本号和生成新版本号
            val currentVersion = extractVersionFromFileName(latestModelFile.name)
            if (!currentVersion.contains(".")) {
                log("错误: 提取的当前版本号 '$currentVersion' 格式不正确，将使用默认的 '1.0'")
                val nextVersion = findNextVersionNumber("1.0")
                log("基于默认版本找到下一个版本号: $nextVersion")
                continueWithMigration(nextVersion, allTables, entityClasses, mapper)
                return
            }
            
            // 检查是否存在已有的版本号，找出当前最大版本号
            val nextVersion = findNextVersionNumber(currentVersion)
            log("当前版本: $currentVersion, 新版本: $nextVersion")
            
            // 继续生成迁移
            continueWithMigration(nextVersion, allTables, entityClasses, mapper)
        }
        
        /**
         * 继续生成迁移的逻辑
         */
        private fun continueWithMigration(
            nextVersion: String,
            allTables: Map<String, SqlInfo>,
            entityClasses: List<KClass<*>>,
            mapper: SqlAnnotationMapper
        ) {
            // 生成当前实体类的模型信息
            val currentTables = mutableMapOf<String, SqlInfo>()
            entityClasses.forEach { entityClass ->
                try {
                    val sqlInfo = SqlAnnotationMapperGenerator.extractSqlInfo(entityClass, mapper)
                    currentTables[sqlInfo.tableName] = sqlInfo
                    log("已提取 ${entityClass.simpleName} 的模型信息，表名: ${sqlInfo.tableName}")
                } catch (e: Exception) {
                    // 由于前面已验证，这里不应该再有错误
                    log("警告: 处理实体类 ${entityClass.simpleName} 时出错: ${e.message}")
                }
            }
            
            // 比较差异并生成SQL
            log("比较数据库结构差异...")
            val diffResult = compareTables(allTables, currentTables)
            
            // 如果没有差异，不生成迁移文件
            if (diffResult.isEmpty()) {
                log("没有发现数据库结构变化，不生成迁移文件")
                return
            }
            
            log("发现以下变更:")
            if (diffResult.tablesToCreate.isNotEmpty()) {
                log("- 新增表: ${diffResult.tablesToCreate.map { it.tableName }.joinToString(", ")}")
            }
            if (diffResult.tablesToDrop.isNotEmpty()) {
                log("- 删除表: ${diffResult.tablesToDrop.joinToString(", ")}")
            }
            if (diffResult.columnsToAdd.isNotEmpty()) {
                log("- 新增列: ${diffResult.columnsToAdd.entries.joinToString(", ") { "${it.key}(${it.value.size}列)" }}")
            }
            if (diffResult.columnsToDrop.isNotEmpty()) {
                log("- 删除列: ${diffResult.columnsToDrop.entries.joinToString(", ") { "${it.key}(${it.value.size}列)" }}")
            }
            if (diffResult.columnsToAlter.isNotEmpty()) {
                log("- 修改列: ${diffResult.columnsToAlter.entries.joinToString(", ") { "${it.key}(${it.value.size}列)" }}")
            }
            
            // 创建新的模型文档，只包含变更
            val modelDocument = createModelDocument()
            val changeSetElement = modelDocument.createElement("changeSet")
            changeSetElement.setAttribute("type", "apply")
            modelDocument.documentElement.appendChild(changeSetElement)
            
            val sqlBuilder = StringBuilder()
            sqlBuilder.append("-- apply changes\n")
            
            // 处理创建表
            processCreateTables(diffResult.tablesToCreate, sqlBuilder, modelDocument, changeSetElement)
            
            // 处理删除表
            processDropTables(diffResult.tablesToDrop, sqlBuilder, modelDocument, changeSetElement)
            
            // 处理添加列
            processAddColumns(diffResult.columnsToAdd, sqlBuilder, modelDocument, changeSetElement)
            
            // 处理删除列
            processDropColumns(diffResult.columnsToDrop, sqlBuilder, modelDocument, changeSetElement)
            
            // 处理修改列
            processAlterColumns(diffResult.columnsToAlter, sqlBuilder, modelDocument, changeSetElement)
            
            // 写入SQL文件
            val sqlFileName = "$MIGRATION_PATH/$nextVersion.sql"
            val sqlFile = File(sqlFileName)
            if (sqlFile.exists()) {
                log("警告: SQL文件 $sqlFileName 已存在，将被覆盖")
            }
            sqlFile.writeText(sqlBuilder.toString())
            log("生成SQL文件: $sqlFileName")
            
            // 写入模型文件
            val modelFileName = "$MODEL_PATH/$nextVersion$MODEL_SUFFIX"
            val modelFile = File(modelFileName)
            if (modelFile.exists()) {
                log("警告: 模型文件 $modelFileName 已存在，将被覆盖")
            }
            writeModelToFile(modelDocument, modelFileName)
            log("生成模型文件: $modelFileName")
            
            log("差异迁移生成完成: $nextVersion")
        }
        
        /**
         * 查找下一个可用的版本号
         */
        private fun findNextVersionNumber(baseVersion: String): String {
            // 打印基础版本，便于调试
            log("查找下一个版本号，基础版本: $baseVersion")
            
            val parts = baseVersion.split(".")
            if (parts.size != 2) {
                log("无效的基础版本格式，使用默认值 1.1")
                return "1.1"
            }
            
            val major = parts[0].toInt()
            val minor = parts[1].toInt()
            
            // 扫描现有文件，找出最大版本号
            val modelDir = File(MODEL_PATH)
            val sqlDir = File(MIGRATION_PATH)
            
            // 获取所有版本号
            val existingVersions = mutableSetOf<String>()
            
            // 检查模型文件
            val modelFiles = modelDir.listFiles()
            if (modelFiles != null) {
                for (file in modelFiles) {
                    if (file.isFile && file.name.endsWith(MODEL_SUFFIX)) {
                        val fileVersion = extractVersionFromFileName(file.name)
                        if (!fileVersion.contains("__")) { // 排除初始版本
                            existingVersions.add(fileVersion)
                            log("发现模型文件版本: $fileVersion (${file.name})")
                        }
                    }
                }
            }
            
            // 检查SQL文件
            val sqlFiles = sqlDir.listFiles()
            if (sqlFiles != null) {
                for (file in sqlFiles) {
                    if (file.isFile && file.name.endsWith(".sql") && !file.name.contains("__")) {
                        val fileVersion = extractVersionFromFileName(file.name)
                        existingVersions.add(fileVersion)
                        log("发现SQL文件版本: $fileVersion (${file.name})")
                    }
                }
            }
            
            // 找出最大版本号
            var maxMajor = major
            var maxMinor = minor
            
            for (version in existingVersions) {
                try {
                    val vParts = version.split(".")
                    if (vParts.size == 2) {
                        val vMajor = vParts[0].toInt()
                        val vMinor = vParts[1].toInt()
                        
                        if (vMajor > maxMajor || (vMajor == maxMajor && vMinor > maxMinor)) {
                            maxMajor = vMajor
                            maxMinor = vMinor
                            log("更新最大版本: $vMajor.$vMinor")
                        }
                    }
                } catch (e: NumberFormatException) {
                    log("警告: 版本号 '$version' 中包含非数字部分，忽略")
                }
            }
            
            // 生成下一个候选版本号
            var candidateVersion: String
            var candidateMinor = maxMinor + 1
            
            do {
                candidateVersion = "$maxMajor.$candidateMinor"
                
                // 检查此版本号是否已存在对应的文件
                val modelFileExists = File("$MODEL_PATH/$candidateVersion.model.xml").exists()
                val sqlFileExists = File("$MIGRATION_PATH/$candidateVersion.sql").exists()
                
                if (modelFileExists || sqlFileExists) {
                    log("版本号 $candidateVersion 已存在文件，尝试下一个版本")
                    candidateMinor++
                } else {
                    break
                }
            } while (true)
            
            log("确定下一个版本号: $candidateVersion")
            return candidateVersion
        }
        
        /**
         * 处理创建表
         */
        private fun processCreateTables(
            tablesToCreate: List<SqlInfo>,
            sqlBuilder: StringBuilder,
            doc: Document,
            changeSetElement: Element
        ) {
            tablesToCreate.forEach { sqlInfo ->
                // 生成SQL
                sqlBuilder.append(generateCreateTableSql(sqlInfo))
                sqlBuilder.append("\n\n")
                
                // 添加到XML
                addCreateTableToModel(doc, changeSetElement, sqlInfo)
            }
        }
        
        /**
         * 处理删除表
         */
        private fun processDropTables(
            tablesToDrop: List<String>, 
            sqlBuilder: StringBuilder,
            doc: Document,
            changeSetElement: Element
        ) {
            // 检查是否需要生成删除语句
            val generateDropStatements = System.getProperty("ddl.migration.generateDropStatements", "false").toBoolean()
            
            tablesToDrop.forEach { tableName ->
                // 只有当配置为生成删除语句时才添加到SQL中
                if (generateDropStatements) {
                    // 生成SQL
                    sqlBuilder.append("drop table if exists $tableName cascade;\n")
                }
                
                // 无论如何都添加到XML中，以记录历史变更
                val dropTableElement = doc.createElement("dropTable")
                dropTableElement.setAttribute("name", tableName)
                changeSetElement.appendChild(dropTableElement)
            }
            
            if (generateDropStatements && tablesToDrop.isNotEmpty()) {
                sqlBuilder.append("\n")
            }
        }
        
        /**
         * 处理添加列
         */
        private fun processAddColumns(
            columnsToAdd: Map<String, List<ColumnInfo>>,
            sqlBuilder: StringBuilder,
            doc: Document,
            changeSetElement: Element
        ) {
            if (columnsToAdd.isNotEmpty()) {
                sqlBuilder.append("-- apply alter tables\n")
            }
            
            columnsToAdd.forEach { (tableName, columns) ->
                // 为每个表创建一个addColumn元素
                val addColumnElement = doc.createElement("addColumn")
                addColumnElement.setAttribute("tableName", tableName)
                changeSetElement.appendChild(addColumnElement)
                
                columns.forEach { column ->
                    // 生成SQL
                    val nullable = if (column.nullable) "" else " not null"
                    val defaultValue = if (column.defaultValue.isNotEmpty()) " default ${column.defaultValue}" else ""
                    sqlBuilder.append("alter table $tableName\n    add column ${column.name} ${column.type}$defaultValue$nullable;\n")
                    
                    // 添加到XML
                    val columnElement = doc.createElement("column")
                    columnElement.setAttribute("name", column.name)
                    columnElement.setAttribute("type", column.type)
                    if (column.defaultValue.isNotEmpty()) {
                        columnElement.setAttribute("defaultValue", column.defaultValue)
                    }
                    if (!column.nullable) {
                        columnElement.setAttribute("notnull", "true")
                    }
                    addColumnElement.appendChild(columnElement)
                }
                
                sqlBuilder.append("\n")
            }
        }
        
        /**
         * 处理删除列
         */
        private fun processDropColumns(
            columnsToDrop: Map<String, List<String>>,
            sqlBuilder: StringBuilder,
            doc: Document,
            changeSetElement: Element
        ) {
            // 检查是否需要生成删除语句
            val generateDropStatements = System.getProperty("ddl.migration.generateDropStatements", "false").toBoolean()
            
            var addedSql = false
            
            columnsToDrop.forEach { (tableName, columns) ->
                if (columns.isNotEmpty()) {
                    val alterTableElement = doc.createElement("alterTable")
                    alterTableElement.setAttribute("name", tableName)
                    changeSetElement.appendChild(alterTableElement)
                    
                    columns.forEach { columnName ->
                        // 只有当配置为生成删除语句时才添加到SQL中
                        if (generateDropStatements) {
                            sqlBuilder.append("alter table $tableName drop column if exists $columnName;\n")
                            addedSql = true
                        }
                        
                        // 无论如何都添加到XML中，以记录历史变更
                        val dropColumnElement = doc.createElement("dropColumn")
                        dropColumnElement.setAttribute("columnName", columnName)
                        alterTableElement.appendChild(dropColumnElement)
                    }
                }
            }
            
            if (addedSql) {
                sqlBuilder.append("\n")
            }
        }
        
        /**
         * 处理修改列
         */
        private fun processAlterColumns(
            columnsToAlter: Map<String, List<Pair<ColumnInfo, ColumnInfo>>>,
            sqlBuilder: StringBuilder,
            doc: Document,
            changeSetElement: Element
        ) {
            columnsToAlter.forEach { (tableName, columns) ->
                // 为每个表创建一个alterColumn元素
                val alterColumnElement = doc.createElement("alterColumn")
                alterColumnElement.setAttribute("tableName", tableName)
                changeSetElement.appendChild(alterColumnElement)
                
                columns.forEach { (oldColumn, newColumn) ->
                    // 生成SQL和XML
                    
                    // 如果类型不同，修改类型
                    if (oldColumn.type != newColumn.type) {
                        sqlBuilder.append("alter table $tableName alter column ${oldColumn.name} type ${newColumn.type} using ${oldColumn.name}::${newColumn.type};\n")
                        
                        val columnElement = doc.createElement("column")
                        columnElement.setAttribute("name", oldColumn.name)
                        columnElement.setAttribute("type", newColumn.type)
                        columnElement.setAttribute("currentType", oldColumn.type)
                        alterColumnElement.appendChild(columnElement)
                    }
                    
                    // 如果可空性不同，修改可空性
                    if (oldColumn.nullable != newColumn.nullable) {
                        if (newColumn.nullable) {
                            sqlBuilder.append("alter table $tableName alter column ${oldColumn.name} drop not null;\n")
                            
                            val columnElement = doc.createElement("column")
                            columnElement.setAttribute("name", oldColumn.name)
                            columnElement.setAttribute("notnull", "false")
                            columnElement.setAttribute("currentNotnull", "true")
                            alterColumnElement.appendChild(columnElement)
                        } else {
                            sqlBuilder.append("alter table $tableName alter column ${oldColumn.name} set not null;\n")
                            
                            val columnElement = doc.createElement("column")
                            columnElement.setAttribute("name", oldColumn.name)
                            columnElement.setAttribute("notnull", "true")
                            columnElement.setAttribute("currentNotnull", "false")
                            alterColumnElement.appendChild(columnElement)
                        }
                    }
                    
                    // 如果默认值不同，修改默认值
                    if (oldColumn.defaultValue != newColumn.defaultValue) {
                        if (newColumn.defaultValue.isEmpty()) {
                            sqlBuilder.append("alter table $tableName alter column ${oldColumn.name} drop default;\n")
                            
                            val columnElement = doc.createElement("column")
                            columnElement.setAttribute("name", oldColumn.name)
                            columnElement.setAttribute("dropDefault", "true")
                            alterColumnElement.appendChild(columnElement)
                        } else {
                            sqlBuilder.append("alter table $tableName alter column ${oldColumn.name} set default ${newColumn.defaultValue};\n")
                            
                            val columnElement = doc.createElement("column")
                            columnElement.setAttribute("name", oldColumn.name)
                            columnElement.setAttribute("defaultValue", newColumn.defaultValue)
                            columnElement.setAttribute("currentDefaultValue", oldColumn.defaultValue)
                            alterColumnElement.appendChild(columnElement)
                        }
                    }
                }
                
                sqlBuilder.append("\n")
            }
        }
        
        /**
         * 从过去的变更中构建完整的数据库模型
         */
        private fun buildFullModel(latestModelFile: File): Map<String, SqlInfo> {
            val allTables = mutableMapOf<String, SqlInfo>()
            val modelDir = File(MODEL_PATH)
            
            // 获取所有模型文件并按版本排序
            val modelFiles = modelDir.listFiles { file -> 
                file.isFile && file.name.endsWith(".model.xml")
            } ?: return allTables
            
            // 按版本号排序
            val sortedFiles = sortModelFilesByVersion(modelFiles)
            
            println("按版本排序后的模型文件:")
            sortedFiles.forEach { println(" - ${it.name} (${extractVersionFromFileName(it.name)})") }
            
            // 按顺序应用每个变更
            for (file in sortedFiles) {
                try {
                    val modelDoc = parseModelFile(file)
                    applyChangesToModel(modelDoc, allTables)
                    println("已应用模型变更: ${file.name}")
                } catch (e: Exception) {
                    println("处理模型文件 ${file.name} 时出错: ${e.message}")
                }
            }
            
            // 打印当前模型状态
            println("当前数据库模型状态:")
            allTables.forEach { (tableName, sqlInfo) ->
                println(" - 表: $tableName, 列数: ${sqlInfo.columns.size}")
            }
            
            return allTables
        }
        
        /**
         * 按版本号排序模型文件
         */
        private fun sortModelFilesByVersion(files: Array<File>): List<File> {
            // 构建文件映射和版本值
            val fileInfos = files.map { file ->
                val version = extractVersionFromFileName(file.name)
                val versionValue = calculateVersionValue(version)
                Triple(file, version, versionValue)
            }
            
            // 排序：初始化文件在前，其他按版本号排序
            return fileInfos.sortedWith { a, b ->
                val aVersion = a.second
                val bVersion = b.second
                
                // 初始化文件总是最先处理
                if (aVersion.contains("__") && !bVersion.contains("__")) {
                    -1
                } else if (!aVersion.contains("__") && bVersion.contains("__")) {
                    1
                } else {
                    // 按版本号比较
                    a.third.compareTo(b.third)
                }
            }.map { it.first }
        }
        
        /**
         * 将模型文件中的变更应用到模型
         */
        private fun applyChangesToModel(doc: Document, allTables: MutableMap<String, SqlInfo>) {
            val changeSetNodes = doc.getElementsByTagName("changeSet")
            
            for (i in 0 until changeSetNodes.length) {
                val changeSetNode = changeSetNodes.item(i) as Element
                val changeType = changeSetNode.getAttribute("type")
                
                if (changeType == "apply") {
                    // 处理创建表
                    val createTableNodes = changeSetNode.getElementsByTagName("createTable")
                    for (j in 0 until createTableNodes.length) {
                        val createTableNode = createTableNodes.item(j) as Element
                        val tableName = createTableNode.getAttribute("name")
                        val sqlInfo = SqlInfo(tableName = tableName)
                        
                        // 解析列信息
                        val columnNodes = createTableNode.getElementsByTagName("column")
                        for (k in 0 until columnNodes.length) {
                            val columnNode = columnNodes.item(k) as Element
                            val columnInfo = parseColumnFromXml(columnNode)
                            sqlInfo.columns.add(columnInfo)
                            
                            if (columnInfo.isPrimaryKey) {
                                sqlInfo.primaryKeys.add(columnInfo.name)
                            }
                        }
                        
                        allTables[tableName] = sqlInfo
                    }
                    
                    // 处理添加列
                    val addColumnNodes = changeSetNode.getElementsByTagName("addColumn")
                    for (j in 0 until addColumnNodes.length) {
                        val addColumnNode = addColumnNodes.item(j) as Element
                        val tableName = addColumnNode.getAttribute("tableName")
                        val table = allTables[tableName] ?: continue
                        
                        val columnNodes = addColumnNode.getElementsByTagName("column")
                        for (k in 0 until columnNodes.length) {
                            val columnNode = columnNodes.item(k) as Element
                            val columnInfo = parseColumnFromXml(columnNode)
                            table.columns.add(columnInfo)
                            
                            if (columnInfo.isPrimaryKey) {
                                table.primaryKeys.add(columnInfo.name)
                            }
                        }
                    }
                    
                    // 处理删除列
                    val dropColumnNodes = changeSetNode.getElementsByTagName("dropColumn")
                    for (j in 0 until dropColumnNodes.length) {
                        val dropColumnNode = dropColumnNodes.item(j) as Element
                        val tableName = dropColumnNode.getAttribute("tableName")
                        val table = allTables[tableName] ?: continue
                        
                        val columnNodes = dropColumnNode.getElementsByTagName("column")
                        for (k in 0 until columnNodes.length) {
                            val columnNode = columnNodes.item(k) as Element
                            val columnName = columnNode.getAttribute("name")
                            
                            // 删除列
                            table.columns.removeIf { it.name == columnName }
                            table.primaryKeys.remove(columnName)
                        }
                    }
                    
                    // 处理修改列
                    val alterColumnNodes = changeSetNode.getElementsByTagName("alterColumn")
                    for (j in 0 until alterColumnNodes.length) {
                        val alterColumnNode = alterColumnNodes.item(j) as Element
                        val tableName = alterColumnNode.getAttribute("tableName")
                        val table = allTables[tableName] ?: continue
                        
                        val columnNodes = alterColumnNode.getElementsByTagName("column")
                        for (k in 0 until columnNodes.length) {
                            val columnNode = columnNodes.item(k) as Element
                            val columnName = columnNode.getAttribute("name")
                            
                            // 查找并更新列
                            val column = table.columns.find { it.name == columnName }
                            
                            if (column != null) {
                                // 更新类型
                                if (columnNode.hasAttribute("type")) {
                                    column.type = columnNode.getAttribute("type")
                                }
                                
                                // 更新可空性
                                if (columnNode.hasAttribute("notnull")) {
                                    column.nullable = columnNode.getAttribute("notnull") != "true"
                                }
                                
                                // 更新默认值
                                if (columnNode.hasAttribute("defaultValue")) {
                                    column.defaultValue = columnNode.getAttribute("defaultValue")
                                } else if (columnNode.hasAttribute("dropDefault") && 
                                           columnNode.getAttribute("dropDefault") == "true") {
                                    column.defaultValue = ""
                                }
                            }
                        }
                    }
                    
                    // 处理删除表
                    val dropTableNodes = changeSetNode.getElementsByTagName("dropTable")
                    for (j in 0 until dropTableNodes.length) {
                        val dropTableNode = dropTableNodes.item(j) as Element
                        val tableName = dropTableNode.getAttribute("name")
                        allTables.remove(tableName)
                    }
                }
            }
        }
        
        /**
         * 从XML解析列信息
         */
        private fun parseColumnFromXml(columnNode: Element): ColumnInfo {
            val name = columnNode.getAttribute("name")
            val type = columnNode.getAttribute("type")
            val nullable = !columnNode.hasAttribute("notnull") || columnNode.getAttribute("notnull") != "true"
            val defaultValue = if (columnNode.hasAttribute("defaultValue")) {
                columnNode.getAttribute("defaultValue")
            } else {
                ""
            }
            val isPrimaryKey = columnNode.hasAttribute("primaryKey") && 
                               columnNode.getAttribute("primaryKey") == "true"
            
            return ColumnInfo(
                name = name,
                type = type,
                nullable = nullable,
                defaultValue = defaultValue,
                isPrimaryKey = isPrimaryKey
            )
        }
        
        /**
         * 添加创建表到模型
         */
        private fun addCreateTableToModel(doc: Document, parentElement: Element, sqlInfo: SqlInfo) {
            val createTableElement = doc.createElement("createTable")
            createTableElement.setAttribute("name", sqlInfo.tableName)
            
            if (sqlInfo.primaryKeys.isNotEmpty()) {
                createTableElement.setAttribute("pkName", "pk_${sqlInfo.tableName}")
            }
            
            parentElement.appendChild(createTableElement)
            
            // 添加列
            sqlInfo.columns.forEach { column ->
                val columnElement = doc.createElement("column")
                columnElement.setAttribute("name", column.name)
                columnElement.setAttribute("type", column.type)
                
                if (!column.nullable) {
                    columnElement.setAttribute("notnull", "true")
                }
                
                if (column.defaultValue.isNotEmpty()) {
                    columnElement.setAttribute("defaultValue", column.defaultValue)
                }
                
                if (column.isPrimaryKey) {
                    columnElement.setAttribute("primaryKey", "true")
                }
                
                createTableElement.appendChild(columnElement)
            }
        }
        
        /**
         * 比较表差异
         */
        private fun compareTables(
            oldTables: Map<String, SqlInfo>,
            currentTables: Map<String, SqlInfo>
        ): DiffResult {
            val result = DiffResult()
            
            // 找出需要创建的表
            currentTables.forEach { (tableName, sqlInfo) ->
                if (!oldTables.containsKey(tableName)) {
                    result.tablesToCreate.add(sqlInfo)
                }
            }
            
            // 找出需要删除的表
            oldTables.forEach { (tableName, _) ->
                if (!currentTables.containsKey(tableName)) {
                    result.tablesToDrop.add(tableName)
                }
            }
            
            // 对于都存在的表，比较列差异
            currentTables.forEach { (tableName, currentSqlInfo) ->
                oldTables[tableName]?.let { oldSqlInfo ->
                    // 当前表中的列名集合
                    val currentColumnNames = currentSqlInfo.columns.map { it.name }.toSet()
                    // 旧表中的列名集合
                    val oldColumnNames = oldSqlInfo.columns.map { it.name }.toSet()
                    
                    // 找出需要添加的列
                    val columnsToAdd = currentSqlInfo.columns.filter { it.name !in oldColumnNames }
                    if (columnsToAdd.isNotEmpty()) {
                        result.columnsToAdd[tableName] = columnsToAdd
                    }
                    
                    // 找出需要删除的列
                    val columnsToDrop = oldSqlInfo.columns
                        .filter { it.name !in currentColumnNames }
                        .map { it.name }
                    if (columnsToDrop.isNotEmpty()) {
                        result.columnsToDrop[tableName] = columnsToDrop
                    }
                    
                    // 找出需要修改的列
                    val columnsToAlter = mutableListOf<Pair<ColumnInfo, ColumnInfo>>()
                    
                    // 遍历两个表中都存在的列
                    oldSqlInfo.columns.forEach { oldColumn ->
                        currentSqlInfo.columns.find { it.name == oldColumn.name }?.let { currentColumn ->
                            // 检查列定义是否发生变化
                            if (oldColumn.type != currentColumn.type || 
                                oldColumn.nullable != currentColumn.nullable || 
                                oldColumn.defaultValue != currentColumn.defaultValue) {
                                columnsToAlter.add(oldColumn to currentColumn)
                            }
                        }
                    }
                    
                    if (columnsToAlter.isNotEmpty()) {
                        result.columnsToAlter[tableName] = columnsToAlter
                    }
                }
            }
            
            return result
        }
        
        /**
         * 查找最新的模型文件
         */
        private fun findLatestModelFile(): File? {
            val modelDir = File(MODEL_PATH)
            val modelFiles = modelDir.listFiles { file -> 
                file.isFile && file.name.endsWith(".model.xml") 
            } ?: return null
            
            // 先打印所有可能的文件，便于调试
            println("找到所有模型文件:")
            modelFiles.forEach { println(" - ${it.name}") }
            
            // 构建版本号到文件的映射
            val versionToFileMap = mutableMapOf<String, File>()
            val versionToValueMap = mutableMapOf<String, Int>()
            
            modelFiles.forEach { file ->
                val version = extractVersionFromFileName(file.name)
                val versionValue = calculateVersionValue(version)
                versionToFileMap[version] = file
                versionToValueMap[version] = versionValue
            }
            
            // 找出最大版本号
            val maxVersionEntry = versionToValueMap.entries.maxByOrNull { it.value }
            if (maxVersionEntry != null) {
                val latestFile = versionToFileMap[maxVersionEntry.key]
                println("选择最新版本文件: ${latestFile?.name}, 版本值: ${maxVersionEntry.value}")
                return latestFile
            }
            
            return null
        }
        
        /**
         * 计算版本号的数值表示，用于比较
         */
        private fun calculateVersionValue(version: String): Int {
            println("计算版本值: $version")
            
            if (!version.contains(".")) {
                println("警告: 版本号 '$version' 不包含'.'，返回默认值0")
                return 0
            }
            
            val parts = version.split(".")
            if (parts.size != 2) {
                println("警告: 版本号 '$version' 格式不正确，返回默认值0")
                return 0
            }
            
            try {
                val major = parts[0].toInt()
                val minor = parts[1].toInt()
                val value = major * 1000 + minor
                println("版本号 '$version' 的数值为: $value")
                return value
            } catch (e: NumberFormatException) {
                println("警告: 版本号 '$version' 中包含非数字部分，返回默认值0")
                return 0
            }
        }
        
        /**
         * 从文件名提取版本号
         */
        private fun extractVersionFromFileName(fileName: String): String {
            // 打印原始文件名，便于调试
            println("提取版本号，原始文件名: $fileName")
            
            // 处理带有初始化标记的情况
            if (fileName.contains("__")) {
                val parts = fileName.split("__")
                return parts[0]
            }
            
            // 处理普通版本号
            val fileNameWithoutExt = fileName.substringBeforeLast(".model.xml").substringBeforeLast(".sql")
            
            // 确保包含点号的完整版本
            if (fileNameWithoutExt.contains(".")) {
                println("提取的版本号: $fileNameWithoutExt")
                return fileNameWithoutExt
            }
            
            // 如果没有找到有效格式，返回原始部分
            println("未找到有效版本格式，使用: $fileNameWithoutExt")
            return fileNameWithoutExt
        }
        
        /**
         * 创建模型文档
         */
        private fun createModelDocument(): Document {
            val docFactory = DocumentBuilderFactory.newInstance()
            val docBuilder = docFactory.newDocumentBuilder()
            val doc = docBuilder.newDocument()
            
            val rootElement = doc.createElement("migration")
            rootElement.setAttribute("generated", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
//            rootElement.setAttribute("xmlns", "http://ebean-orm.github.io/xml/ns/dbmigration")
            doc.appendChild(rootElement)
            
            return doc
        }
        
        /**
         * 解析模型文件
         */
        private fun parseModelFile(file: File): Document {
            val docFactory = DocumentBuilderFactory.newInstance()
            val docBuilder = docFactory.newDocumentBuilder()
            return docBuilder.parse(file)
        }
        
        /**
         * 写入模型到文件
         */
        private fun writeModelToFile(doc: Document, fileName: String) {
            val transformerFactory = TransformerFactory.newInstance()
            val transformer = transformerFactory.newTransformer()
            transformer.setOutputProperty(OutputKeys.INDENT, "yes")
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            transformer.setOutputProperty(OutputKeys.STANDALONE, "yes")
            
            val source = DOMSource(doc)
            val result = StreamResult(FileOutputStream(fileName))
            transformer.transform(source, result)
        }
        
        /**
         * 根据SqlInfo生成创建表SQL
         */
        private fun generateCreateTableSql(sqlInfo: SqlInfo): String {
            val tableName = sqlInfo.tableName
            val columns = sqlInfo.columns
            
            if (tableName.isEmpty() || columns.isEmpty()) {
                throw IllegalArgumentException("无法生成SQL，表名或列信息为空")
            }
            
            val sb = StringBuilder()
            sb.append("create table $tableName (\n")
            
            // 添加列定义
            val columnDefinitions = columns.map { column ->
                val nullable = if (column.nullable) "" else " not null"
                val defaultValue = if (column.defaultValue.isNotEmpty()) " default ${column.defaultValue}" else ""
                "  ${column.name} ${column.type}$defaultValue$nullable"
            }
            
            sb.append(columnDefinitions.joinToString(",\n"))
            
            // 添加主键
            if (sqlInfo.primaryKeys.isNotEmpty()) {
                sb.append(",\n  constraint pk_${sqlInfo.tableName} primary key (${sqlInfo.primaryKeys.joinToString(", ")})")
            }
            
            sb.append("\n);")
            return sb.toString()
        }
        
        /**
         * 检查差异结果是否为空
         */
        private fun DiffResult.isEmpty(): Boolean {
            return tablesToCreate.isEmpty() &&
                   tablesToDrop.isEmpty() &&
                   columnsToAdd.isEmpty() &&
                   columnsToDrop.isEmpty() &&
                   columnsToAlter.isEmpty()
        }
    }
    
    /**
     * 差异结果类
     */
    data class DiffResult(
        val tablesToCreate: MutableList<SqlInfo> = mutableListOf(),
        val tablesToDrop: MutableList<String> = mutableListOf(),
        val columnsToAdd: MutableMap<String, List<ColumnInfo>> = mutableMapOf(),
        val columnsToDrop: MutableMap<String, List<String>> = mutableMapOf(),
        val columnsToAlter: MutableMap<String, List<Pair<ColumnInfo, ColumnInfo>>> = mutableMapOf()
    )
} 