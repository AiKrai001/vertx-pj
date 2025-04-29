# Vert.x 快速开发模板

这是一个基于 Vert.x 库的后端快速开发模板，采用 Kotlin 语言和协程实现高性能异步编程。本项目提供了完整的开发框架，能够帮助开发者快速搭建响应式、模块化、高性能的后端服务。

## 技术栈

- **核心框架**: Vert.x 4.5.x (响应式、事件驱动框架)
- **编程语言**: Kotlin 1.9.x (协程支持)
- **依赖注入**: Google Guice 7.0.0
- **数据库**: PostgreSQL (主), MySQL (可选)
- **缓存**: Redis
- **认证**: JWT
- **构建工具**: Gradle with Kotlin DSL
- **代码规范**: Spotless, ktlint

## 项目结构

项目采用模块化设计，主要分为两个模块：

### vertx-fw (框架核心)

提供基础架构和通用功能:

- 依赖注入配置
- 路由管理
- 认证与授权
- 数据库操作封装
- 事务管理
- 全局异常处理
- 通用工具类

### vertx-demo (应用示例)

包含示例应用代码:

- 控制器实现示例
- 服务层实现
- 数据访问层
- 实体类定义
- 配置文件

## 主要特性

### 1. 注解驱动的路由

通过简单的注解即可定义 API 路由和参数绑定:

```kotlin
@D("测试1:测试")
@Controller
class Demo1Controller @Inject constructor(
  private val accountService: AccountService,
  private val accountRepository: AccountRepository
) {

  @AllowAnonymous
  @D("参数测试", "详细说明......")
  suspend fun test1(
    @D("name", "姓名") name: String?,
    @D("age", "年龄") age: Int?,
    @D("list", "列表") list: List<String>?,
    @D("status", "状态-0正常,1禁用,2删除") status: Status?,
    @D("account", "账号") account: Account?
  ) {
    // 业务逻辑
  }
}
```

### 2. 据库支持

#### Repository 模式

```kotlin
interface AccountRepository : Repository<Long, Account> {
  suspend fun getByName(name: String): Account?
  suspend fun getUserList(userName: String?, phone: String?): List<Account>
}
```

#### 事务管理

```kotlin
suspend fun testTransaction() {
  withTransaction {
    accountRepository.update(1L, mapOf("avatar" to "test0001"))
    
    // 嵌套事务
    try {
      withTransaction {
        accountRepository.update(1L, mapOf("avatar" to "test002"))
        throw Meta.error("test transaction", "test transaction")
      }
    } catch (e: Exception) {
      logger.info { "内层事务失败已处理: ${e.message}" }
    }
  }
}
```

#### 数据库迁移

支持通过实体类注解自动生成数据库变更脚本，遵循版本化管理。

### 3. 安全与认证

- JWT 认证
- 基于角色的权限控制
- 自定义安全注解: `@AllowAnonymous`, `@CheckRole`, `@CheckPermission`

### 4. API 文档生成

自动生成 OpenAPI 规范文档，支持与 ApiFox 等 API 工具集成。

### 5. 统一响应处理

标准化的 JSON 响应格式:

```json
{
  "code": 200,
  "message": "Success",
  "data": { ... },
  "requestId": "1899712678486753280"
}
```

### 6. 全局异常处理

统一捕获和处理异常，转换为友好的 API 响应。

### 7. 配置管理

支持 YAML 配置，环境变量和系统属性覆盖，多环境配置。

## 快速开始

### 环境要求

- JDK 17+
- PostgreSQL 数据库
- Redis (可选)

### 构建与运行

1. **克隆项目**

```bash
git clone https://github.com/yourusername/vertx-pj.git
cd vertx-pj
```

2. **配置数据库**

编辑 `vertx-demo/src/main/resources/config/application-database.yml`

```yaml
databases:
  name: vertx-demo
  host: 127.0.0.1
  port: 5432
  username: your_username
  password: your_password
```

3. **构建项目**

```bash
./gradlew clean build
```

4. **运行项目**

```bash
./gradlew :vertx-demo:run
```

## 创建新API

1. 创建实体类

```kotlin
@TableName("users")
class User : BaseEntity() {
  @TableId(type = IdType.ASSIGN_ID)
  var userId: Long = 0L
  
  @TableField("user_name")
  var userName: String? = ""
  
  var status: Status? = Status.ACTIVE
}
```

2. 创建Repository

```kotlin
interface UserRepository : Repository<Long, User> {
  suspend fun findByName(name: String): User?
}
```

3. 创建Service

```kotlin
class UserService @Inject constructor(
  private val userRepository: UserRepository
) {
  suspend fun getUserByName(name: String): User? {
    return userRepository.findByName(name)
  }
}
```

4. 创建Controller

```kotlin
@D("用户管理")
@Controller("/users")
class UserController @Inject constructor(
  private val userService: UserService
) {
  @D("通过用户名获取用户")
  suspend fun getByName(
    @D("name", "用户名") name: String
  ): User? {
    return userService.getUserByName(name)
  }
}
```

---

## 快速开发注解说明

### @AllowAnonymous
允许匿名访问注解
标记在Controller类上时，表示该类下所有接口都不需要鉴权。
标记在Controller类中的方法上时，表示该接口不需要鉴权。


### @Controller
自定义Controller注解，用于路由注册
- 启动时使用反射获取所有标记了@Controller注解的类
- 获取类中所有方法，将其统一注册到router中
- 可选参数`prefix`：定义请求路径前缀
    - 不填时默认使用类名(去除"Controller"后首字母小写)作为前缀

### @D
文档生成注解，可用于以下位置：
1. 类上：为Controller类添加说明
2. 方法上：为方法添加说明
3. 方法参数上：格式如 `@D("age", "年龄") age: Int?`
    - name: 参数名，用于从query或body中自动获取参数
    - value: 参数说明，用于文档生成

> 注：参数类型后的`?`表示可为空，不带`?`表示必填。框架会根据此进行参数校验。


### 权限相关注解
仿sa-token实现的权限控制：
- `@CheckRole()`：角色检查
- `@CheckPermission()`：权限检查

### 请求响应相关注解

#### @CustomizeRequest
请求方式定制
- 全局默认使用POST请求
- 使用`@CustomizeRequest("get")`可将方法改为GET请求

#### @CustomizeResponse
响应方式定制
- 全局默认返回JSON格式
- 使用`@CustomizeResponse`可获取response对象自定义返回内容
