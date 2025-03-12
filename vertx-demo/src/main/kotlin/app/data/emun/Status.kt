package app.data.emun

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import org.aikrai.vertx.db.annotation.EnumValue

enum class Status(private val code: Int, private val description: String) {
  ACTIVE(0, "正常"),
  INACTIVE(1, "禁用"),
  DELETED(2, "删除");

  @JsonValue
  @EnumValue
  public fun getCode(): Int {
    return code
  }
  override fun toString(): String {
    return description
  }

  companion object {
    @JsonCreator
    fun parse(code: Int): Status? {
      return entries.find { it.code == code }
    }
  }
}
