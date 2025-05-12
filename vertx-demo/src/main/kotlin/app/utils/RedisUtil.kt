package app.utils

import io.vertx.kotlin.coroutines.coAwait
import io.vertx.redis.client.Command
import io.vertx.redis.client.Redis
import io.vertx.redis.client.Request
import java.util.concurrent.TimeUnit

class RedisUtil constructor(private val redis: Redis) {

  /**
   * 缓存基本的对象，Integer、String、实体类等
   *
   * @param key 缓存的键值
   * @param value 缓存的值
   */
  suspend fun <T> setObject(key: String, value: T): Boolean {
    val request = Request.cmd(Command.SET)
      .arg(key)
      .arg(value.toString())
      .arg("KEEPTTL")
    val response = redis.send(request).coAwait()
    return response?.toString() == "OK"
  }

  /**
   * 缓存基本的对象，Integer、String、实体类等
   *
   * @param key 缓存的键值
   * @param value 缓存的值
   * @param timeout 时间
   * @param timeUnit 时间颗粒度
   */
  suspend fun <T> setObject(key: String, value: T, timeout: Long, timeUnit: TimeUnit): Boolean {
    val expireSeconds = timeUnit.toSeconds(timeout)
    val request = Request.cmd(Command.SET, key, value.toString(), "EX", expireSeconds.toString())
    val response = redis.send(request).coAwait()
    return response?.toString() == "OK"
  }

  /**
   * 设置有效时间
   *
   * @param key Redis键
   * @param timeout 超时时间
   * @return true=设置成功；false=设置失败
   */
  suspend fun expire(key: String, timeout: Long): Boolean {
    return expire(key, timeout, TimeUnit.SECONDS)
  }

  /**
   * 设置有效时间
   *
   * @param key Redis键
   * @param timeout 超时时间
   * @param unit 时间单位
   * @return true=设置成功；false=设置失败
   */
  suspend fun expire(key: String, timeout: Long, unit: TimeUnit): Boolean {
    val expireSeconds = unit.toSeconds(timeout)
    val response = redis.send(Request.cmd(Command.EXPIRE, key, expireSeconds.toString())).coAwait()
    return response?.toLong() == 1L
  }

  /**
   * 获取有效时间
   *
   * @param key Redis键
   * @return 有效时间
   */
  suspend fun getExpire(key: String): Long? {
    val response = redis.send(Request.cmd(Command.TTL, key)).coAwait()
    val ttl = response?.toLong()
    return if (ttl == -1L || ttl == -2L) null else ttl
  }

  /**
   * 判断 key是否存在
   *
   * @param key 键
   * @return true 存在 false不存在
   */
  suspend fun hasKey(key: String): Boolean {
    val response = redis.send(Request.cmd(Command.EXISTS, key)).coAwait()
    return (response?.toLong() ?: 0) > 0
  }

  /**
   * 获得缓存的基本对象。
   *
   * @param key 缓存键值
   * @return 缓存键值对应的数据
   */
  suspend fun <T> getObject(key: String): T? {
    val response = redis.send(Request.cmd(Command.GET, key)).coAwait()
    return response?.toString() as? T
  }

  /**
   * 删除单个对象
   *
   * @param key
   */
  suspend fun deleteObject(key: String): Boolean {
    val response = redis.send(Request.cmd(Command.DEL, key)).coAwait()
    return response?.toLong() == 1L
  }

  /**
   * 删除集合对象
   *
   * @param collection 多个对象
   * @return
   */
  suspend fun deleteObject(collection: Collection<String>): Boolean {
    if (collection.isEmpty()) return false
    val response = redis.send(Request.cmd(Command.DEL, *collection.toTypedArray())).coAwait()
    return (response?.toLong() ?: 0) > 0
  }

  /**
   * 缓存List数据
   *
   * @param key 缓存的键值
   * @param dataList 待缓存的List数据
   * @return 缓存的对象
   */
  suspend fun <T> setList(key: String, dataList: List<T>): Long {
    val args = mutableListOf<String>().apply {
      add(key)
      dataList.forEach { add(it.toString()) }
    }
    val response = redis.send(Request.cmd(Command.RPUSH, *args.toTypedArray())).coAwait()
    return response?.toLong() ?: 0
  }

