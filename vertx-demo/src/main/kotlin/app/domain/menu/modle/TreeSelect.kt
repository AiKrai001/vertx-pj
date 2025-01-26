package app.base.domain.auth.menu.modle

import app.domain.menu.Menu

class TreeSelect {

  /** 节点ID */
  var id: Long? = null

  /** 节点名称 */
  var label: String? = null

  /** 节点禁用 */
  var disabled: Boolean = false

  /** 子节点 */
//  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  var children: List<TreeSelect>? = null

  constructor() // 无参构造函数

//  constructor(dept: SysDept) { // 带 SysDept 参数的构造函数
//    this.id = dept.deptId
//    this.label = dept.deptName
//    this.disabled = UserConstants.DEPT_DISABLE == dept.status
//    this.children = dept.children.map { TreeSelect(it) }
//  }

  constructor(menu: Menu) { // 带 SysMenu 参数的构造函数
    this.id = menu.menuId
    this.label = menu.menuName
    this.children = menu.children.map { TreeSelect(it) }
  }
}
