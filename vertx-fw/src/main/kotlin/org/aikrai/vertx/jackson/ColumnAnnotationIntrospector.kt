package org.aikrai.vertx.jackson

import com.fasterxml.jackson.databind.PropertyName
import com.fasterxml.jackson.databind.introspect.Annotated
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector
import jakarta.persistence.Column

class ColumnAnnotationIntrospector : JacksonAnnotationIntrospector() {
  override fun findNameForDeserialization(annotated: Annotated?): PropertyName? {
    return getColumnName(annotated)
  }

  override fun findNameForSerialization(annotated: Annotated?): PropertyName? {
    return getColumnName(annotated)
  }

  private fun getColumnName(annotated: Annotated?): PropertyName? {
    if (annotated == null) return null
    val column = annotated.getAnnotation(Column::class.java)
    return column?.let { PropertyName(it.name) }
  }
}
