package app.data.emun

enum class MenuType(val desc: String) {
  M("目录"),
  C("菜单"),
  F("按钮");

  companion object {
    fun parse(value: String?): MenuType? {
      if (value.isNullOrBlank()) return null
      return MenuType.entries.find { it.name == value || it.desc == value }
    }
  }
}