  /**
   * 获得缓存的list对象
   *
   * @param key 缓存的键值
   * @return 缓存键值对应的数据
   */
  suspend fun <T> getList(key: String): List<T>? {
    val response = redis.send(Request.cmd(Command.LRANGE, key, "0", "-1")).coAwait()
    return response?.map { it.toString() as T }?.toList()
  }

  /**
   * 缓存Set
   *
   * @param key 缓存键值
   * @param dataSet 缓存的数据
   * @return 缓存数据的对象
   */
  suspend fun <T> setSet(key: String, dataSet: Set<T>): Long {
    val args = mutableListOf<String>().apply {
      add(key)
      dataSet.forEach { add(it.toString()) }
    }
    val response = redis.send(Request.cmd(Command.SADD, *args.toTypedArray())).coAwait()
    return response?.toLong() ?: 0
  }

  /**
   * 获得缓存的set
   *
   * @param key
   * @return
   */
  suspend fun <T> getSet(key: String): Set<T>? {
    val response = redis.send(Request.cmd(Command.SMEMBERS, key)).coAwait()
    return response?.map { it.toString() as T }?.toSet()
  }

  /**
   * 向Set中添加一个或多个元素
   *
   * @param key 缓存键值
   * @param values 要添加的元素
   * @return 成功添加的元素数量
   */
  suspend fun <T> sAdd(key: String, vararg values: T): Long {
    val request = Request.cmd(Command.SADD)
      .arg(key)

    // 逐个添加参数
    values.forEach {
      request.arg(it.toString())
    }

    val response = redis.send(request).coAwait()
    return response?.toLong() ?: 0
  }

  /**
   * 向Set中添加一个或多个元素，并设置过期时间
   *
   * @param key 缓存键值
   * @param value 要添加的元素
   * @param timeout 过期时间
   * @param timeUnit 时间单位
   * @return 是否成功添加元素
   */
  suspend fun <T> sAdd(key: String, value: T, timeout: Long, timeUnit: TimeUnit): Boolean {
    val added = sAdd(key, value) > 0
    if (added) {
      expire(key, timeout, timeUnit)
    }
    return added
  }

  /**
   * 从Set中移除一个或多个元素
   *
   * @param key 缓存键值
   * @param values 要移除的元素
   * @return 成功移除的元素数量
   */
  suspend fun <T> sRemove(key: String, vararg values: T): Long {
    val args = mutableListOf<String>().apply {
      add(key)
      values.forEach { add(it.toString()) }
    }
    val response = redis.send(Request.cmd(Command.SREM, *args.toTypedArray())).coAwait()
    return response?.toLong() ?: 0
  }

  /**
   * 获取Set中的所有成员
   *
   * @param key 缓存键值
   * @return Set中的所有成员
   */
  suspend fun sMembers(key: String): List<String> {
    val response = redis.send(Request.cmd(Command.SMEMBERS, key)).coAwait()
    return response?.map { it.toString() } ?: emptyList()
  }

  /**
   * 缓存Map
   *
   * @param key
   * @param dataMap
   */
  suspend fun <T> setMap(key: String, dataMap: Map<String, T>): Boolean {
    if (dataMap.isEmpty()) return false
    val args = mutableListOf<String>().apply {
      add(key)
      dataMap.forEach { (k, v) ->
        add(k)
        add(v.toString())
      }
    }
    val response = redis.send(Request.cmd(Command.HMSET, *args.toTypedArray())).coAwait()
    return response?.toString() == "OK"
  }

  /**
   * 获得缓存的Map
   *
   * @param key
   * @return
   */
  suspend fun <T> getMap(key: String): Map<String, T>? {
    val response = redis.send(Request.cmd(Command.HGETALL, key)).coAwait()
    if (response == null || response.size() == 0) return null

    val map = mutableMapOf<String, T>()
//    for (i in 0 until response.size() step 2) {
//      val k = response.get(i).toString()
//      val v = response.get(i + 1).toString() as T
//      map[k] = v
//    }
    for (item in response) {
      val list = item.toMutableList()
      val k = list[0].toString()
      val v = list[1].toString() as T
      map[k] = v
    }
    return map
  }

