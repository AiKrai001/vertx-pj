package app.data.emun

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import org.aikrai.vertx.db.annotation.EnumValue

enum class DeleteEnum(private val code: Int, private val description: String) {
  UNDELETED(0, "未删除"),
  DELETED(2, "已删除");

  @JsonValue
  @EnumValue
  fun getCode(): Int {
    return this.code
  }

  override fun toString(): String {
    return this.description
  }

  companion object {
    @JsonCreator
    fun parse(code: Int): DeleteEnum? {
      return entries.find { it.code == code }
    }
  }
}
