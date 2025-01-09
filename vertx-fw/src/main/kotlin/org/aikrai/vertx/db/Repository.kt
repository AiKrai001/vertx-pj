package org.aikrai.vertx.db

interface Repository<TId, TEntity> {
  suspend fun create(t: TEntity): Int
  suspend fun delete(id: TId): Int
  suspend fun update(t: TEntity): Int
  suspend fun update(id: TId, parameters: Map<String, Any?>): Int
  suspend fun get(id: TId): TEntity?

  suspend fun createBatch(list: List<TEntity>): Int

  suspend fun <R> execute(sql: String): R

  suspend fun queryBuilder(): QueryWrapper<TEntity>
  suspend fun queryBuilder(clazz: Class<*>): QueryWrapper<*>
}