  /**
   * 往Hash中存入数据
   *
   * @param key Redis键
   * @param hKey Hash键
   * @param value 值
   */
  suspend fun <T> setMapValue(key: String, hKey: String, value: T): Boolean {
    val response = redis.send(Request.cmd(Command.HSET, key, hKey, value.toString())).coAwait()
    return response?.toLong() == 1L
  }

  /**
   * 获取Hash中的数据
   *
   * @param key Redis键
   * @param hKey Hash键
   * @return Hash中的对象
   */
  suspend fun <T> getMapValue(key: String, hKey: String): T? {
    val response = redis.send(Request.cmd(Command.HGET, key, hKey)).coAwait()
    return response?.toString() as? T
  }

  /**
   * 获取多个Hash中的数据
   *
   * @param key Redis键
   * @param hKeys Hash键集合
   * @return Hash对象集合
   */
  suspend fun <T> getMultiMapValue(key: String, hKeys: Collection<String>): List<T?>? {
    val args = mutableListOf<String>().apply {
      add(key)
      addAll(hKeys)
    }
    val response = redis.send(Request.cmd(Command.HMGET, *args.toTypedArray())).coAwait()
    return response?.map { if (it == null) null else it.toString() as T }
  }

  /**
   * 删除Hash中的某条数据
   *
   * @param key Redis键
   * @param hKey Hash键
   * @return 是否成功
   */
  suspend fun deleteMapValue(key: String, hKey: String): Boolean {
    val response = redis.send(Request.cmd(Command.HDEL, key, hKey)).coAwait()
    return response?.toLong() == 1L
  }

  /**
   * 获得缓存的基本对象列表
   *
   * @param pattern 字符串前缀
   * @return 对象列表
   */
  suspend fun keys(pattern: String): List<String>? {
    val response = redis.send(Request.cmd(Command.KEYS, pattern)).coAwait()
    return response?.map { it.toString() }
  }

  /**
   * 自增操作
   *
   * @param key Redis键
   * @return 自增后的值
   */
  suspend fun incr(key: String): Long {
    val response = redis.send(Request.cmd(Command.INCR, key)).coAwait()
    return response?.toLong() ?: 0L
  }

  /**
   * 自增指定步长
   *
   * @param key Redis键
   * @param increment 步长
   * @return 自增后的值
   */
  suspend fun incrBy(key: String, increment: Long): Long {
    val response = redis.send(Request.cmd(Command.INCRBY, key, increment.toString())).coAwait()
    return response?.toLong() ?: 0L
  }

  /**
   * 自减操作
   *
   * @param key Redis键
   * @return 自减后的值
   */
  suspend fun decr(key: String): Long {
    val response = redis.send(Request.cmd(Command.DECR, key)).coAwait()
    return response?.toLong() ?: 0L
  }

  /**
   * 自减指定步长
   *
   * @param key Redis键
   * @param decrement 步长
   * @return 自减后的值
   */
  suspend fun decrBy(key: String, decrement: Long): Long {
    val response = redis.send(Request.cmd(Command.DECRBY, key, decrement.toString())).coAwait()
    return response?.toLong() ?: 0L
  }

  /**
   * 浮点数自增
   *
   * @param key Redis键
   * @param increment 增量
   * @return 自增后的值
   */
  suspend fun incrByFloat(key: String, increment: Double): Double {
    val response = redis.send(Request.cmd(Command.INCRBYFLOAT, key, increment.toString())).coAwait()
    return response?.toDouble() ?: 0.0
  }

  /**
   * 设置键值并返回旧值
   *
   * @param key Redis键
   * @param value 新值
   * @return 旧值
   */
  suspend fun <T> getSet(key: String, value: T): T? {
    val response = redis.send(Request.cmd(Command.GETSET, key, value.toString())).coAwait()
    return response?.toString() as? T
  }

  /**
   * 设置键值（仅当键不存在时）
   *
   * @param key Redis键
   * @param value 值
   * @return 是否设置成功
   */
  suspend fun <T> setIfAbsent(key: String, value: T): Boolean {
    val response = redis.send(Request.cmd(Command.SETNX, key, value.toString())).coAwait()
    return response?.toLong() == 1L
  }

