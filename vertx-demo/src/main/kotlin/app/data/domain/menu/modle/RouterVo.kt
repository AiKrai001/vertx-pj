package app.base.domain.auth.menu.modle

data class RouterVo(
  /**
   * 路由名字
   */
  var name: String? = null,
  /**
   * 路由地址
   */
  var path: String? = null,
  /**
   * 是否隐藏路由，当设置 true 的时候该路由不会再侧边栏出现
   */
  var hidden: Boolean = false,
  /**
   * 组件地址
   */
  var component: String? = null,
  /**
   * 当你一个路由下面的 children 声明的路由大于1个时，自动会变成嵌套的模式--如组件页面
   */
  var alwaysShow: Boolean? = null,
  /**
   * 子路由
   */
  var children: List<RouterVo>? = null
)
