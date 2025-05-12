package app.data.emun

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import org.aikrai.vertx.db.annotation.EnumValue

enum class SexType(private val code: Int, private val description: String) {
  MALE(0, "男"),
  FEMALE(1, "女"),
  UNKNOWN(2, "未知");

  @JsonValue
  @EnumValue
  fun getCode(): Int {
    return code
  }

  override fun toString(): String {
    return description
  }

  companion object {
    @JsonCreator
    fun parse(code: Int): SexType? {
      return SexType.entries.find { it.code == code }
    }
  }
}