  /**
   * 获取字符串长度
   *
   * @param key Redis键
   * @return 字符串长度
   */
  suspend fun strlen(key: String): Long {
    val response = redis.send(Request.cmd(Command.STRLEN, key)).coAwait()
    return response?.toLong() ?: 0L
  }

  /**
   * 追加字符串
   *
   * @param key Redis键
   * @param value 追加的值
   * @return 追加后的字符串长度
   */
  suspend fun append(key: String, value: String): Long {
    val response = redis.send(Request.cmd(Command.APPEND, key, value)).coAwait()
    return response?.toLong() ?: 0L
  }

  /**
   * 获取子字符串
   *
   * @param key Redis键
   * @param start 开始位置
   * @param end 结束位置
   * @return 子字符串
   */
  suspend fun getRange(key: String, start: Long, end: Long): String? {
    val response = redis.send(Request.cmd(Command.GETRANGE, key, start.toString(), end.toString())).coAwait()
    return response?.toString()
  }

  /**
   * 设置子字符串
   *
   * @param key Redis键
   * @param offset 偏移量
   * @param value 值
   * @return 修改后的字符串长度
   */
  suspend fun setRange(key: String, offset: Long, value: String): Long {
    val response = redis.send(Request.cmd(Command.SETRANGE, key, offset.toString(), value)).coAwait()
    return response?.toLong() ?: 0L
  }

  /**
   * 设置多个键值对
   *
   * @param keyValues 键值对（key1, value1, key2, value2...）
   * @return 是否成功
   */
  suspend fun mset(vararg keyValues: String): Boolean {
    if (keyValues.size % 2 != 0) throw IllegalArgumentException("参数数量必须为偶数")
    val response = redis.send(Request.cmd(Command.MSET, *keyValues)).coAwait()
    return response?.toString() == "OK"
  }

  /**
   * 获取多个键的值
   *
   * @param keys 键集合
   * @return 值列表
   */
  suspend fun mget(vararg keys: String): List<String?> {
    val response = redis.send(Request.cmd(Command.MGET, *keys)).coAwait()
    return response?.map { it?.toString() } ?: emptyList()
  }

  /**
   * 向有序集合添加一个或多个成员，或者更新已存在成员的分数
   *
   * @param key Redis键
   * @param score 分数
   * @param member 成员
   * @return 成功添加的新成员的数量，不包括那些被更新的、已经存在的成员
   */
  suspend fun zadd(key: String, score: Double, member: String): Long {
    val request = Request.cmd(Command.ZADD)
      .arg(key)
      .arg(score.toString())
      .arg(member)

    val response = redis.send(request).coAwait()
    return response?.toLong() ?: 0L
  }

  /**
   * 为有序集合的成员增加分数
   *
   * @param key Redis键
   * @param increment 增量分数
   * @param member 成员
   * @return 增加后的分数
   */
  suspend fun zincrby(key: String, increment: Double, member: String): Double {
    val request = Request.cmd(Command.ZINCRBY)
      .arg(key)
      .arg(increment.toString())
      .arg(member)

    val response = redis.send(request).coAwait()
    return response?.toDouble() ?: 0.0
  }

  /**
   * 获取有序集合中指定成员的分数
   *
   * @param key Redis键
   * @param member 成员
   * @return 分数，如果成员不存在或键不存在则返回null
   */
  suspend fun zscore(key: String, member: String): Double? {
    val request = Request.cmd(Command.ZSCORE)
      .arg(key)
      .arg(member)

    val response = redis.send(request).coAwait()
    return response?.toDouble()
  }

