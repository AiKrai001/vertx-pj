package org.aikrai.vertx.db

import kotlin.reflect.KProperty1

interface QueryWrapper<T> {
  fun select(vararg columns: String): QueryWrapper<T>
  fun select(vararg columns: KProperty1<T, *>): QueryWrapper<T>

  fun eq(column: String, value: Any): QueryWrapper<T>
  fun eq(column: KProperty1<T, *>, value: Any): QueryWrapper<T>
  fun eq(condition: Boolean, column: String, value: Any): QueryWrapper<T>
  fun eq(condition: Boolean, column: KProperty1<T, *>, value: Any): QueryWrapper<T>

  fun from(table: String): QueryWrapper<T>

  fun like(column: String, value: String): QueryWrapper<T>
  fun like(column: KProperty1<T, *>, value: String): QueryWrapper<T>
//  fun like(condition: Boolean, column: String, value: String): QueryWrapper<T>
//  fun like(condition: Boolean, column: KProperty1<T, *>, value: String): QueryWrapper<T>

//  fun likeLeft(column: String, value: String): QueryWrapper<T>
  fun likeLeft(column: KProperty1<T, *>, value: String): QueryWrapper<T>

//  fun likeRight(column: String, value: String): QueryWrapper<T>
  fun likeRight(column: KProperty1<T, *>, value: String): QueryWrapper<T>

  fun `in`(column: KProperty1<T, *>, values: Collection<*>): QueryWrapper<T>
  fun notIn(column: KProperty1<T, *>, values: Collection<*>): QueryWrapper<T>

  fun groupBy(vararg columns: KProperty1<T, *>): QueryWrapper<T>
  fun having(condition: String): QueryWrapper<T>

  fun orderByAsc(vararg columns: KProperty1<T, *>): QueryWrapper<T>
  fun orderByDesc(vararg columns: KProperty1<T, *>): QueryWrapper<T>

  fun genSql(): String
  suspend fun getList(): List<T>
  suspend fun getOne(): T?
}
