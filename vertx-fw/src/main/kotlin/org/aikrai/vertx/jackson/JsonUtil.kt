package org.aikrai.vertx.jackson

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject

object JsonUtil {

  fun configure(writeClassName: Boolean) {
    objectMapper = createObjectMapper(writeClassName)
    objectMapperSnakeCase = createObjectMapperSnakeCase(writeClassName)
  }

  private var objectMapper = createObjectMapper(false)
  private var objectMapperSnakeCase = createObjectMapperSnakeCase(false)

  private val objectMapperDeserialization = run {
    val mapper: ObjectMapper = jacksonObjectMapper()
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    mapper.registerModule(JavaTimeModule())
    mapper.setAnnotationIntrospector(
      AnnotationIntrospectorPair(ColumnAnnotationIntrospector(), mapper.deserializationConfig.annotationIntrospector)
    )
    mapper
  }

  private val objectMapperSnakeCaseDeserialization = run {
    val mapper: ObjectMapper = jacksonObjectMapper()
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    mapper.propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
    mapper.registerModule(JavaTimeModule())
    mapper.setAnnotationIntrospector(
      AnnotationIntrospectorPair(ColumnAnnotationIntrospector(), mapper.deserializationConfig.annotationIntrospector)
    )
    mapper
  }

  fun <T> toJsonStr(value: T, snakeCase: Boolean = false): String {
    if (snakeCase) {
      return objectMapperSnakeCase.writeValueAsString(value)
    }
    return objectMapper.writeValueAsString(value)
  }

  fun <T> toJsonObject(value: T, snakeCase: Boolean = false): JsonObject {
    return JsonObject(toJsonStr(value, snakeCase))
  }

  fun <T> toJsonArray(value: T, snakeCase: Boolean = false): JsonArray {
    return JsonArray(toJsonStr(value, snakeCase))
  }

  fun <T> parseObject(value: JsonObject, clazz: Class<T>, snakeCase: Boolean = false): T {
    return parseObject(value.encode(), clazz, snakeCase)
  }

  fun <T> parseObject(value: JsonObject, typeReference: TypeReference<T>, snakeCase: Boolean = false): T {
    return parseObject<T>(value.encode(), typeReference, snakeCase)
  }

  fun <T> parseObject(value: String, clazz: Class<T>, snakeCase: Boolean = false): T {
    if (snakeCase) {
      return objectMapperSnakeCaseDeserialization.readValue(value, clazz)
    }
    return objectMapperDeserialization.readValue(value, clazz)
  }

  private fun <T> parseObject(value: String, typeReference: TypeReference<T>, snakeCase: Boolean = false): T {
    if (snakeCase) {
      return objectMapperSnakeCaseDeserialization.readValue(value, typeReference)
    }
    return objectMapperDeserialization.readValue(value, typeReference)
  }

  fun <T> parseObject(value: Map<*, *>, clazz: Class<T>, snakeCase: Boolean = false): T {
    return parseObject(toJsonStr(value), clazz, snakeCase)
  }

  fun <T> parseObject(value: Map<*, *>, typeReference: TypeReference<T>, snakeCase: Boolean = false): T {
    return parseObject<T>(
      toJsonStr(value),
      typeReference,
      snakeCase
    )
  }

  fun <T> parseArray(value: JsonArray, typeReference: TypeReference<List<T>>, snakeCase: Boolean = false): List<T> {
    return parseArray(value.encode(), typeReference, snakeCase)
  }

  fun <T> parseArray(value: String, typeReference: TypeReference<List<T>>, snakeCase: Boolean = false): List<T> {
    if (snakeCase) {
      return objectMapperSnakeCaseDeserialization.readValue(value, typeReference)
    }
    return objectMapperDeserialization.readValue(value, typeReference)
  }
}

private class CustomTypeResolverBuilder : ObjectMapper.DefaultTypeResolverBuilder(
  ObjectMapper.DefaultTyping.NON_FINAL,
  BasicPolymorphicTypeValidator.builder().build()
) {
  override fun useForType(t: JavaType): Boolean {
    if (t.rawClass.isEnum) return false
    return t.rawClass.name.startsWith("app.")
  }
}

private fun createObjectMapper(writeClassName: Boolean): ObjectMapper {
  val mapper: ObjectMapper = jacksonObjectMapper()
  mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  mapper.registerModule(JavaTimeModule())
  if (writeClassName) {
    val typeResolver = CustomTypeResolverBuilder()
    typeResolver.init(JsonTypeInfo.Id.CLASS, null)
    typeResolver.inclusion(JsonTypeInfo.As.PROPERTY)
    typeResolver.typeProperty("@t")
    mapper.setDefaultTyping(typeResolver)
  }
  return mapper
}

private fun createObjectMapperSnakeCase(writeClassName: Boolean): ObjectMapper {
  val mapper: ObjectMapper = jacksonObjectMapper()
  mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  mapper.propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
  mapper.registerModule(JavaTimeModule())
  if (writeClassName) {
    val typeResolver = CustomTypeResolverBuilder()
    typeResolver.init(JsonTypeInfo.Id.CLASS, null)
    typeResolver.inclusion(JsonTypeInfo.As.PROPERTY)
    typeResolver.typeProperty("@t")
    mapper.setDefaultTyping(typeResolver)
  }
  return mapper
}
