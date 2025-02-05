# Vertx-pj 说明

基于vert.x的web开发框架，提供了一些简单的封装，使得开发更加便捷。<br/>
**写的简单，问题不少，仅供参考**。
- **vertx-fw**: 简单封装的web开发框架
- **vertx-demo**: 使用vertx-fw开发的demo

### 项目介绍

#### 技术栈
- **框架**: Vert.x 4.x (响应式异步框架)
- **语言**: Kotlin + 协程
- **数据库**: PostgreSQL + Vert.x PgClient
- **DI**: Google Guice
- **认证**: JWT + 角色权限体系
- **工具库**: Hutool(工具包)、mu(日志)、jackson(序列化)
- **文档**: OpenAPI 3.0 + Apifox集成

#### 设计
```
├── Application (入口)
├── Verticles
│   ├── MainVerticle (主部署器)
│   └── WebVerticle (WEB服务)
├── Config
│   ├── JWT认证
│   └── 依赖注入配置
├── Controller (接口层)
├── Service (业务层)
├── Repository (数据层)
└── Domain (领域模型)
```

#### 核心特性
1. **全异步架构**：基于Vert.x事件循环和Kotlin协程
2. **自动路由注册**：通过注解自动映射REST接口
3. **声明式事务**：通过withTransaction函数管理事务
4. **RBAC权限控制**：支持角色+权限双重验证
5. **智能参数解析**：自动处理路径/查询/表单/JSON参数
6. **全链路日志**：包含请求ID、用户上下文、耗时预警
7. **代码生成**：Repository自动生成基础SQL

---

### 使用说明

#### 1. 快速启动
```kotlin
// 配置application.yaml
server:
  name: vtx_demo
  port: 8080
  context: api
  package: app

jwt:
  key: 123456sdfjasdfjl 
        
databases:
  name: vertx-demo
  host: 127.0.0.1
  port: 5432
  username: root
  password: 123456

redis:
  host: 127.0.0.1
  port: 6379
  database: 0
  password: xxx
  maxPoolSize: 8
  maxPoolWaiting: 2000

apifox:
  token: APS-xxxxxxxxxxxxxxx
  projectId: xxxxxx
  folderId: xxxxxx
```

#### 2. 创建Controller
```kotlin
@Controller("/user")
class UserController @Inject constructor(
    private val userService: UserService
) {
    @D("获取用户详情")
    @CheckRole("admin", mode = Mode.OR) // 权限控制
    suspend fun getDetail(
        ctx: RoutingContext,
        @D("userId") userId: Long
    ): User {
        return userService.getDetail(userId)
    }
}
```

#### 3. 定义Service
```kotlin
class UserService @Inject constructor(
   private val snowflake: Snowflake,
   private val userRepository: UserRepository
) {
    suspend fun createUser(dto: UserDTO): Long {
        return userRepository.create(dto.toEntity(snowflake))
    }
    
    suspend fun batchUpdate(ids: List<Long>) {
        withTransaction() {
            ids.forEach { id ->
                userRepository.update(id, mapOf("status" to 1))
            }
        }
    }
}
```

#### 4. 实现Repository
```kotlin
@ImplementedBy(UserRepositoryImpl::class)
interface UserRepository : Repository<Long, User> {
    suspend fun findByEmail(email: String): User?
}

@Singleton
class UserRepositoryImpl @Inject constructor(sqlClient: SqlClient) 
    : RepositoryImpl<Long, User>(sqlClient), UserRepository {
    
    override suspend fun findByEmail(email: String): User? {
        return queryBuilder()
            .eq(User::email, email)
            .getOne()
    }
}
```

#### 5. 数据库实体
```kotlin
@TableName("sys_user")
data class User(
    @TableId(type = IdType.ASSIGN_ID)
    @TableField("user_id")
    val userId: Long = 0,
    
    @TableField("user_name")
    val userName: String,
    
    @TableField(fill = FieldFill.INSERT)
    val createTime: LocalDateTime = LocalDateTime.now()
)
```

#### 6. 运行与测试
```bash
# 启动应用
gradle run

# 生成的API文档
http://localhost:8080/api/docs

# 示例请求
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "123456"
}
```

---

### 高级功能

#### 事务管理
```kotlin
suspend fun transfer(from: Long, to: Long, amount: Double) {
    withTransaction {
        accountRepository.deductBalance(tx, from, amount)
        accountRepository.addBalance(tx, to, amount)
    }
}
```

#### 复杂查询
```kotlin
val users = queryBuilder()
    .like(User::name, "%张%")
    .between("create_time", start, end)
    .orderByDesc("user_id")
    .getList()
```

#### 自定义响应
```kotlin
@CustomizeResponse
suspend fun customResponse(): RespBean {
    return RespBean.success("自定义格式")
}
```

#### 权限配置
```kotlin
@CheckRole(value = ["admin", "supervisor"], mode = Mode.OR)
@CheckPermission(value = ["user:write", "user:update"])
suspend fun sensitiveOperation() {
    // 需要admin或supervisor角色
    // 且拥有user:write或user:update权限
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
