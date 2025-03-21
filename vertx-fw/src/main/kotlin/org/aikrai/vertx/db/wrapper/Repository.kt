package org.aikrai.vertx.db.wrapper

import kotlin.reflect.KProperty1

interface Repository<TId, TEntity> {
  suspend fun create(t: TEntity): Int
  suspend fun delete(id: TId): Int
  suspend fun update(t: TEntity): Int
  suspend fun update(id: TId, parameters: Map<String, Any?>): Int
  suspend fun get(id: TId): TEntity?

  suspend fun getByField(field: String, value: Any): TEntity?
  suspend fun getByField(field: KProperty1<TEntity, *>, value: Any): TEntity?

  suspend fun createBatch(list: List<TEntity>): Int
}
