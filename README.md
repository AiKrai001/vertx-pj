# Vertx-pj 说明

## 项目简介
基于vert.x的web开发框架，提供了一些简单的封装，使得开发更加便捷。<br/>
**写的简单，问题不少，仅供参考**。


## 项目结构
- **vertx-fw**: 简单封装的web开发框架
- **vertx-demo**: 使用vertx-fw开发的demo

## 注解说明

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

### @AllowAnonymous
权限控制注解
- 可标记在Controller类或方法上
- 表示该接口不需要鉴权即可访问

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