  /**
   * 获取有序集合中指定区间的成员，按分数从高到低排序
   *
   * @param key Redis键
   * @param start 开始位置
   * @param stop 结束位置
   * @param withScores 是否返回分数
   * @return 指定区间的成员列表，如果withScores为true，则返回成员和分数的交替列表
   */
  suspend fun zrevrange(key: String, start: Long, stop: Long, withScores: Boolean = false): List<String> {
    // 使用可变参数列表而不是数组，避免类型转换问题
    val request = if (withScores) {
      Request.cmd(Command.ZREVRANGE)
        .arg(key)
        .arg(start.toString())
        .arg(stop.toString())
        .arg("WITHSCORES")
    } else {
      Request.cmd(Command.ZREVRANGE)
        .arg(key)
        .arg(start.toString())
        .arg(stop.toString())
    }

    val response = redis.send(request).coAwait()
    return response?.map { it.toString() } ?: emptyList()
  }

  /**
   * 获取有序集合中所有成员的分数总和
   *
   * @param key Redis键
   * @return 所有成员的分数总和
   */
  suspend fun zsumScores(key: String): Double {
    // 首先检查键是否存在
    if (!hasKey(key)) return 0.0

    val request = Request.cmd(Command.ZRANGE)
      .arg(key)
      .arg("0")
      .arg("-1")
      .arg("WITHSCORES")

    val response = redis.send(request).coAwait()
    if (response == null || response.size() == 0) return 0.0

    var sum = 0.0
    for (item in response) {
      sum += item.toMutableList()[1]?.toString()?.toDoubleOrNull() ?: 0.0
    }
    return sum
  }

  /**
   * 使用 SCAN 命令获取匹配指定模式的所有键
   *
   * @param pattern 匹配模式（例如：content:clicks:*）
   * @return 匹配模式的键列表
   */
  suspend fun scan(pattern: String): List<String> {
    val keys = mutableListOf<String>()
    var cursor = "0"

    do {
      val request = Request.cmd(Command.SCAN)
        .arg(cursor)
        .arg("MATCH")
        .arg(pattern)
        .arg("COUNT")
        .arg("100") // 每次迭代返回的键数量

      val response = redis.send(request).coAwait()
      if (response != null && response.size() >= 2) {
        cursor = response.get(0).toString()

        // 从索引1处获取键列表
        val scanKeys = response.get(1)
        for (i in 0 until scanKeys.size()) {
          keys.add(scanKeys.get(i).toString())
        }
      } else {
        break
      }
    } while (cursor != "0")

    return keys
  }

  /**
   * 计算多个有序集合的并集，并将结果存储在新的键中
   *
   * @param destKey 目标键，存储计算结果
   * @param keys 要计算并集的有序集合键列表
   * @param weights 各有序集的权重（可选）
   * @param aggregate 结果集的聚合方式（可选，默认为 SUM）
   * @return 目标键中的元素数量
   */
  suspend fun zunionstore(
    destKey: String,
    keys: Array<String>,
    weights: DoubleArray? = null,
    aggregate: String = "SUM"
  ): Long {
    if (keys.isEmpty()) return 0

    val request = Request.cmd(Command.ZUNIONSTORE)
      .arg(destKey)
      .arg(keys.size.toString())

    // 添加源集合键
    keys.forEach { request.arg(it) }

    // 添加权重（如果指定）
    if (weights != null && weights.size == keys.size) {
      request.arg("WEIGHTS")
      weights.forEach { request.arg(it.toString()) }
    }

    // 添加聚合方式
    if (aggregate in listOf("SUM", "MIN", "MAX")) {
      request.arg("AGGREGATE")
      request.arg(aggregate)
    }

    val response = redis.send(request).coAwait()
    return response?.toLong() ?: 0L
  }

  /**
   * 计算多个有序集合的并集，使用相同的权重
   *
   * @param destKey 目标键，存储计算结果
   * @param keys 要计算并集的有序集合键列表
   * @return 目标键中的元素数量
   */
  suspend fun zunionstoreWithEqualWeights(destKey: String, keys: Array<String>): Long {
    val weights = DoubleArray(keys.size) { 1.0 }
    return zunionstore(destKey, keys, weights)
  }

  /**
   * 计算多个有序集合的并集，对结果取最大值
   *
   * @param destKey 目标键，存储计算结果
   * @param keys 要计算并集的有序集合键列表
   * @return 目标键中的元素数量
   */
  suspend fun zunionstoreMax(destKey: String, keys: Array<String>): Long {
    return zunionstore(destKey, keys, null, "MAX")
  }

