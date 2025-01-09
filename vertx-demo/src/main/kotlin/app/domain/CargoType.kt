package app.domain

enum class CargoType(val message: String) {

  RECEIVING("收货"),

  SHIPPING("发货"),

  INTERNAL_TRANSFER("内部调拨")
  ;

  companion object {
    fun parse(value: String?): CargoType? {
      if (value.isNullOrBlank()) return null
      return CargoType.values().find { it.name == value || it.message == value }
    }
  }
}
