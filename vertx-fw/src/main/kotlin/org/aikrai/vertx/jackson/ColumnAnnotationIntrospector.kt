package org.aikrai.vertx.jackson

import com.fasterxml.jackson.databind.PropertyName
import com.fasterxml.jackson.databind.introspect.Annotated
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector
import org.aikrai.vertx.db.annotation.TableField

class ColumnAnnotationIntrospector : JacksonAnnotationIntrospector() {
  override fun findNameForDeserialization(annotated: Annotated?): PropertyName? {
    return getColumnName(annotated)
  }

  override fun findNameForSerialization(annotated: Annotated?): PropertyName? {
    return getColumnName(annotated)
  }

  private fun getColumnName(annotated: Annotated?): PropertyName? {
    if (annotated == null) return null
    val column = annotated.getAnnotation(TableField::class.java)
    return column?.let { PropertyName(it.value) }
  }
}