  /**
   * 计算两个或多个有序集合的差集，并将结果存储在新的键中
   * 此方法仅适用于Redis 6.2+版本
   *
   * @param destKey 目标键，存储计算结果
   * @param keys 要计算差集的有序集合键数组，第一个集合是基准
   * @return 目标键中的元素数量
   */
  suspend fun zdiffstore(destKey: String, keys: Array<String>): Long {
    if (keys.isEmpty() || keys.size < 2) return 0L

    val request = Request.cmd(Command.ZDIFFSTORE)
      .arg(destKey)
      .arg(keys.size.toString())

    // 添加源集合键
    keys.forEach { request.arg(it) }

    val response = redis.send(request).coAwait()
    return response?.toLong() ?: 0L
  }

  /**
   * 获取有序集合的大小（成员数量）
   *
   * @param key Redis键
   * @return 有序集合的大小
   */
  suspend fun zcard(key: String): Long {
    val request = Request.cmd(Command.ZCARD)
      .arg(key)

    val response = redis.send(request).coAwait()
    return response?.toLong() ?: 0L
  }

  /**
   * 获取有序集合中所有成员
   *
   * @param key Redis键
   * @return 有序集合的所有成员
   */
  suspend fun zall(key: String): List<String> {
    return zrevrange(key, 0, -1)
  }

  /**
   * 计算两个有序集合的差集，并返回结果
   * 此方法仅适用于Redis 6.2+版本
   *
   * @param keys 要计算差集的有序集合键数组，第一个集合是基准
   * @return 差集结果
   */
  suspend fun zdiff(vararg keys: String): List<String> {
    if (keys.isEmpty() || keys.size < 2) return emptyList()

    val request = Request.cmd(Command.ZDIFF)

    // 添加集合数量
    request.arg(keys.size.toString())

    // 添加源集合键
    keys.forEach { request.arg(it) }

    val response = redis.send(request).coAwait()
    return response?.map { it.toString() } ?: emptyList()
  }

  /**
   * 执行 Lua 脚本
   *
   * @param script Lua 脚本
   * @param keys 脚本中使用的 KEYS 参数
   * @param args 脚本中使用的 ARGV 参数
   * @return 脚本执行结果
   */
  suspend fun eval(script: String, keys: List<String> = emptyList(), vararg args: String): List<String>? {
    val request = Request.cmd(Command.EVAL)
      .arg(script)
      .arg(keys.size.toString())

    // 添加 KEYS 参数
    keys.forEach { request.arg(it) }

    // 添加 ARGV 参数
    args.forEach { request.arg(it) }

    val response = redis.send(request).coAwait()
    return response?.map { it.toString() }
  }

  /**
   * 执行 Lua 脚本并返回整数结果
   *
   * @param script Lua 脚本
   * @param keys 脚本中使用的 KEYS 参数
   * @param args 脚本中使用的 ARGV 参数
   * @return 脚本执行结果（整数）
   */
  suspend fun evalToInt(script: String, keys: List<String> = emptyList(), vararg args: String): Int? {
    val result = eval(script, keys, *args)
    return result?.firstOrNull()?.toIntOrNull()
  }

  /**
   * 执行 Lua 脚本并返回长整数结果
   *
   * @param script Lua 脚本
   * @param keys 脚本中使用的 KEYS 参数
   * @param args 脚本中使用的 ARGV 参数
   * @return 脚本执行结果（长整数）
   */
  suspend fun evalToLong(script: String, keys: List<String> = emptyList(), vararg args: String): Long? {
    val result = eval(script, keys, *args)
    return result?.firstOrNull()?.toLongOrNull()
  }

  /**
   * 执行 Lua 脚本并返回布尔结果
   *
   * @param script Lua 脚本
   * @param keys 脚本中使用的 KEYS 参数
   * @param args 脚本中使用的 ARGV 参数
   * @return 脚本执行结果（布尔值）
   */
  suspend fun evalToBoolean(script: String, keys: List<String> = emptyList(), vararg args: String): Boolean {
    val result = eval(script, keys, *args)
    return result?.firstOrNull()?.toIntOrNull() == 1
  }
}

